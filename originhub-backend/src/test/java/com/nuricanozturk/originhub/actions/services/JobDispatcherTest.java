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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.actions.entities.Runner;
import com.nuricanozturk.originhub.actions.entities.RunnerStatus;
import com.nuricanozturk.originhub.actions.entities.WorkflowJob;
import com.nuricanozturk.originhub.actions.entities.WorkflowJobStatus;
import com.nuricanozturk.originhub.actions.repositories.RunnerRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowDefinitionRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowJobRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowRunRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowStepRepository;
import com.nuricanozturk.originhub.actions.websocket.RunnerSession;
import com.nuricanozturk.originhub.actions.websocket.RunnerSessionRegistry;
import com.nuricanozturk.originhub.actions.websocket.ServerMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

  private JobDispatcher dispatcher;

  private static final UUID RUNNER_ID = UUID.randomUUID();
  private static final UUID JOB_ID = UUID.randomUUID();
  private static final UUID RUN_ID = UUID.randomUUID();

  @Mock private com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository repoRepository;

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
            expressionEvaluator);
    ReflectionTestUtils.setField(this.dispatcher, "heartbeatTimeoutSeconds", 60);
  }

  @Test
  @DisplayName("dispatch does nothing when no queued jobs")
  void dispatch_doesNothing_whenNoQueuedJobs() {
    when(this.jobRepository.findAllQueued()).thenReturn(List.of());

    this.dispatcher.dispatch();

    verify(this.runnerRepository, never()).findAllByStatus(any());
  }

  @Test
  @DisplayName("dispatch does nothing when no online runners")
  void dispatch_doesNothing_whenNoOnlineRunners() {
    when(this.jobRepository.findAllQueued()).thenReturn(List.of(buildJob(List.of("self-hosted"))));
    when(this.runnerRepository.findAllByStatus(RunnerStatus.ONLINE)).thenReturn(List.of());
    when(this.runnerRepository.markStaleRunnersOffline(any())).thenReturn(0);

    this.dispatcher.dispatch();

    verify(this.sessionRegistry, never()).get(any());
  }

  @Test
  @DisplayName("dispatch skips runner whose session is not connected")
  void dispatch_skipsRunner_whenNotConnected() {
    final var job = buildJob(List.of("self-hosted"));
    final var runner = buildRunner(List.of("self-hosted"), RunnerStatus.ONLINE);

    when(this.jobRepository.findAllQueued()).thenReturn(List.of(job));
    when(this.runnerRepository.findAllByStatus(RunnerStatus.ONLINE)).thenReturn(List.of(runner));
    when(this.sessionRegistry.isConnected(runner.getId())).thenReturn(false);
    when(this.runnerRepository.markStaleRunnersOffline(any())).thenReturn(0);

    this.dispatcher.dispatch();

    verify(this.sessionRegistry, never()).get(any());
  }

  @Test
  @DisplayName("dispatch skips job when runner labels do not match")
  void dispatch_skipsJob_whenLabelMismatch() {
    final var job = buildJob(List.of("self-hosted", "docker"));
    final var runner = buildRunner(List.of("self-hosted"), RunnerStatus.ONLINE);

    when(this.jobRepository.findAllQueued()).thenReturn(List.of(job));
    when(this.runnerRepository.findAllByStatus(RunnerStatus.ONLINE)).thenReturn(List.of(runner));
    when(this.runnerRepository.markStaleRunnersOffline(any())).thenReturn(0);

    this.dispatcher.dispatch();

    verify(this.sessionRegistry, never()).get(any());
  }

  @Test
  @DisplayName("dispatch sends JOB_ASSIGNED and marks runner BUSY when labels match")
  void dispatch_sendsJobAssigned_whenLabelsMatch() {
    final var job = buildJob(List.of("self-hosted"));
    final var runner = buildRunner(List.of("self-hosted", "linux"), RunnerStatus.ONLINE);

    when(this.jobRepository.findAllQueued()).thenReturn(List.of(job));
    when(this.runnerRepository.findAllByStatus(RunnerStatus.ONLINE)).thenReturn(List.of(runner));
    when(this.sessionRegistry.isConnected(runner.getId())).thenReturn(true);
    when(this.sessionRegistry.get(runner.getId())).thenReturn(Optional.of(this.runnerSession));
    when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.empty());
    when(this.runnerRepository.markStaleRunnersOffline(any())).thenReturn(0);

    this.dispatcher.dispatch();

    final var captor = ArgumentCaptor.forClass(ServerMessage.class);
    verify(this.runnerSession).send(captor.capture());
    assertThat(captor.getValue().type()).isEqualTo("JOB_ASSIGNED");

    verify(this.runnerRepository).save(runner);
    assertThat(runner.getStatus()).isEqualTo(RunnerStatus.BUSY);
  }

  @Test
  @DisplayName("dispatch marks stale runners offline")
  void dispatch_marksStaleRunnersOffline() {
    when(this.jobRepository.findAllQueued()).thenReturn(List.of());
    when(this.runnerRepository.markStaleRunnersOffline(any())).thenReturn(2);

    this.dispatcher.dispatch();

    verify(this.runnerRepository).markStaleRunnersOffline(any());
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static WorkflowJob buildJob(final List<String> requiredLabels) {
    final var job = new WorkflowJob();
    job.setId(JOB_ID);
    job.setRunId(RUN_ID);
    job.setName("build");
    job.setRunnerLabels(requiredLabels);
    job.setStatus(WorkflowJobStatus.QUEUED);
    return job;
  }

  private static Runner buildRunner(final List<String> labels, final RunnerStatus status) {
    final var runner = new Runner();
    runner.setId(RUNNER_ID);
    runner.setName("test-runner");
    runner.setLabels(labels);
    runner.setStatus(status);
    runner.setLastHeartbeat(Instant.now());
    runner.setTokenHash("hash");
    return runner;
  }
}
