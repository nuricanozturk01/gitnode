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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.actions.entities.Runner;
import dev.gitnode.os.actions.entities.RunnerStatus;
import dev.gitnode.os.actions.entities.WorkflowDefinition;
import dev.gitnode.os.actions.entities.WorkflowJob;
import dev.gitnode.os.actions.entities.WorkflowJobStatus;
import dev.gitnode.os.actions.entities.WorkflowLog;
import dev.gitnode.os.actions.entities.WorkflowRun;
import dev.gitnode.os.actions.entities.WorkflowRunStatus;
import dev.gitnode.os.actions.entities.WorkflowStep;
import dev.gitnode.os.actions.model.WorkflowModel;
import dev.gitnode.os.actions.repositories.RunnerRepository;
import dev.gitnode.os.actions.repositories.WorkflowDefinitionRepository;
import dev.gitnode.os.actions.repositories.WorkflowJobRepository;
import dev.gitnode.os.actions.repositories.WorkflowLogRepository;
import dev.gitnode.os.actions.repositories.WorkflowRunRepository;
import dev.gitnode.os.actions.repositories.WorkflowStepRepository;
import dev.gitnode.os.actions.websocket.RunStatusSseRegistry;
import dev.gitnode.os.actions.websocket.SseEmitterRegistry;
import dev.gitnode.os.events.actions.WorkflowJobQueuedEvent;
import dev.gitnode.os.events.actions.WorkflowJobStartedEvent;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

  @Mock private WorkflowDefinitionRepository definitionRepository;
  @Mock private WorkflowParserService parserService;
  @Mock private RepoLookupPort repoLookup;
  @Mock private WorkflowTriggerService triggerService;

  @Mock private RunnerCommands runnerCommands;

  private WorkflowExecutionService service;

  @BeforeEach
  void setUp() {
    this.service =
        new WorkflowExecutionService(
            this.jobRepository,
            this.runRepository,
            this.stepRepository,
            this.logRepository,
            this.runnerRepository,
            this.eventPublisher,
            this.sseEmitterRegistry,
            this.runStatusSseRegistry,
            new JobAdvancer(
                this.definitionRepository,
                this.parserService,
                new ExpressionEvaluator(),
                this.repoLookup),
            this.triggerService,
            this.runnerCommands);
  }

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
  @DisplayName("cancelRun tells the runner, closes running steps and keeps the cancelled result")
  void cancelRun_notifiesRunnerAndClosesSteps() {
    final var job = buildJob(WorkflowJobStatus.IN_PROGRESS);
    job.setRunnerId(RUNNER_ID);
    final var run = buildRun(WorkflowRunStatus.IN_PROGRESS);
    final var running = buildStep();
    running.setStatus("running");

    when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    when(this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(RUN_ID)).thenReturn(List.of(job));
    when(this.stepRepository.findAllByJobIdOrderByStepNumberAsc(JOB_ID))
        .thenReturn(List.of(running));

    this.service.cancelRun(RUN_ID);

    assertThat(job.getStatus()).isEqualTo(WorkflowJobStatus.CANCELLED);
    assertThat(running.getStatus()).isEqualTo("completed");
    assertThat(running.getConclusion()).isEqualTo("cancelled");
    verify(this.runnerCommands).cancelJob(RUNNER_ID, JOB_ID);

    // The runner confirms later; that must not turn the job into a success.
    final var runner = buildRunner();
    when(this.runnerRepository.findById(RUNNER_ID)).thenReturn(Optional.of(runner));
    when(this.jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
    when(this.jobRepository.countByRunnerIdAndStatus(RUNNER_ID, WorkflowJobStatus.IN_PROGRESS))
        .thenReturn(0L);

    this.service.handleJobCompleted(JOB_ID, "success");

    assertThat(job.getStatus()).isEqualTo(WorkflowJobStatus.CANCELLED);
    assertThat(runner.getStatus()).isEqualTo(RunnerStatus.ONLINE);
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
    when(this.stepRepository.existsById(STEP_ID)).thenReturn(true);
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

  // ── needs and if: semantics ──────────────────────────────────────────────

  private WorkflowRun runWithWorkflow(final String yaml) throws Exception {
    final var defId = UUID.randomUUID();
    final var run = buildRun(WorkflowRunStatus.IN_PROGRESS);
    run.setWorkflowDefId(defId);
    final var definition = new WorkflowDefinition();
    definition.setContent(yaml);
    lenient().when(this.definitionRepository.findById(defId)).thenReturn(Optional.of(definition));
    lenient()
        .when(this.parserService.parseFromContent(yaml))
        .thenReturn(WorkflowParserService.YAML_MAPPER.readValue(yaml, WorkflowModel.class));
    return run;
  }

  private static WorkflowJob namedJob(
      final String key, final WorkflowJobStatus status, final List<String> needs) {
    final var job = buildJob(status);
    job.setId(UUID.randomUUID());
    job.setJobKey(key);
    job.setName(key);
    job.setNeeds(needs);
    return job;
  }

  private void complete(
      final WorkflowRun run,
      final WorkflowJob finished,
      final List<WorkflowJob> all,
      final String conclusion) {
    finished.setId(JOB_ID);
    lenient().when(this.jobRepository.findById(JOB_ID)).thenReturn(Optional.of(finished));
    lenient().when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    lenient().when(this.jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    lenient().when(this.runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    lenient().when(this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(RUN_ID)).thenReturn(all);
    this.service.handleJobCompleted(JOB_ID, conclusion);
  }

  private static final String CLEANUP_WORKFLOW =
      """
      on: push
      jobs:
        test:
          runs-on: x
          steps: []
        cleanup:
          runs-on: x
          needs: test
          if: always()
          steps: []
        onfail:
          runs-on: x
          needs: test
          if: ${{ failure() }}
          steps: []
        report:
          runs-on: x
          needs: test
          if: needs.test.result == 'success'
          steps: []
        deploy:
          runs-on: x
          needs: test
          steps: []
      """;

  @Test
  @DisplayName("a failed need skips dependents unless their if: allows it (always/failure)")
  void needFails_conditionsDecide() throws Exception {
    final var run = this.runWithWorkflow(CLEANUP_WORKFLOW);
    final var test = namedJob("test", WorkflowJobStatus.IN_PROGRESS, null);
    final var cleanup = namedJob("cleanup", WorkflowJobStatus.WAITING, List.of("test"));
    final var onfail = namedJob("onfail", WorkflowJobStatus.WAITING, List.of("test"));
    final var report = namedJob("report", WorkflowJobStatus.WAITING, List.of("test"));
    final var deploy = namedJob("deploy", WorkflowJobStatus.WAITING, List.of("test"));

    this.complete(run, test, List.of(test, cleanup, onfail, report, deploy), "failure");

    assertThat(cleanup.getStatus()).isEqualTo(WorkflowJobStatus.QUEUED);
    assertThat(onfail.getStatus()).isEqualTo(WorkflowJobStatus.QUEUED);
    assertThat(report.getStatus()).isEqualTo(WorkflowJobStatus.SKIPPED);
    assertThat(deploy.getStatus()).isEqualTo(WorkflowJobStatus.SKIPPED);
  }

  @Test
  @DisplayName(
      "a successful need runs plain dependents and success-conditioned ones, not failure-only")
  void needSucceeds_conditionsDecide() throws Exception {
    final var run = this.runWithWorkflow(CLEANUP_WORKFLOW);
    final var test = namedJob("test", WorkflowJobStatus.IN_PROGRESS, null);
    final var cleanup = namedJob("cleanup", WorkflowJobStatus.WAITING, List.of("test"));
    final var onfail = namedJob("onfail", WorkflowJobStatus.WAITING, List.of("test"));
    final var report = namedJob("report", WorkflowJobStatus.WAITING, List.of("test"));
    final var deploy = namedJob("deploy", WorkflowJobStatus.WAITING, List.of("test"));

    this.complete(run, test, List.of(test, cleanup, onfail, report, deploy), "success");

    assertThat(cleanup.getStatus()).isEqualTo(WorkflowJobStatus.QUEUED);
    assertThat(onfail.getStatus()).isEqualTo(WorkflowJobStatus.SKIPPED);
    assertThat(report.getStatus()).isEqualTo(WorkflowJobStatus.QUEUED);
    assertThat(deploy.getStatus()).isEqualTo(WorkflowJobStatus.QUEUED);
  }

  @Test
  @DisplayName("a job waits until every job it needs has finished")
  void waitsForAllNeeds() throws Exception {
    final var yaml =
        """
        on: push
        jobs:
          a: {runs-on: x, steps: []}
          b: {runs-on: x, steps: []}
          c: {runs-on: x, needs: [a, b], steps: []}
        """;
    final var run = this.runWithWorkflow(yaml);
    final var a = namedJob("a", WorkflowJobStatus.IN_PROGRESS, null);
    final var b = namedJob("b", WorkflowJobStatus.IN_PROGRESS, null);
    final var c = namedJob("c", WorkflowJobStatus.WAITING, List.of("a", "b"));

    this.complete(run, a, List.of(a, b, c), "success");
    assertThat(c.getStatus()).isEqualTo(WorkflowJobStatus.WAITING);

    b.setStatus(WorkflowJobStatus.SUCCESS);
    this.complete(run, b, List.of(a, b, c), "success");
    assertThat(c.getStatus()).isEqualTo(WorkflowJobStatus.QUEUED);
  }

  @Test
  @DisplayName("skipping a job cascades to the jobs that need it")
  void skipCascades() throws Exception {
    final var yaml =
        """
        on: push
        jobs:
          a: {runs-on: x, steps: []}
          b: {runs-on: x, needs: a, steps: []}
          c: {runs-on: x, needs: b, steps: []}
        """;
    final var run = this.runWithWorkflow(yaml);
    final var a = namedJob("a", WorkflowJobStatus.IN_PROGRESS, null);
    final var b = namedJob("b", WorkflowJobStatus.WAITING, List.of("a"));
    final var c = namedJob("c", WorkflowJobStatus.WAITING, List.of("b"));

    this.complete(run, a, List.of(a, b, c), "failure");

    assertThat(b.getStatus()).isEqualTo(WorkflowJobStatus.SKIPPED);
    assertThat(c.getStatus()).isEqualTo(WorkflowJobStatus.SKIPPED);
    assertThat(run.getStatus()).isEqualTo(WorkflowRunStatus.FAILURE);
  }

  @Test
  @DisplayName("an already skipped need skips its dependent, but not one with always()")
  void skippedNeed() throws Exception {
    final var yaml =
        """
        on: push
        jobs:
          a: {runs-on: x, steps: []}
          skipme: {runs-on: x, if: "false", steps: []}
          plain: {runs-on: x, needs: [a, skipme], steps: []}
          tolerant: {runs-on: x, needs: [a, skipme], if: always(), steps: []}
        """;
    final var run = this.runWithWorkflow(yaml);
    final var a = namedJob("a", WorkflowJobStatus.IN_PROGRESS, null);
    final var skipme = namedJob("skipme", WorkflowJobStatus.SKIPPED, null);
    final var plain = namedJob("plain", WorkflowJobStatus.WAITING, List.of("a", "skipme"));
    final var tolerant = namedJob("tolerant", WorkflowJobStatus.WAITING, List.of("a", "skipme"));

    this.complete(run, a, List.of(a, skipme, plain, tolerant), "success");

    assertThat(plain.getStatus()).isEqualTo(WorkflowJobStatus.SKIPPED);
    assertThat(tolerant.getStatus()).isEqualTo(WorkflowJobStatus.QUEUED);
  }

  @Test
  @DisplayName("matrix jobs share a key: dependents wait for every expansion")
  void matrixNeedsWaitForAllExpansions() throws Exception {
    final var yaml =
        """
        on: push
        jobs:
          build: {runs-on: x, steps: []}
          ship: {runs-on: x, needs: build, steps: []}
        """;
    final var run = this.runWithWorkflow(yaml);
    final var linux = namedJob("build", WorkflowJobStatus.IN_PROGRESS, null);
    linux.setName("build (linux)");
    final var mac = namedJob("build", WorkflowJobStatus.IN_PROGRESS, null);
    mac.setName("build (mac)");
    final var ship = namedJob("ship", WorkflowJobStatus.WAITING, List.of("build"));

    this.complete(run, linux, List.of(linux, mac, ship), "success");
    assertThat(ship.getStatus()).isEqualTo(WorkflowJobStatus.WAITING);

    mac.setStatus(WorkflowJobStatus.FAILURE);
    this.complete(run, mac, List.of(linux, mac, ship), "failure");
    assertThat(ship.getStatus()).isEqualTo(WorkflowJobStatus.SKIPPED);
  }

  @Test
  @DisplayName("without a stored workflow definition, needs still resolve with success() semantics")
  void noDefinition_defaultSemantics() {
    final var run = buildRun(WorkflowRunStatus.IN_PROGRESS);
    final var test = namedJob("test", WorkflowJobStatus.IN_PROGRESS, null);
    final var deploy = namedJob("deploy", WorkflowJobStatus.WAITING, List.of("test"));

    this.complete(run, test, List.of(test, deploy), "success");

    assertThat(deploy.getStatus()).isEqualTo(WorkflowJobStatus.QUEUED);
  }

  private static final String MATRIX_YAML =
      """
      on: push
      jobs:
        build:
          runs-on: x
          strategy:
            matrix:
              os: [a, b, c]
          steps: []
      """;

  private static WorkflowJob matrixJob(final String os, final WorkflowJobStatus status) {
    final var job = namedJob("build", status, null);
    job.setName("build (" + os + ")");
    job.setMatrixValues(java.util.Map.of("os", os));
    return job;
  }

  @Test
  @DisplayName("fail-fast: one failed matrix job cancels the ones that have not started")
  void failFast_cancelsUnstartedSiblings() throws Exception {
    final var run = this.runWithWorkflow(MATRIX_YAML);
    final var failing = matrixJob("a", WorkflowJobStatus.IN_PROGRESS);
    final var queued = matrixJob("b", WorkflowJobStatus.QUEUED);
    final var running = matrixJob("c", WorkflowJobStatus.IN_PROGRESS);

    this.complete(run, failing, List.of(failing, queued, running), "failure");

    assertThat(queued.getStatus()).isEqualTo(WorkflowJobStatus.CANCELLED);
    assertThat(running.getStatus())
        .as("jobs that already run are left to finish")
        .isEqualTo(WorkflowJobStatus.IN_PROGRESS);
  }

  @Test
  @DisplayName("fail-fast: false keeps the other matrix jobs")
  void failFastFalse_keepsSiblings() throws Exception {
    final var run =
        this.runWithWorkflow(MATRIX_YAML.replace("matrix:", "fail-fast: false\n    matrix:"));
    final var failing = matrixJob("a", WorkflowJobStatus.IN_PROGRESS);
    final var queued = matrixJob("b", WorkflowJobStatus.QUEUED);

    this.complete(run, failing, List.of(failing, queued), "failure");

    assertThat(queued.getStatus()).isEqualTo(WorkflowJobStatus.QUEUED);
  }

  @Test
  @DisplayName("a successful matrix job cancels nothing")
  void success_cancelsNothing() throws Exception {
    final var run = this.runWithWorkflow(MATRIX_YAML);
    final var ok = matrixJob("a", WorkflowJobStatus.IN_PROGRESS);
    final var queued = matrixJob("b", WorkflowJobStatus.QUEUED);

    this.complete(run, ok, List.of(ok, queued), "success");

    assertThat(queued.getStatus()).isEqualTo(WorkflowJobStatus.QUEUED);
  }

  @Test
  @DisplayName("fail-fast does not touch other jobs of the run")
  void failFast_onlyAffectsSameMatrix() throws Exception {
    final var run = this.runWithWorkflow(MATRIX_YAML);
    final var failing = matrixJob("a", WorkflowJobStatus.IN_PROGRESS);
    final var other = namedJob("lint", WorkflowJobStatus.QUEUED, null);

    this.complete(run, failing, List.of(failing, other), "failure");

    assertThat(other.getStatus()).isEqualTo(WorkflowJobStatus.QUEUED);
  }

  private static WorkflowJob buildJob(final WorkflowJobStatus status) {
    final var job = new WorkflowJob();
    job.setId(JOB_ID);
    job.setRunId(RUN_ID);
    job.setName("build");
    job.setRunnerLabels(List.of("self-hosted"));
    job.setStatus(status);
    return job;
  }

  // ── rerun ─────────────────────────────────────────────────────────────────

  private static WorkflowJob rerunJob(
      final String key, final WorkflowJobStatus status, final List<String> needs) {
    final var job = new WorkflowJob();
    job.setId(UUID.randomUUID());
    job.setRunId(RUN_ID);
    job.setJobKey(key);
    job.setName(key);
    job.setRunnerLabels(List.of("self-hosted"));
    job.setStatus(status);
    job.setNeeds(needs);
    job.setConclusion(status == WorkflowJobStatus.SUCCESS ? "success" : "failure");
    return job;
  }

  @Test
  @DisplayName("rerun failed only resets the failed job, keeps the green one and bumps the attempt")
  void rerun_failedOnly_resetsFailedJobs() {
    final var build = rerunJob("build", WorkflowJobStatus.SUCCESS, null);
    final var test = rerunJob("test", WorkflowJobStatus.FAILURE, List.of("build"));
    final var run = buildRun(WorkflowRunStatus.FAILURE);
    run.setConclusion("failure");
    when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    when(this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(RUN_ID))
        .thenReturn(List.of(build, test));

    this.service.rerun(RUN_ID, true, null);

    assertThat(build.getStatus()).isEqualTo(WorkflowJobStatus.SUCCESS);
    assertThat(test.getStatus()).isEqualTo(WorkflowJobStatus.QUEUED);
    assertThat(test.getConclusion()).isNull();
    assertThat(run.getRunAttempt()).isEqualTo(2);
    assertThat(run.getStatus()).isEqualTo(WorkflowRunStatus.QUEUED);
    assertThat(run.getConclusion()).isNull();
    verify(this.stepRepository).deleteAllByJobIds(List.of(test.getId()));
    verify(this.eventPublisher).publishEvent(any(WorkflowJobQueuedEvent.class));
  }

  @Test
  @DisplayName("rerun of one job also resets the jobs that need it")
  void rerun_singleJob_resetsDependents() {
    final var build = rerunJob("build", WorkflowJobStatus.SUCCESS, null);
    final var test = rerunJob("test", WorkflowJobStatus.SUCCESS, List.of("build"));
    final var deploy = rerunJob("deploy", WorkflowJobStatus.SUCCESS, List.of("test"));
    final var run = buildRun(WorkflowRunStatus.SUCCESS);
    when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    when(this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(RUN_ID))
        .thenReturn(List.of(build, test, deploy));

    this.service.rerun(RUN_ID, false, test.getId());

    assertThat(build.getStatus()).isEqualTo(WorkflowJobStatus.SUCCESS);
    assertThat(test.getStatus()).isEqualTo(WorkflowJobStatus.QUEUED);
    assertThat(deploy.getStatus()).isEqualTo(WorkflowJobStatus.WAITING);
    verify(this.stepRepository).deleteAllByJobIds(List.of(test.getId(), deploy.getId()));
  }

  @Test
  @DisplayName("rerun is refused while the run is still going")
  void rerun_rejectsActiveRun() {
    final var run = buildRun(WorkflowRunStatus.IN_PROGRESS);
    when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

    assertThatThrownBy(() -> this.service.rerun(RUN_ID, false, null))
        .isInstanceOf(BadRequestException.class);

    verify(this.stepRepository, never()).deleteAllByJobIds(any());
  }

  @Test
  @DisplayName("rerun failed only with nothing failed is refused")
  void rerun_failedOnly_withoutFailures() {
    final var build = rerunJob("build", WorkflowJobStatus.SUCCESS, null);
    final var run = buildRun(WorkflowRunStatus.SUCCESS);
    when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    when(this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(RUN_ID)).thenReturn(List.of(build));

    assertThatThrownBy(() -> this.service.rerun(RUN_ID, true, null))
        .isInstanceOf(BadRequestException.class);
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
