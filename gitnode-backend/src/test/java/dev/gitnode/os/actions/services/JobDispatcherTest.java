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

import dev.gitnode.os.actions.entities.Runner;
import dev.gitnode.os.actions.entities.RunnerStatus;
import dev.gitnode.os.actions.entities.WorkflowJob;
import dev.gitnode.os.actions.entities.WorkflowJobStatus;
import dev.gitnode.os.actions.entities.WorkflowRun;
import dev.gitnode.os.actions.repositories.RunnerRepository;
import dev.gitnode.os.actions.repositories.WorkflowDefinitionRepository;
import dev.gitnode.os.actions.repositories.WorkflowJobRepository;
import dev.gitnode.os.actions.repositories.WorkflowRunRepository;
import dev.gitnode.os.actions.repositories.WorkflowStepRepository;
import dev.gitnode.os.actions.websocket.RunnerSession;
import dev.gitnode.os.actions.websocket.RunnerSessionRegistry;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
  @Mock private RepoRepository repoRepository;
  @Mock private SecretVaultService secretVaultService;
  @Mock private StringRedisTemplate redisTemplate;

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
            this.redisTemplate);
    ReflectionTestUtils.setField(this.dispatcher, "heartbeatTimeoutSeconds", 60);
  }

  @Test
  @DisplayName("dispatch does nothing when no queued jobs")
  void dispatch_doesNothing_whenNoQueuedJobs() {
    when(this.jobRepository.findQueuedForClaim(50)).thenReturn(List.of());

    this.dispatcher.dispatch();

    verify(this.runnerRepository, never()).findAllByStatus(any());
  }

  @Test
  @DisplayName("dispatch does nothing when no online runners")
  void dispatch_doesNothing_whenNoOnlineRunners() {
    when(this.jobRepository.findQueuedForClaim(50))
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

    when(this.jobRepository.findQueuedForClaim(50)).thenReturn(List.of(job));
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

    when(this.jobRepository.findQueuedForClaim(50)).thenReturn(List.of(job));
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

    when(this.jobRepository.findQueuedForClaim(50)).thenReturn(List.of(job));
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

    when(this.jobRepository.findQueuedForClaim(50)).thenReturn(List.of(job));
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

    final List<Object[]> emptyOwners = List.of();
    when(this.jobRepository.findQueuedForClaim(50)).thenReturn(List.of(job));
    when(this.runnerRepository.findAllByStatus(RunnerStatus.ONLINE))
        .thenReturn(List.of(buildRunner(List.of("self-hosted"), RunnerStatus.ONLINE, TENANT_ID)));
    when(this.runRepository.findAllById(Set.of(RUN_ID))).thenReturn(List.of(run));
    when(this.repoRepository.findOwnerIdsByRepoIds(Set.of(REPO_ID))).thenReturn(emptyOwners);
    when(this.runnerRepository.markStaleRunnersOffline(any())).thenReturn(0);

    this.dispatcher.dispatch();

    verify(this.redisTemplate, never()).convertAndSend(any(), (Object) any());
  }

  @Test
  @DisplayName("dispatch marks stale runners offline")
  void dispatch_marksStaleRunnersOffline() {
    when(this.jobRepository.findQueuedForClaim(50)).thenReturn(List.of());
    when(this.runnerRepository.markStaleRunnersOffline(any())).thenReturn(2);

    this.dispatcher.dispatch();

    verify(this.runnerRepository).markStaleRunnersOffline(any());
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private void stubRunToTenant(final UUID tenantId) {
    final var run = new WorkflowRun();
    run.setId(RUN_ID);
    run.setRepoId(REPO_ID);
    when(this.runRepository.findAllById(Set.of(RUN_ID))).thenReturn(List.of(run));
    final List<Object[]> ownerRow = new java.util.ArrayList<>();
    ownerRow.add(new Object[] {REPO_ID, tenantId});
    when(this.repoRepository.findOwnerIdsByRepoIds(Set.of(REPO_ID))).thenReturn(ownerRow);
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
    runner.setTokenHash("password");
    return runner;
  }
}
