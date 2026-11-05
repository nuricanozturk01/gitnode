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
import dev.gitnode.os.actions.model.WorkflowModel;
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
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class WorkflowExecutionService {

  /** Length of the log level column. */
  private static final int MAX_LEVEL_LENGTH = 8;

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
  private final JobAdvancer jobAdvancer;
  private final WorkflowTriggerService triggerService;
  private final RunnerCommands runnerCommands;

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
    this.handleJobCompleted(jobId, conclusion, java.util.Map.of());
  }

  @Transactional
  public void handleJobCompleted(
      final UUID jobId, final String conclusion, final java.util.Map<String, String> outputs) {

    final var job = this.requireJob(jobId);
    if (job.getStatus() == WorkflowJobStatus.CANCELLED) {
      // Cancelled from the UI; the runner only confirms it stopped. Keep the cancelled result and
      // free the runner.
      if (job.getRunnerId() != null) {
        this.markRunnerOnlineIfIdle(job.getRunnerId());
      }
      log.info("Job {} finished on the runner after it was cancelled", jobId);
      return;
    }
    final var run0 = this.requireRun(job.getRunId());
    final var effective = this.applyContinueOnError(run0, job, conclusion);
    final var status = conclusionToStatus(effective);

    job.setStatus(status);
    job.setConclusion(effective);
    job.setOutputs(outputs.isEmpty() ? null : new java.util.LinkedHashMap<>(outputs));
    job.setCompletedAt(Instant.now());
    this.jobRepository.save(job);

    if (job.getRunnerId() != null) {
      this.markRunnerOnlineIfIdle(job.getRunnerId());
    }

    final var run = run0;

    this.eventPublisher.publishEvent(
        new WorkflowJobCompletedEvent(
            jobId, job.getRunId(), run.getRepoId(), job.getName(), effective));

    this.runStatusSseRegistry.notify(job.getRunId());
    final var allJobs = this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(job.getRunId());
    final var current = this.advanceWaitingJobs(run, allJobs);
    this.maybeCompleteRun(run, current);

    log.info("Job completed: jobId={}, conclusion={}", jobId, effective);
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
    jobs.stream()
        .filter(job -> !TERMINAL_STATUSES.contains(job.getStatus()))
        .forEach(this::cancelJob);

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

  /**
   * A job with {@code continue-on-error: true} that fails counts as a success for the run and for
   * the jobs that need it, as on GitHub. Anything else keeps the reported conclusion.
   */
  private String applyContinueOnError(
      final WorkflowRun run, final WorkflowJob job, final String conclusion) {

    if (!"failure".equalsIgnoreCase(conclusion)) {
      return conclusion;
    }
    final var model = this.jobAdvancer.loadModel(run);
    final var def = this.jobAdvancer.jobDefinition(model, job);
    if (def == null || def.continueOnError() == null) {
      return conclusion;
    }
    return "true".equalsIgnoreCase(def.continueOnError().strip()) ? "success" : conclusion;
  }

  /**
   * Runs a finished run again as a new attempt, like GitHub's "Re-run jobs". With {@code jobId}
   * only that job and the jobs that need it are reset; with {@code failedOnly} only the jobs that
   * did not succeed; otherwise every job. Jobs that keep their result are left as they are.
   */
  @Audited(
      action = "ACTIONS_RUN_RERUN",
      entityType = "WORKFLOW_RUN",
      entityIdSpEL = "#runId.toString()",
      detailsSpEL = "'runId=' + #runId")
  @Transactional
  public void rerun(final UUID runId, final boolean failedOnly, final @Nullable UUID jobId) {

    final var run = this.requireRun(runId);
    assertFinished(run);

    final var allJobs = this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(runId);
    final var reset = this.selectJobsToRerun(allJobs, failedOnly, jobId);
    if (reset.isEmpty()) {
      throw new BadRequestException("There is no job to re-run");
    }

    this.stepRepository.deleteAllByJobIds(reset.stream().map(WorkflowJob::getId).toList());
    reset.forEach(WorkflowExecutionService::resetJob);

    run.setRunAttempt(run.getRunAttempt() + 1);
    run.setStatus(WorkflowRunStatus.QUEUED);
    run.setConclusion(null);
    run.setCompletedAt(null);
    this.runRepository.save(run);

    final var changed = this.jobAdvancer.advance(run, allJobs, this.jobAdvancer.loadModel(run));
    this.jobRepository.saveAll(reset);
    this.jobRepository.saveAll(changed);
    this.announceQueued(run, reset, changed);
    this.runStatusSseRegistry.notify(run.getId());

    log.info("Run re-run: runId={}, attempt={}, jobs={}", runId, run.getRunAttempt(), reset.size());
  }

  private static void assertFinished(final WorkflowRun run) {
    if (run.getStatus() == WorkflowRunStatus.QUEUED
        || run.getStatus() == WorkflowRunStatus.IN_PROGRESS) {
      throw new BadRequestException("The run is still in progress");
    }
  }

  /** Back to the state of a freshly created job: waiting on its needs, or ready to be queued. */
  private static void resetJob(final WorkflowJob job) {
    job.setRunnerId(null);
    job.setConclusion(null);
    job.setStartedAt(null);
    job.setCompletedAt(null);
    final boolean waits = job.getNeeds() != null && !job.getNeeds().isEmpty();
    job.setStatus(waits ? WorkflowJobStatus.WAITING : WorkflowJobStatus.QUEUED);
  }

  private void announceQueued(
      final WorkflowRun run, final List<WorkflowJob> reset, final List<WorkflowJob> changed) {

    final var toAnnounce = new LinkedHashSet<WorkflowJob>();
    reset.stream().filter(j -> j.getStatus() == WorkflowJobStatus.QUEUED).forEach(toAnnounce::add);
    changed.stream()
        .filter(j -> j.getStatus() == WorkflowJobStatus.QUEUED)
        .forEach(toAnnounce::add);
    for (final var job : toAnnounce) {
      this.eventPublisher.publishEvent(
          new WorkflowJobQueuedEvent(
              job.getId(), run.getId(), run.getRepoId(), job.getName(), job.getRunnerLabels()));
    }
  }

  private List<WorkflowJob> selectJobsToRerun(
      final List<WorkflowJob> allJobs, final boolean failedOnly, final @Nullable UUID jobId) {

    final var selected = new HashSet<>(initialSelection(allJobs, failedOnly, jobId));
    addDependents(allJobs, selected);
    return allJobs.stream().filter(selected::contains).toList();
  }

  private static List<WorkflowJob> initialSelection(
      final List<WorkflowJob> allJobs, final boolean failedOnly, final @Nullable UUID jobId) {

    if (jobId != null) {
      final var single = allJobs.stream().filter(j -> j.getId().equals(jobId)).toList();
      if (single.isEmpty()) {
        throw new ItemNotFoundException("Workflow job not found: " + jobId);
      }
      return single;
    }
    return failedOnly
        ? allJobs.stream().filter(WorkflowExecutionService::didNotSucceed).toList()
        : allJobs;
  }

  private static boolean didNotSucceed(final WorkflowJob job) {
    return job.getStatus() == WorkflowJobStatus.FAILURE
        || job.getStatus() == WorkflowJobStatus.CANCELLED
        || job.getStatus() == WorkflowJobStatus.SKIPPED;
  }

  /** Grows the selection with every job that (transitively) needs a selected job. */
  private static void addDependents(
      final List<WorkflowJob> allJobs, final Set<WorkflowJob> selected) {
    boolean grew = true;
    while (grew) {
      final var dependents =
          allJobs.stream()
              .filter(job -> !selected.contains(job) && dependsOnAny(job, selected, allJobs))
              .toList();
      selected.addAll(dependents);
      grew = !dependents.isEmpty();
    }
  }

  private static boolean dependsOnAny(
      final WorkflowJob job, final Set<WorkflowJob> selected, final List<WorkflowJob> allJobs) {
    return job.getNeeds() != null
        && job.getNeeds().stream()
            .anyMatch(
                need -> JobNeeds.jobsFor(allJobs, need).stream().anyMatch(selected::contains));
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

      this.closeOpenSteps(job.getId(), "failure");

      this.runRepository
          .findById(job.getRunId())
          .ifPresent(
              run -> {
                this.eventPublisher.publishEvent(
                    new WorkflowJobCompletedEvent(
                        job.getId(), job.getRunId(), run.getRepoId(), job.getName(), "failure"));
                final var runJobs =
                    this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(job.getRunId());
                this.advanceWaitingJobs(run, runJobs);
                this.maybeCompleteRun(run, runJobs);
              });

      log.warn("Job failed due to runner disconnect: jobId={}, runnerId={}", job.getId(), runnerId);
    }
  }

  @Transactional
  public void ingestLog(
      final UUID stepId, final int lineNumber, final String content, final String level) {

    final var now = Instant.now();
    final var storedLevel = normalizeLevel(level);
    if (!this.stepRepository.existsById(stepId)) {
      log.warn("LOG for unknown stepId={}, dropping DB write but broadcasting SSE", stepId);
      this.sseEmitterRegistry.broadcast(stepId, new LogLine(lineNumber, content, storedLevel, now));
      return;
    }

    final var logRecord = new WorkflowLog();
    logRecord.setStepId(stepId);
    logRecord.setLineNumber(lineNumber);
    logRecord.setContent(content);
    logRecord.setLevel(storedLevel);
    this.logRepository.save(logRecord);

    this.sseEmitterRegistry.broadcast(stepId, new LogLine(lineNumber, content, storedLevel, now));
  }

  /** The log level as stored: "info" when missing, cut to the column's length. */
  private static String normalizeLevel(final @Nullable String level) {
    if (level == null || level.isBlank()) {
      return "info";
    }
    return level.length() > MAX_LEVEL_LENGTH ? level.substring(0, MAX_LEVEL_LENGTH) : level;
  }

  /**
   * Starts (or skips) the WAITING jobs whose needs have finished. See {@link JobAdvancer} for the
   * rules; the changed jobs are persisted here and the started ones announced.
   */
  private List<WorkflowJob> advanceWaitingJobs(
      final WorkflowRun run, final java.util.List<WorkflowJob> allJobs) {

    final var model = this.jobAdvancer.loadModel(run);
    final var jobs = this.expandReadyMatrices(run, allJobs, model);
    final var changed = this.jobAdvancer.advance(run, jobs, model);
    for (final var job : changed) {
      this.jobRepository.save(job);
      if (job.getStatus() == WorkflowJobStatus.QUEUED) {
        this.eventPublisher.publishEvent(
            new WorkflowJobQueuedEvent(
                job.getId(), run.getId(), run.getRepoId(), job.getName(), job.getRunnerLabels()));
        log.info("Job advanced to QUEUED after needs resolved: jobId={}", job.getId());
      } else {
        log.info("Job ended without running (status={}): jobId={}", job.getStatus(), job.getId());
      }
    }
    return jobs;
  }

  /**
   * Turns dynamic-matrix placeholders whose needs have finished into the jobs of their matrix. A
   * placeholder that must not run (a need failed) is left for the advancer to skip.
   */
  private List<WorkflowJob> expandReadyMatrices(
      final WorkflowRun run, final List<WorkflowJob> allJobs, final @Nullable WorkflowModel model) {

    var jobs = allJobs;
    for (final var placeholder : allJobs) {
      final var ready =
          placeholder.getMatrixExpression() != null
              && placeholder.getStatus() == WorkflowJobStatus.WAITING
              && this.jobAdvancer.needsFinished(placeholder, jobs)
              && this.jobAdvancer.shouldRun(
                  run, placeholder, this.jobAdvancer.jobDefinition(model, placeholder), jobs);
      if (!ready) {
        continue;
      }
      final var expanded = this.triggerService.expandDynamicMatrix(run, placeholder, jobs, model);
      final var next = new ArrayList<>(jobs);
      next.remove(placeholder);
      next.addAll(expanded);
      jobs = next;
    }
    return jobs;
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

  private void cancelJob(final WorkflowJob job) {

    final boolean wasRunning = job.getStatus() == WorkflowJobStatus.IN_PROGRESS;
    job.setStatus(WorkflowJobStatus.CANCELLED);
    job.setConclusion("cancelled");
    job.setCompletedAt(Instant.now());
    this.jobRepository.save(job);
    this.closeOpenSteps(job.getId(), "cancelled");
    if (wasRunning && job.getRunnerId() != null) {
      // The runner stops the job and reports it; until then it stays BUSY.
      this.runnerCommands.cancelJob(job.getRunnerId(), job.getId());
    }
  }

  /** Steps that never finished (running or not started) are closed so the UI stops spinning. */
  private void closeOpenSteps(final UUID jobId, final String conclusion) {

    final var now = Instant.now();
    for (final var step : this.stepRepository.findAllByJobIdOrderByStepNumberAsc(jobId)) {
      if ("completed".equals(step.getStatus())) {
        continue;
      }
      final boolean wasRunning = "running".equals(step.getStatus());
      step.setStatus("completed");
      step.setConclusion(wasRunning ? conclusion : "cancelled");
      step.setCompletedAt(now);
      this.stepRepository.save(step);
      this.sseEmitterRegistry.complete(step.getId());
    }
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

  public record LogLine(
      int lineNumber, String content, String level, @Nullable Instant timestamp) {}
}
