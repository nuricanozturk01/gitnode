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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.gitnode.os.actions.entities.Runner;
import dev.gitnode.os.actions.entities.RunnerStatus;
import dev.gitnode.os.actions.entities.WorkflowDefinition;
import dev.gitnode.os.actions.entities.WorkflowJob;
import dev.gitnode.os.actions.entities.WorkflowJobStatus;
import dev.gitnode.os.actions.entities.WorkflowRun;
import dev.gitnode.os.actions.entities.WorkflowStep;
import dev.gitnode.os.actions.repositories.RunnerRepository;
import dev.gitnode.os.actions.repositories.WorkflowDefinitionRepository;
import dev.gitnode.os.actions.repositories.WorkflowJobRepository;
import dev.gitnode.os.actions.repositories.WorkflowRunRepository;
import dev.gitnode.os.actions.repositories.WorkflowStepRepository;
import dev.gitnode.os.actions.websocket.RunnerSession;
import dev.gitnode.os.actions.websocket.RunnerSessionRegistry;
import dev.gitnode.os.repo.api.RepoLookupPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobDispatcher unit tests")
class JobDispatcherTest {

  @Mock private WorkflowJobRepository jobRepository;
  @Mock private WorkflowRunRepository runRepository;
  @Mock private WorkflowDefinitionRepository definitionRepository;
  @Mock private WorkflowStepRepository stepRepository;
  @Mock private RunnerRepository runnerRepository;
  @Mock private RunnerSessionRegistry sessionRegistry;
  @Mock private WorkflowParserService parserService;
  @Mock private RunnerSession runnerSession;
  @Mock private RepoLookupPort repoRepository;
  @Mock private SecretVaultService secretVaultService;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private RunContextEnricher runContext;
  @Mock private dev.gitnode.os.actions.repositories.WorkflowLogRepository logRepository;

  private JobDispatcher dispatcher;

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID OTHER_TENANT_ID = UUID.randomUUID();
  private static final UUID RUNNER_ID = UUID.randomUUID();
  private static final UUID JOB_ID = UUID.randomUUID();
  private static final UUID RUN_ID = UUID.randomUUID();
  private static final UUID REPO_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    final var expressionEvaluator = new ExpressionEvaluator();
    this.dispatcher =
        new JobDispatcher(
            this.jobRepository,
            this.runRepository,
            this.definitionRepository,
            this.stepRepository,
            this.runnerRepository,
            this.sessionRegistry,
            this.parserService,
            this.repoRepository,
            expressionEvaluator,
            this.secretVaultService,
            this.redisTemplate,
            this.runContext,
            this.logRepository);
    ReflectionTestUtils.setField(this.dispatcher, "heartbeatTimeoutSeconds", 60);
  }

  @Test
  @DisplayName("dispatch does nothing when no queued jobs")
  void dispatch_doesNothing_whenNoQueuedJobs() {
    when(this.jobRepository.findDispatchableForClaim(50)).thenReturn(List.of());

    this.dispatcher.dispatch();

    verify(this.runnerRepository, never()).findAllByStatus(any());
  }

  @Test
  @DisplayName("dispatch does nothing when no online runners")
  void dispatch_doesNothing_whenNoOnlineRunners() {
    when(this.jobRepository.findDispatchableForClaim(50))
        .thenReturn(List.of(buildJob(List.of("self-hosted"))));
    when(this.runnerRepository.findAllByStatus(RunnerStatus.ONLINE)).thenReturn(List.of());
    when(this.runnerRepository.markStaleRunnersOffline(any())).thenReturn(0);

    this.dispatcher.dispatch();

    verify(this.redisTemplate, never()).convertAndSend(any(), (Object) any());
  }

  @Test
  @DisplayName("dispatch publishes to Redis via dispatch channel even without local session")
  void dispatch_publishesViaRedis_whenRunnerOnlineWithoutLocalSession() {
    final var job = buildJob(List.of("self-hosted"));
    final var runner = buildRunner(List.of("self-hosted"), RunnerStatus.ONLINE, TENANT_ID);

    stubRunToTenant(TENANT_ID);

    when(this.jobRepository.findDispatchableForClaim(50)).thenReturn(List.of(job));
    when(this.runnerRepository.findAllByStatus(RunnerStatus.ONLINE)).thenReturn(List.of(runner));
    when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.empty());
    when(this.runnerRepository.markStaleRunnersOffline(any())).thenReturn(0);

    this.dispatcher.dispatch();

    verify(this.redisTemplate)
        .convertAndSend(eq(JobDispatcher.DISPATCH_CHANNEL_PREFIX + RUNNER_ID), (Object) any());
    assertThat(runner.getStatus()).isEqualTo(RunnerStatus.BUSY);
  }

  @Test
  @DisplayName("dispatch skips job when runner labels do not match")
  void dispatch_skipsJob_whenLabelMismatch() {
    final var job = buildJob(List.of("self-hosted", "docker"));
    final var runner = buildRunner(List.of("self-hosted"), RunnerStatus.ONLINE, TENANT_ID);

    stubRunToTenant(TENANT_ID);

    when(this.jobRepository.findDispatchableForClaim(50)).thenReturn(List.of(job));
    when(this.runnerRepository.findAllByStatus(RunnerStatus.ONLINE)).thenReturn(List.of(runner));
    when(this.runnerRepository.markStaleRunnersOffline(any())).thenReturn(0);

    this.dispatcher.dispatch();

    verify(this.redisTemplate, never()).convertAndSend(any(), (Object) any());
  }

  @Test
  @DisplayName("dispatch publishes JOB_ASSIGNED to Redis channel when labels and tenant match")
  void dispatch_publishesJobAssigned_whenLabelsAndTenantMatch() {
    final var job = buildJob(List.of("self-hosted"));
    final var runner = buildRunner(List.of("self-hosted", "linux"), RunnerStatus.ONLINE, TENANT_ID);

    stubRunToTenant(TENANT_ID);

    when(this.jobRepository.findDispatchableForClaim(50)).thenReturn(List.of(job));
    when(this.runnerRepository.findAllByStatus(RunnerStatus.ONLINE)).thenReturn(List.of(runner));
    when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.empty());
    when(this.runnerRepository.markStaleRunnersOffline(any())).thenReturn(0);

    this.dispatcher.dispatch();

    verify(this.redisTemplate)
        .convertAndSend(
            eq(JobDispatcher.DISPATCH_CHANNEL_PREFIX + RUNNER_ID), contains("JOB_ASSIGNED"));

    verify(this.runnerRepository).save(runner);
    assertThat(runner.getStatus()).isEqualTo(RunnerStatus.BUSY);
  }

  @Test
  @DisplayName("dispatch does NOT assign runner belonging to a different tenant")
  void dispatch_skipsJob_whenRunnerBelongsToDifferentTenant() {
    final var job = buildJob(List.of("self-hosted"));
    final var runner =
        buildRunner(List.of("self-hosted", "linux"), RunnerStatus.ONLINE, OTHER_TENANT_ID);

    stubRunToTenant(TENANT_ID);

    when(this.jobRepository.findDispatchableForClaim(50)).thenReturn(List.of(job));
    when(this.runnerRepository.findAllByStatus(RunnerStatus.ONLINE)).thenReturn(List.of(runner));
    when(this.runnerRepository.markStaleRunnersOffline(any())).thenReturn(0);

    this.dispatcher.dispatch();

    verify(this.redisTemplate, never()).convertAndSend(any(), (Object) any());
    verify(this.runnerRepository, never()).save(runner);
  }

  @Test
  @DisplayName("dispatch skips job when run has no matching repo")
  @SuppressWarnings("unchecked")
  void dispatch_skipsJob_whenRepoNotFound() {
    final var job = buildJob(List.of("self-hosted"));
    final var run = new WorkflowRun();
    run.setId(RUN_ID);
    run.setRepoId(REPO_ID);

    when(this.jobRepository.findDispatchableForClaim(50)).thenReturn(List.of(job));
    when(this.runnerRepository.findAllByStatus(RunnerStatus.ONLINE))
        .thenReturn(List.of(buildRunner(List.of("self-hosted"), RunnerStatus.ONLINE, TENANT_ID)));
    when(this.runRepository.findAllById(Set.of(RUN_ID))).thenReturn(List.of(run));
    when(this.repoRepository.findOwnerIds(Set.of(REPO_ID))).thenReturn(java.util.Map.of());
    when(this.runnerRepository.markStaleRunnersOffline(any())).thenReturn(0);

    this.dispatcher.dispatch();

    verify(this.redisTemplate, never()).convertAndSend(any(), (Object) any());
  }

  @Test
  @DisplayName("dispatch marks stale runners offline")
  void dispatch_marksStaleRunnersOffline() {
    when(this.jobRepository.findDispatchableForClaim(50)).thenReturn(List.of());
    when(this.runnerRepository.markStaleRunnersOffline(any())).thenReturn(2);

    this.dispatcher.dispatch();

    verify(this.runnerRepository).markStaleRunnersOffline(any());
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  // ── housekeeping: orphans and "why is it still queued" ───────────────────

  private final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>
      logs = new ch.qos.logback.core.read.ListAppender<>();

  private void captureLogs() {
    this.logs.start();
    ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(DispatchDiagnostics.class))
        .addAppender(this.logs);
  }

  private void releaseLogs() {
    ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(DispatchDiagnostics.class))
        .detachAppender(this.logs);
  }

  private List<String> warnings() {
    return this.logs.list.stream().map(e -> e.getFormattedMessage()).toList();
  }

  private void stubRecentQueued(final WorkflowJob job, final Runner runner) {
    when(this.jobRepository.findRecentQueued(20)).thenReturn(List.of(job));
    when(this.runnerRepository.findAll()).thenReturn(List.of(runner));
    stubRunToTenant(TENANT_ID);
  }

  @Test
  @DisplayName("housekeeping cancels queued jobs whose repository is gone")
  void housekeeping_cancelsOrphans() {
    when(this.jobRepository.cancelOrphanedQueuedJobs()).thenReturn(3);
    when(this.jobRepository.findRecentQueued(20)).thenReturn(List.of());

    this.dispatcher.housekeeping();

    verify(this.jobRepository).cancelOrphanedQueuedJobs();
  }

  @Test
  @DisplayName("housekeeping explains an OFFLINE runner")
  void housekeeping_reportsOfflineRunner() {
    this.captureLogs();
    try {
      final var job = buildJob(List.of("self-hosted"));
      this.stubRecentQueued(
          job, buildRunner(List.of("self-hosted"), RunnerStatus.OFFLINE, TENANT_ID));

      when(this.stepRepository.save(any(WorkflowStep.class)))
          .thenAnswer(
              i -> {
                final WorkflowStep st = i.getArgument(0);
                st.setId(UUID.randomUUID());
                return st;
              });

      this.dispatcher.housekeeping();

      assertThat(this.warnings())
          .anySatisfy(
              m ->
                  assertThat(m)
                      .contains("not being picked up")
                      .contains("test-runner -> status is OFFLINE"));
    } finally {
      this.releaseLogs();
    }
  }

  @Test
  @DisplayName("housekeeping explains a runner that belongs to another owner")
  void housekeeping_reportsOtherOwner() {
    this.captureLogs();
    try {
      final var job = buildJob(List.of("self-hosted"));
      this.stubRecentQueued(
          job, buildRunner(List.of("self-hosted"), RunnerStatus.ONLINE, OTHER_TENANT_ID));

      when(this.stepRepository.save(any(WorkflowStep.class)))
          .thenAnswer(
              i -> {
                final WorkflowStep st = i.getArgument(0);
                st.setId(UUID.randomUUID());
                return st;
              });

      this.dispatcher.housekeeping();

      assertThat(this.warnings())
          .anySatisfy(m -> assertThat(m).contains("registered for another owner"));
    } finally {
      this.releaseLogs();
    }
  }

  @Test
  @DisplayName("housekeeping lists the missing labels")
  void housekeeping_reportsMissingLabels() {
    this.captureLogs();
    try {
      final var job = buildJob(List.of("self-hosted", "linux"));
      this.stubRecentQueued(
          job, buildRunner(List.of("self-hosted"), RunnerStatus.ONLINE, TENANT_ID));

      when(this.stepRepository.save(any(WorkflowStep.class)))
          .thenAnswer(
              i -> {
                final WorkflowStep st = i.getArgument(0);
                st.setId(UUID.randomUUID());
                return st;
              });

      this.dispatcher.housekeeping();

      assertThat(this.warnings()).anySatisfy(m -> assertThat(m).contains("missing labels [linux]"));
      final var logCaptor =
          ArgumentCaptor.forClass(dev.gitnode.os.actions.entities.WorkflowLog.class);
      verify(this.logRepository).save(logCaptor.capture());
      assertThat(logCaptor.getValue().getContent()).contains("missing labels [linux]");
    } finally {
      this.releaseLogs();
    }
  }

  @Test
  @DisplayName("housekeeping stays quiet when a runner can take the job")
  void housekeeping_quietWhenRunnerMatches() {
    this.captureLogs();
    try {
      final var job = buildJob(List.of("self-hosted"));
      this.stubRecentQueued(
          job, buildRunner(List.of("self-hosted", "linux"), RunnerStatus.ONLINE, TENANT_ID));

      this.dispatcher.housekeeping();

      assertThat(this.warnings()).isEmpty();
    } finally {
      this.releaseLogs();
    }
  }

  @Test
  @DisplayName("housekeeping skips jobs whose owner cannot be resolved")
  void housekeeping_skipsOrphans() {
    this.captureLogs();
    try {
      final var job = buildJob(List.of("self-hosted"));
      when(this.jobRepository.findRecentQueued(20)).thenReturn(List.of(job));
      when(this.runnerRepository.findAll()).thenReturn(List.of());
      final var run = new WorkflowRun();
      run.setId(RUN_ID);
      run.setRepoId(REPO_ID);
      when(this.runRepository.findAllById(Set.of(RUN_ID))).thenReturn(List.of(run));
      when(this.repoRepository.findOwnerIds(Set.of(REPO_ID))).thenReturn(java.util.Map.of());

      this.dispatcher.housekeeping();

      assertThat(this.warnings()).isEmpty();
    } finally {
      this.releaseLogs();
    }
  }

  private static final String WORKFLOW_YAML =
      """
      on: push
      env:
        GLOBAL: g1
        TOKEN: ${{ secrets.TOK }}
      defaults:
        run:
          shell: bash
          working-directory: app
      jobs:
        build:
          runs-on: self-hosted
          timeout-minutes: 15
          needs: setup
          env:
            JOBVAR: ${{ github.ref }}
            GLOBAL: overridden
          steps:
            - id: first
              run: echo hi
              continue-on-error: true
              timeout-minutes: 2
            - id: second
              run: echo ${{ steps.first.outputs.x }} ${{ inputs.v }}
              if: ${{ failure() }}
              shell: sh
              working-directory: sub
              env:
                LOCAL: ${{ inputs.v }}
      """;

  @Test
  @DisplayName("job payload carries merged env, expression context and step settings")
  void dispatch_buildsRichPayload() throws Exception {
    final var defId = UUID.randomUUID();
    final var definition = new WorkflowDefinition();
    definition.setContent(WORKFLOW_YAML);

    final var run = new WorkflowRun();
    run.setId(RUN_ID);
    run.setRepoId(REPO_ID);
    run.setWorkflowDefId(defId);
    run.setWorkflowName("CI");
    run.setRunNumber(7);
    run.setTriggerRef("refs/heads/main");
    run.setTriggerEvent("workflow_dispatch");
    run.setInputs(java.util.Map.of("v", "x"));

    final var setup = new WorkflowJob();
    setup.setId(UUID.randomUUID());
    setup.setRunId(RUN_ID);
    setup.setJobKey("setup");
    setup.setName("setup");
    setup.setStatus(WorkflowJobStatus.SUCCESS);

    final var job = buildJob(List.of("self-hosted"));
    job.setJobKey("build");
    job.setNeeds(List.of("setup"));

    final var runner = buildRunner(List.of("self-hosted", "linux"), RunnerStatus.ONLINE, TENANT_ID);

    when(this.runRepository.findAllById(Set.of(RUN_ID))).thenReturn(List.of(run));
    when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    when(this.repoRepository.findOwnerIds(Set.of(REPO_ID)))
        .thenReturn(java.util.Map.of(REPO_ID, TENANT_ID));
    when(this.repoRepository.findById(REPO_ID))
        .thenReturn(
            Optional.of(new RepoLookupPort.RepoRef(REPO_ID, TENANT_ID, "acme", "app", false)));
    when(this.definitionRepository.findById(defId)).thenReturn(Optional.of(definition));
    when(this.parserService.parseFromContent(WORKFLOW_YAML))
        .thenReturn(
            WorkflowParserService.YAML_MAPPER.readValue(
                WORKFLOW_YAML, dev.gitnode.os.actions.model.WorkflowModel.class));
    when(this.secretVaultService.resolveSecrets(REPO_ID))
        .thenReturn(java.util.Map.of("TOK", "s3cr3t"));
    when(this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(RUN_ID))
        .thenReturn(List.of(setup, job));
    when(this.stepRepository.save(any(WorkflowStep.class)))
        .thenAnswer(
            inv -> {
              final WorkflowStep st = inv.getArgument(0);
              st.setId(UUID.randomUUID());
              return st;
            });
    when(this.jobRepository.findDispatchableForClaim(50)).thenReturn(List.of(job));
    when(this.runnerRepository.findAllByStatus(RunnerStatus.ONLINE)).thenReturn(List.of(runner));
    when(this.runnerRepository.markStaleRunnersOffline(any())).thenReturn(0);

    this.dispatcher.dispatch();

    final var json = ArgumentCaptor.forClass(String.class);
    verify(this.redisTemplate)
        .convertAndSend(eq(JobDispatcher.DISPATCH_CHANNEL_PREFIX + RUNNER_ID), json.capture());
    final JsonNode payload = new ObjectMapper().readTree(json.getValue()).path("data").path("job");

    final var env = payload.path("env");
    assertThat(env.path("GLOBAL").asText()).isEqualTo("overridden");
    assertThat(env.path("JOBVAR").asText()).isEqualTo("refs/heads/main");
    assertThat(env.path("TOKEN").asText()).isEqualTo("s3cr3t");
    assertThat(env.path("GITNODE_OWNER").asText()).isEqualTo("acme");
    assertThat(env.path("GITNODE_REPO").asText()).isEqualTo("app");
    assertThat(env.path("GITNODE_REF").asText()).isEqualTo("refs/heads/main");
    assertThat(payload.path("timeoutMinutes").asInt()).isEqualTo(15);

    final var ctx = payload.path("context");
    assertThat(ctx.path("github.ref").asText()).isEqualTo("refs/heads/main");
    assertThat(ctx.path("github.event_name").asText()).isEqualTo("workflow_dispatch");
    assertThat(ctx.path("github.repository").asText()).isEqualTo("acme/app");
    assertThat(ctx.path("github.repository_owner").asText()).isEqualTo("acme");
    assertThat(ctx.path("github.run_number").asText()).isEqualTo("7");
    assertThat(ctx.path("github.workflow").asText()).isEqualTo("CI");
    assertThat(ctx.path("github.job").asText()).isEqualTo("build");
    assertThat(ctx.path("inputs.v").asText()).isEqualTo("x");
    assertThat(ctx.path("needs.setup.result").asText()).isEqualTo("success");
    ctx.fieldNames().forEachRemaining(name -> assertThat(name).doesNotStartWith("secrets."));

    final var first = payload.path("steps").get(0);
    assertThat(first.path("key").asText()).isEqualTo("first");
    assertThat(first.path("continueOnError").asBoolean()).isTrue();
    assertThat(first.path("timeoutMinutes").asInt()).isEqualTo(2);
    assertThat(first.path("shell").asText()).isEqualTo("bash");
    assertThat(first.path("workingDirectory").asText()).isEqualTo("app");

    final var second = payload.path("steps").get(1);
    assertThat(second.path("key").asText()).isEqualTo("second");
    assertThat(second.path("continueOnError").asBoolean()).isFalse();
    assertThat(second.path("shell").asText()).isEqualTo("sh");
    assertThat(second.path("workingDirectory").asText()).isEqualTo("sub");
    assertThat(second.path("condition").asText()).isEqualTo("${{ failure() }}");
    assertThat(second.path("run").asText())
        .as("steps.* is left for the runner, inputs.* is resolved")
        .isEqualTo("echo ${{ steps.first.outputs.x }} x");
    assertThat(second.path("env").path("LOCAL").asText()).isEqualTo("x");
  }

  private void stubRunToTenant(final UUID tenantId) {
    final var run = new WorkflowRun();
    run.setId(RUN_ID);
    run.setRepoId(REPO_ID);
    when(this.runRepository.findAllById(Set.of(RUN_ID))).thenReturn(List.of(run));
    when(this.repoRepository.findOwnerIds(Set.of(REPO_ID)))
        .thenReturn(java.util.Map.of(REPO_ID, tenantId));
  }

  private static WorkflowJob buildJob(final List<String> requiredLabels) {
    final var job = new WorkflowJob();
    job.setId(JOB_ID);
    job.setRunId(RUN_ID);
    job.setName("build");
    job.setRunnerLabels(requiredLabels);
    job.setStatus(WorkflowJobStatus.QUEUED);
    return job;
  }

  private static Runner buildRunner(
      final List<String> labels, final RunnerStatus status, final UUID tenantId) {
    final var runner = new Runner();
    runner.setId(RUNNER_ID);
    runner.setName("test-runner");
    runner.setLabels(labels);
    runner.setStatus(status);
    runner.setTenantId(tenantId);
    runner.setLastHeartbeat(Instant.now());
    runner.setTokenHash("hash");
    return runner;
  }
}
