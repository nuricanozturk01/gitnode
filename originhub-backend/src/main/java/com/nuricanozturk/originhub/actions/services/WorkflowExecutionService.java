/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nuricanozturk.originhub.actions.services;

import com.nuricanozturk.originhub.actions.entities.RunnerStatus;
import com.nuricanozturk.originhub.actions.entities.WorkflowJob;
import com.nuricanozturk.originhub.actions.entities.WorkflowJobStatus;
import com.nuricanozturk.originhub.actions.entities.WorkflowLog;
import com.nuricanozturk.originhub.actions.entities.WorkflowRun;
import com.nuricanozturk.originhub.actions.entities.WorkflowRunStatus;
import com.nuricanozturk.originhub.actions.entities.WorkflowStep;
import com.nuricanozturk.originhub.actions.repositories.RunnerRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowJobRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowLogRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowRunRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowStepRepository;
import com.nuricanozturk.originhub.actions.websocket.SseEmitterRegistry;
import com.nuricanozturk.originhub.events.actions.WorkflowJobCompletedEvent;
import com.nuricanozturk.originhub.events.actions.WorkflowJobQueuedEvent;
import com.nuricanozturk.originhub.events.actions.WorkflowJobStartedEvent;
import com.nuricanozturk.originhub.events.actions.WorkflowRunCompletedEvent;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class WorkflowExecutionService {

  private static final Set<WorkflowJobStatus> TERMINAL_STATUSES =
      Set.of(
          WorkflowJobStatus.SUCCESS,
          WorkflowJobStatus.FAILURE,
          WorkflowJobStatus.CANCELLED,
          WorkflowJobStatus.SKIPPED);

  private final WorkflowJobRepository jobRepository;
  private final WorkflowRunRepository runRepository;
  private final WorkflowStepRepository stepRepository;
  private final WorkflowLogRepository logRepository;
  private final RunnerRepository runnerRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final SseEmitterRegistry sseEmitterRegistry;

  @Transactional
  public void handleJobClaimed(final UUID jobId, final UUID runnerId) {

    final var job = this.requireJob(jobId);
    job.setStatus(WorkflowJobStatus.IN_PROGRESS);
    job.setRunnerId(runnerId);
    job.setStartedAt(Instant.now());
    this.jobRepository.save(job);

    final var run = this.requireRun(job.getRunId());
    if (run.getStatus() == WorkflowRunStatus.QUEUED) {
      run.setStatus(WorkflowRunStatus.IN_PROGRESS);
      run.setStartedAt(Instant.now());
      this.runRepository.save(run);
    }

    this.eventPublisher.publishEvent(
        new WorkflowJobStartedEvent(jobId, job.getRunId(), run.getRepoId(), job.getName()));

    log.info("Job claimed: jobId={}, runnerId={}", jobId, runnerId);
  }

  @Transactional
  public void handleStepStarted(final UUID stepId) {

    final var step = this.requireStep(stepId);
    step.setStatus("running");
    step.setStartedAt(Instant.now());
    this.stepRepository.save(step);
  }

  @Transactional
  public void handleStepCompleted(final UUID stepId, final String conclusion) {

    final var step = this.requireStep(stepId);
    step.setStatus("completed");
    step.setConclusion(conclusion);
    step.setCompletedAt(Instant.now());
    this.stepRepository.save(step);

    this.sseEmitterRegistry.complete(stepId);
  }

  @Transactional
  public void handleJobCompleted(final UUID jobId, final String conclusion) {

    final var job = this.requireJob(jobId);
    final var status = conclusionToStatus(conclusion);
    job.setStatus(status);
    job.setConclusion(conclusion);
    job.setCompletedAt(Instant.now());
    this.jobRepository.save(job);

    if (job.getRunnerId() != null) {
      this.markRunnerOnlineIfIdle(job.getRunnerId());
    }

    final var run = this.requireRun(job.getRunId());

    this.eventPublisher.publishEvent(
        new WorkflowJobCompletedEvent(
            jobId, job.getRunId(), run.getRepoId(), job.getName(), conclusion));

    this.advanceWaitingJobs(job.getRunId(), run.getRepoId());
    this.maybeCompleteRun(run);

    log.info("Job completed: jobId={}, conclusion={}", jobId, conclusion);
  }

  @Transactional
  public void ingestLog(
      final UUID stepId, final int lineNumber, final String content, final String level) {

    final var log_ = new WorkflowLog();
    log_.setStepId(stepId);
    log_.setLineNumber(lineNumber);
    log_.setContent(content);
    log_.setLevel(level != null && !level.isBlank() ? level : "info");
    this.logRepository.save(log_);

    this.sseEmitterRegistry.broadcast(stepId, new LogLine(lineNumber, content, level));
  }

  private void advanceWaitingJobs(final UUID runId, final UUID repoId) {

    final var allJobs = this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(runId);
    final var completedNames =
        allJobs.stream()
            .filter(j -> TERMINAL_STATUSES.contains(j.getStatus()))
            .map(WorkflowJob::getName)
            .collect(Collectors.toSet());

    for (final var waiting : allJobs) {
      if (waiting.getStatus() != WorkflowJobStatus.WAITING) {
        continue;
      }
      final var needs = waiting.getNeeds();
      if (needs == null || needs.isEmpty()) {
        continue;
      }
      if (completedNames.containsAll(needs)) {
        waiting.setStatus(WorkflowJobStatus.QUEUED);
        this.jobRepository.save(waiting);
        this.eventPublisher.publishEvent(
            new WorkflowJobQueuedEvent(
                waiting.getId(), runId, repoId, waiting.getName(), waiting.getRunnerLabels()));
        log.info("Job advanced to QUEUED after needs resolved: jobId={}", waiting.getId());
      }
    }
  }

  private void maybeCompleteRun(final WorkflowRun run) {

    final var jobs = this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(run.getId());
    final boolean allDone = jobs.stream().allMatch(j -> TERMINAL_STATUSES.contains(j.getStatus()));

    if (!allDone) {
      return;
    }

    final boolean anyFailed =
        jobs.stream().anyMatch(j -> j.getStatus() == WorkflowJobStatus.FAILURE);
    final var conclusion = anyFailed ? "failure" : "success";

    run.setStatus(anyFailed ? WorkflowRunStatus.FAILURE : WorkflowRunStatus.SUCCESS);
    run.setConclusion(conclusion);
    run.setCompletedAt(Instant.now());
    this.runRepository.save(run);

    this.eventPublisher.publishEvent(
        new WorkflowRunCompletedEvent(
            run.getId(), run.getRepoId(), run.getWorkflowName(), conclusion));

    log.info("Run completed: runId={}, conclusion={}", run.getId(), conclusion);
  }

  private void markRunnerOnlineIfIdle(final UUID runnerId) {

    this.runnerRepository
        .findById(runnerId)
        .ifPresent(
            runner -> {
              final long inProgress =
                  this.jobRepository
                      .findAllByRunnerIdAndStatus(runnerId, WorkflowJobStatus.IN_PROGRESS)
                      .size();
              if (inProgress == 0) {
                runner.setStatus(RunnerStatus.ONLINE);
                this.runnerRepository.save(runner);
              }
            });
  }

  private WorkflowJob requireJob(final UUID jobId) {
    return this.jobRepository
        .findById(jobId)
        .orElseThrow(() -> new ItemNotFoundException("Workflow job not found: " + jobId));
  }

  private WorkflowRun requireRun(final UUID runId) {
    return this.runRepository
        .findById(runId)
        .orElseThrow(() -> new ItemNotFoundException("Workflow run not found: " + runId));
  }

  private WorkflowStep requireStep(final UUID stepId) {
    return this.stepRepository
        .findById(stepId)
        .orElseThrow(() -> new ItemNotFoundException("Workflow step not found: " + stepId));
  }

  private static WorkflowJobStatus conclusionToStatus(final String conclusion) {
    return switch (conclusion.toLowerCase(java.util.Locale.ROOT)) {
      case "success" -> WorkflowJobStatus.SUCCESS;
      case "cancelled" -> WorkflowJobStatus.CANCELLED;
      case "skipped" -> WorkflowJobStatus.SKIPPED;
      default -> WorkflowJobStatus.FAILURE;
    };
  }

  public record LogLine(int lineNumber, String content, String level) {}
}
