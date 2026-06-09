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
package dev.gitnode.os.actions.services;

import dev.gitnode.os.actions.entities.RunnerStatus;
import dev.gitnode.os.actions.entities.WorkflowJob;
import dev.gitnode.os.actions.entities.WorkflowJobStatus;
import dev.gitnode.os.actions.entities.WorkflowLog;
import dev.gitnode.os.actions.entities.WorkflowRun;
import dev.gitnode.os.actions.entities.WorkflowRunStatus;
import dev.gitnode.os.actions.entities.WorkflowStep;
import dev.gitnode.os.actions.repositories.RunnerRepository;
import dev.gitnode.os.actions.repositories.WorkflowJobRepository;
import dev.gitnode.os.actions.repositories.WorkflowLogRepository;
import dev.gitnode.os.actions.repositories.WorkflowRunRepository;
import dev.gitnode.os.actions.repositories.WorkflowStepRepository;
import dev.gitnode.os.actions.websocket.RunStatusSseRegistry;
import dev.gitnode.os.actions.websocket.SseEmitterRegistry;
import dev.gitnode.os.events.actions.WorkflowJobCompletedEvent;
import dev.gitnode.os.events.actions.WorkflowJobQueuedEvent;
import dev.gitnode.os.events.actions.WorkflowJobStartedEvent;
import dev.gitnode.os.events.actions.WorkflowRunCompletedEvent;
import dev.gitnode.os.shared.audit.annotations.Audited;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
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
  private final RunStatusSseRegistry runStatusSseRegistry;

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
    this.runStatusSseRegistry.notify(job.getRunId());

    log.info("Job claimed: jobId={}, runnerId={}", jobId, runnerId);
  }

  @Transactional
  public void handleStepStarted(final UUID stepId) {

    final var step = this.stepRepository.findById(stepId).orElse(null);
    if (step == null) {
      log.debug("STEP_STARTED for unknown stepId={}, ignoring", stepId);
      return;
    }
    step.setStatus("running");
    step.setStartedAt(Instant.now());
    this.stepRepository.save(step);
  }

  @Transactional
  public void handleStepCompleted(final UUID stepId, final String conclusion) {

    final var step = this.stepRepository.findById(stepId).orElse(null);

    if (step == null) {
      log.warn("STEP_COMPLETED for unknown stepId={}, ignoring", stepId);
      this.sseEmitterRegistry.complete(stepId);
      return;
    }

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

    this.runStatusSseRegistry.notify(job.getRunId());
    final var allJobs = this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(job.getRunId());
    this.advanceWaitingJobs(job.getRunId(), run.getRepoId(), allJobs);
    this.maybeCompleteRun(run, allJobs);

    log.info("Job completed: jobId={}, conclusion={}", jobId, conclusion);
  }

  @Audited(
      action = "ACTIONS_RUN_CANCEL",
      entityType = "WORKFLOW_RUN",
      entityIdSpEL = "#runId.toString()",
      detailsSpEL = "'runId=' + #runId")
  @Transactional
  public void cancelRun(final UUID runId) {

    final var run = this.requireRun(runId);

    if (run.getStatus() == WorkflowRunStatus.SUCCESS
        || run.getStatus() == WorkflowRunStatus.FAILURE
        || run.getStatus() == WorkflowRunStatus.CANCELLED) {
      return;
    }

    final var jobs = this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(runId);
    for (final var job : jobs) {
      if (!TERMINAL_STATUSES.contains(job.getStatus())) {
        job.setStatus(WorkflowJobStatus.CANCELLED);
        job.setConclusion("cancelled");
        job.setCompletedAt(Instant.now());
        this.jobRepository.save(job);
      }
    }

    run.setStatus(WorkflowRunStatus.CANCELLED);
    run.setConclusion("cancelled");
    run.setCompletedAt(Instant.now());
    this.runRepository.save(run);

    this.eventPublisher.publishEvent(
        new WorkflowRunCompletedEvent(
            run.getId(), run.getRepoId(), run.getWorkflowName(), "cancelled"));
    this.runStatusSseRegistry.complete(run.getId());

    log.info("Run cancelled: runId={}", runId);
  }

  @Audited(
      action = "ACTIONS_RUN_DELETE",
      entityType = "WORKFLOW_RUN",
      entityIdSpEL = "#runId.toString()",
      detailsSpEL = "'runId=' + #runId")
  @Transactional
  public void deleteRun(final UUID runId) {

    final var run =
        this.runRepository
            .findById(runId)
            .orElseThrow(() -> new ItemNotFoundException("Workflow run not found: " + runId));

    if (run.getStatus() == WorkflowRunStatus.QUEUED
        || run.getStatus() == WorkflowRunStatus.IN_PROGRESS) {
      this.cancelRun(runId);
    }

    this.runRepository.delete(run);
    this.runStatusSseRegistry.complete(runId);

    log.info("Run deleted: runId={}", runId);
  }

  @Transactional
  public void handleRunnerDisconnected(final UUID runnerId) {

    final var jobs =
        this.jobRepository.findAllByRunnerIdAndStatus(runnerId, WorkflowJobStatus.IN_PROGRESS);

    for (final var job : jobs) {
      job.setStatus(WorkflowJobStatus.FAILURE);
      job.setConclusion("failure");
      job.setCompletedAt(Instant.now());
      this.jobRepository.save(job);

      this.stepRepository.findAllByJobIdOrderByStepNumberAsc(job.getId()).stream()
          .filter(s -> "running".equals(s.getStatus()))
          .forEach(s -> this.sseEmitterRegistry.complete(s.getId()));

      this.runRepository
          .findById(job.getRunId())
          .ifPresent(
              run -> {
                this.eventPublisher.publishEvent(
                    new WorkflowJobCompletedEvent(
                        job.getId(), job.getRunId(), run.getRepoId(), job.getName(), "failure"));
                final var runJobs =
                    this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(job.getRunId());
                this.advanceWaitingJobs(job.getRunId(), run.getRepoId(), runJobs);
                this.maybeCompleteRun(run, runJobs);
              });

      log.warn("Job failed due to runner disconnect: jobId={}, runnerId={}", job.getId(), runnerId);
    }
  }

  @Transactional
  public void ingestLog(
      final UUID stepId, final int lineNumber, final String content, final String level) {

    if (!this.stepRepository.existsById(stepId)) {
      log.warn("LOG for unknown stepId={}, dropping DB write but broadcasting SSE", stepId);
      this.sseEmitterRegistry.broadcast(stepId, new LogLine(lineNumber, content, level));
      return;
    }

    final var logRecord = new WorkflowLog();
    logRecord.setStepId(stepId);
    logRecord.setLineNumber(lineNumber);
    logRecord.setContent(content);
    logRecord.setLevel(level != null && !level.isBlank() ? level : "info");
    this.logRepository.save(logRecord);

    this.sseEmitterRegistry.broadcast(stepId, new LogLine(lineNumber, content, level));
  }

  private void advanceWaitingJobs(
      final UUID runId, final UUID repoId, final java.util.List<WorkflowJob> allJobs) {

    final var successKeys =
        allJobs.stream()
            .filter(j -> j.getStatus() == WorkflowJobStatus.SUCCESS)
            .flatMap(j -> resolveJobIdentifiers(j).stream())
            .collect(Collectors.toSet());

    final var failedKeys =
        allJobs.stream()
            .filter(
                j ->
                    j.getStatus() == WorkflowJobStatus.FAILURE
                        || j.getStatus() == WorkflowJobStatus.CANCELLED)
            .flatMap(j -> resolveJobIdentifiers(j).stream())
            .collect(Collectors.toSet());

    for (final var waiting : allJobs) {
      this.advanceJobIfReady(waiting, successKeys, failedKeys, runId, repoId);
    }
  }

  /** Returns both the job key and name so needs matching works regardless of which is stored. */
  private static java.util.List<String> resolveJobIdentifiers(final WorkflowJob job) {
    final var ids = new java.util.ArrayList<String>();
    if (job.getJobKey() != null) {
      ids.add(job.getJobKey());
    }
    ids.add(job.getName());
    return ids;
  }

  private void advanceJobIfReady(
      final WorkflowJob waiting,
      final Set<String> successKeys,
      final Set<String> failedKeys,
      final UUID runId,
      final UUID repoId) {

    if (waiting.getStatus() != WorkflowJobStatus.WAITING) {
      return;
    }
    final var needs = waiting.getNeeds();
    if (needs == null || needs.isEmpty()) {
      return;
    }

    final boolean anyNeedFailed = needs.stream().anyMatch(failedKeys::contains);
    if (anyNeedFailed) {
      waiting.setStatus(WorkflowJobStatus.SKIPPED);
      waiting.setConclusion("skipped");
      waiting.setCompletedAt(Instant.now());
      this.jobRepository.save(waiting);
      log.info("Job skipped due to failed dependency: jobId={}", waiting.getId());
      return;
    }

    if (successKeys.containsAll(needs)) {
      waiting.setStatus(WorkflowJobStatus.QUEUED);
      this.jobRepository.save(waiting);
      this.eventPublisher.publishEvent(
          new WorkflowJobQueuedEvent(
              waiting.getId(), runId, repoId, waiting.getName(), waiting.getRunnerLabels()));
      log.info("Job advanced to QUEUED after needs resolved: jobId={}", waiting.getId());
    }
  }

  private void maybeCompleteRun(final WorkflowRun run, final java.util.List<WorkflowJob> jobs) {

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
    this.runStatusSseRegistry.complete(run.getId());

    log.info("Run completed: runId={}, conclusion={}", run.getId(), conclusion);
  }

  private void markRunnerOnlineIfIdle(final UUID runnerId) {

    this.runnerRepository
        .findById(runnerId)
        .ifPresent(
            runner -> {
              final long inProgress =
                  this.jobRepository.countByRunnerIdAndStatus(
                      runnerId, WorkflowJobStatus.IN_PROGRESS);
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
