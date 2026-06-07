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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.actions.entities.Runner;
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
import com.nuricanozturk.originhub.actions.websocket.RunStatusSseRegistry;
import com.nuricanozturk.originhub.actions.websocket.SseEmitterRegistry;
import com.nuricanozturk.originhub.events.actions.WorkflowJobStartedEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowExecutionService unit tests")
class WorkflowExecutionServiceTest {

  @Mock private WorkflowJobRepository jobRepository;
  @Mock private WorkflowRunRepository runRepository;
  @Mock private WorkflowStepRepository stepRepository;
  @Mock private WorkflowLogRepository logRepository;
  @Mock private RunnerRepository runnerRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private SseEmitterRegistry sseEmitterRegistry;
  @Mock private RunStatusSseRegistry runStatusSseRegistry;

  @InjectMocks private WorkflowExecutionService service;

  private static final UUID RUNNER_ID = UUID.randomUUID();
  private static final UUID JOB_ID = UUID.randomUUID();
  private static final UUID RUN_ID = UUID.randomUUID();
  private static final UUID REPO_ID = UUID.randomUUID();
  private static final UUID STEP_ID = UUID.randomUUID();

  @Test
  @DisplayName("handleJobClaimed sets status IN_PROGRESS and transitions run to IN_PROGRESS")
  void handleJobClaimed_setsInProgress() {
    final var job = buildJob(WorkflowJobStatus.QUEUED);
    final var run = buildRun(WorkflowRunStatus.QUEUED);

    when(this.jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
    when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    when(this.jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(this.runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    this.service.handleJobClaimed(JOB_ID, RUNNER_ID);

    assertThat(job.getStatus()).isEqualTo(WorkflowJobStatus.IN_PROGRESS);
    assertThat(job.getRunnerId()).isEqualTo(RUNNER_ID);
    assertThat(job.getStartedAt()).isNotNull();
    assertThat(run.getStatus()).isEqualTo(WorkflowRunStatus.IN_PROGRESS);
    verify(this.eventPublisher).publishEvent(any(WorkflowJobStartedEvent.class));
  }

  @Test
  @DisplayName("handleStepStarted sets step status to running")
  void handleStepStarted_setsRunning() {
    final var step = buildStep();
    when(this.stepRepository.findById(STEP_ID)).thenReturn(Optional.of(step));
    when(this.stepRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    this.service.handleStepStarted(STEP_ID);

    assertThat(step.getStatus()).isEqualTo("running");
    assertThat(step.getStartedAt()).isNotNull();
  }

  @Test
  @DisplayName("handleStepCompleted sets step completed and closes SSE emitter")
  void handleStepCompleted_setsCompletedAndClosesSse() {
    final var step = buildStep();
    when(this.stepRepository.findById(STEP_ID)).thenReturn(Optional.of(step));
    when(this.stepRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    this.service.handleStepCompleted(STEP_ID, "success");

    assertThat(step.getStatus()).isEqualTo("completed");
    assertThat(step.getConclusion()).isEqualTo("success");
    verify(this.sseEmitterRegistry).complete(STEP_ID);
  }

  @Test
  @DisplayName("handleJobCompleted marks run SUCCESS when all jobs succeed")
  void handleJobCompleted_marksRunSuccess_whenAllJobsSucceed() {
    final var job = buildJob(WorkflowJobStatus.IN_PROGRESS);
    job.setRunnerId(RUNNER_ID);
    final var run = buildRun(WorkflowRunStatus.IN_PROGRESS);
    final var runner = buildRunner();

    when(this.jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
    when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    when(this.jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(this.runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(this.runnerRepository.findById(RUNNER_ID)).thenReturn(Optional.of(runner));
    when(this.jobRepository.countByRunnerIdAndStatus(RUNNER_ID, WorkflowJobStatus.IN_PROGRESS))
        .thenReturn(0L);
    when(this.runnerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(RUN_ID)).thenReturn(List.of(job));

    this.service.handleJobCompleted(JOB_ID, "success");

    assertThat(job.getStatus()).isEqualTo(WorkflowJobStatus.SUCCESS);
    assertThat(run.getStatus()).isEqualTo(WorkflowRunStatus.SUCCESS);
    assertThat(run.getConclusion()).isEqualTo("success");
  }

  @Test
  @DisplayName("handleJobCompleted marks run FAILURE when any job fails")
  void handleJobCompleted_marksRunFailure_whenJobFails() {
    final var job = buildJob(WorkflowJobStatus.IN_PROGRESS);
    final var run = buildRun(WorkflowRunStatus.IN_PROGRESS);

    when(this.jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
    when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    when(this.jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(this.runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(RUN_ID)).thenReturn(List.of(job));

    this.service.handleJobCompleted(JOB_ID, "failure");

    assertThat(run.getStatus()).isEqualTo(WorkflowRunStatus.FAILURE);
    assertThat(run.getConclusion()).isEqualTo("failure");
  }

  @Test
  @DisplayName("ingestLog saves log entry and broadcasts via SSE")
  void ingestLog_savesAndBroadcasts() {
    when(this.logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    this.service.ingestLog(STEP_ID, 1, "npm install", "info");

    verify(this.logRepository).save(any(WorkflowLog.class));
    verify(this.sseEmitterRegistry).broadcast(eq(STEP_ID), any());
  }

  @Test
  @DisplayName("handleJobCompleted marks WAITING jobs QUEUED when their needs are met")
  void handleJobCompleted_advancesWaitingJobs() {
    final var completedJob = buildJob(WorkflowJobStatus.IN_PROGRESS);
    completedJob.setName("test");

    final var waitingJob = buildJob(WorkflowJobStatus.WAITING);
    waitingJob.setId(UUID.randomUUID());
    waitingJob.setName("deploy");
    waitingJob.setNeeds(List.of("test"));

    final var run = buildRun(WorkflowRunStatus.IN_PROGRESS);

    when(this.jobRepository.findById(JOB_ID)).thenReturn(Optional.of(completedJob));
    when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    when(this.jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(RUN_ID))
        .thenReturn(List.of(completedJob, waitingJob));

    this.service.handleJobCompleted(JOB_ID, "success");

    assertThat(waitingJob.getStatus()).isEqualTo(WorkflowJobStatus.QUEUED);
  }

  @Test
  @DisplayName("handleJobCompleted marks WAITING jobs SKIPPED when a needed job fails")
  void handleJobCompleted_skipsWaitingJobsWhenNeedFails() {
    final var failedJob = buildJob(WorkflowJobStatus.IN_PROGRESS);
    failedJob.setName("test");

    final var waitingJob = buildJob(WorkflowJobStatus.WAITING);
    waitingJob.setId(UUID.randomUUID());
    waitingJob.setName("deploy");
    waitingJob.setNeeds(List.of("test"));

    final var run = buildRun(WorkflowRunStatus.IN_PROGRESS);

    when(this.jobRepository.findById(JOB_ID)).thenReturn(Optional.of(failedJob));
    when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    when(this.jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(RUN_ID))
        .thenReturn(List.of(failedJob, waitingJob));

    this.service.handleJobCompleted(JOB_ID, "failure");

    assertThat(waitingJob.getStatus()).isEqualTo(WorkflowJobStatus.SKIPPED);
    assertThat(waitingJob.getConclusion()).isEqualTo("skipped");
  }

  @Test
  @DisplayName("handleJobCompleted marks run FAILURE and SKIPPED downstream jobs complete run")
  void handleJobCompleted_runCompletesWhenAllJobsTerminal() {
    final var failedJob = buildJob(WorkflowJobStatus.IN_PROGRESS);
    failedJob.setName("test");

    final var skippedJob = buildJob(WorkflowJobStatus.WAITING);
    skippedJob.setId(UUID.randomUUID());
    skippedJob.setName("deploy");
    skippedJob.setNeeds(List.of("test"));

    final var run = buildRun(WorkflowRunStatus.IN_PROGRESS);

    when(this.jobRepository.findById(JOB_ID)).thenReturn(Optional.of(failedJob));
    when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    when(this.jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(this.runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(RUN_ID))
        .thenReturn(List.of(failedJob, skippedJob));

    this.service.handleJobCompleted(JOB_ID, "failure");

    assertThat(run.getStatus()).isEqualTo(WorkflowRunStatus.FAILURE);
    assertThat(skippedJob.getStatus()).isEqualTo(WorkflowJobStatus.SKIPPED);
  }

  @Test
  @DisplayName("handleRunnerDisconnected marks all IN_PROGRESS jobs as FAILURE")
  void handleRunnerDisconnected_marksJobsAsFailed() {
    final var job = buildJob(WorkflowJobStatus.IN_PROGRESS);
    final var run = buildRun(WorkflowRunStatus.IN_PROGRESS);
    final var step = buildStep();
    step.setStatus("running");

    when(this.jobRepository.findAllByRunnerIdAndStatus(RUNNER_ID, WorkflowJobStatus.IN_PROGRESS))
        .thenReturn(List.of(job));
    when(this.stepRepository.findAllByJobIdOrderByStepNumberAsc(JOB_ID)).thenReturn(List.of(step));
    when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    when(this.jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(this.runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(RUN_ID)).thenReturn(List.of(job));

    this.service.handleRunnerDisconnected(RUNNER_ID);

    assertThat(job.getStatus()).isEqualTo(WorkflowJobStatus.FAILURE);
    assertThat(job.getConclusion()).isEqualTo("failure");
    assertThat(job.getCompletedAt()).isNotNull();
  }

  @Test
  @DisplayName("handleRunnerDisconnected closes SSE emitters for running steps")
  void handleRunnerDisconnected_closesRunningStepSseEmitters() {
    final var job = buildJob(WorkflowJobStatus.IN_PROGRESS);
    final var run = buildRun(WorkflowRunStatus.IN_PROGRESS);

    final var runningStep = buildStep();
    runningStep.setStatus("running");

    final var completedStep = buildStep();
    completedStep.setId(UUID.randomUUID());
    completedStep.setStatus("completed");

    when(this.jobRepository.findAllByRunnerIdAndStatus(RUNNER_ID, WorkflowJobStatus.IN_PROGRESS))
        .thenReturn(List.of(job));
    when(this.stepRepository.findAllByJobIdOrderByStepNumberAsc(JOB_ID))
        .thenReturn(List.of(runningStep, completedStep));
    when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    when(this.jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(this.runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(RUN_ID)).thenReturn(List.of(job));

    this.service.handleRunnerDisconnected(RUNNER_ID);

    verify(this.sseEmitterRegistry).complete(STEP_ID);
    verify(this.sseEmitterRegistry, org.mockito.Mockito.never()).complete(completedStep.getId());
  }

  @Test
  @DisplayName("handleRunnerDisconnected completes the run when all jobs reach terminal state")
  void handleRunnerDisconnected_completesRun() {
    final var job = buildJob(WorkflowJobStatus.IN_PROGRESS);
    final var run = buildRun(WorkflowRunStatus.IN_PROGRESS);

    when(this.jobRepository.findAllByRunnerIdAndStatus(RUNNER_ID, WorkflowJobStatus.IN_PROGRESS))
        .thenReturn(List.of(job));
    when(this.stepRepository.findAllByJobIdOrderByStepNumberAsc(JOB_ID)).thenReturn(List.of());
    when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    when(this.jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(this.runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(RUN_ID)).thenReturn(List.of(job));

    this.service.handleRunnerDisconnected(RUNNER_ID);

    assertThat(run.getStatus()).isEqualTo(WorkflowRunStatus.FAILURE);
    assertThat(run.getConclusion()).isEqualTo("failure");
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static WorkflowJob buildJob(final WorkflowJobStatus status) {
    final var job = new WorkflowJob();
    job.setId(JOB_ID);
    job.setRunId(RUN_ID);
    job.setName("build");
    job.setRunnerLabels(List.of("self-hosted"));
    job.setStatus(status);
    return job;
  }

  private static WorkflowRun buildRun(final WorkflowRunStatus status) {
    final var run = new WorkflowRun();
    run.setId(RUN_ID);
    run.setRepoId(REPO_ID);
    run.setWorkflowName("CI");
    run.setRunNumber(1);
    run.setTriggerEvent("push");
    run.setStatus(status);
    return run;
  }

  private static WorkflowStep buildStep() {
    final var step = new WorkflowStep();
    step.setId(STEP_ID);
    step.setJobId(JOB_ID);
    step.setStepNumber(1);
    step.setName("Checkout");
    return step;
  }

  private static Runner buildRunner() {
    final var runner = new Runner();
    runner.setId(RUNNER_ID);
    runner.setName("test-runner");
    runner.setLabels(List.of("self-hosted"));
    runner.setStatus(RunnerStatus.BUSY);
    runner.setTokenHash("hash");
    return runner;
  }
}
