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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.actions.entities.WorkflowDefinition;
import dev.gitnode.os.actions.entities.WorkflowJob;
import dev.gitnode.os.actions.entities.WorkflowJobStatus;
import dev.gitnode.os.actions.entities.WorkflowLog;
import dev.gitnode.os.actions.entities.WorkflowRun;
import dev.gitnode.os.actions.entities.WorkflowRunStatus;
import dev.gitnode.os.actions.entities.WorkflowStep;
import dev.gitnode.os.actions.repositories.WorkflowDefinitionRepository;
import dev.gitnode.os.actions.repositories.WorkflowJobRepository;
import dev.gitnode.os.actions.repositories.WorkflowLogRepository;
import dev.gitnode.os.actions.repositories.WorkflowRunRepository;
import dev.gitnode.os.actions.repositories.WorkflowStepRepository;
import dev.gitnode.os.actions.services.WorkflowParserService.ParsedWorkflow;
import dev.gitnode.os.events.actions.WorkflowRunCompletedEvent;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("A job that calls a reusable workflow fails visibly instead of passing silently")
class ReusableWorkflowGuardTest {

  private static final UUID REPO_ID = UUID.randomUUID();

  private static final String CALLER =
      """
      name: caller
      on: workflow_dispatch
      jobs:
        call:
          uses: ./.gitnode/workflows/shared.yml
          with:
            who: world
        after:
          needs: call
          runs-on: [self-hosted]
          steps:
            - run: echo must not run
      """;

  private static final String MIXED =
      """
      name: mixed
      on: workflow_dispatch
      jobs:
        call:
          uses: ./.gitnode/workflows/shared.yml
        real:
          runs-on: [self-hosted]
          steps:
            - run: echo fine
      """;

  @Mock private WorkflowParserService parserService;
  @Mock private WorkflowDefinitionRepository definitionRepository;
  @Mock private WorkflowRunRepository runRepository;
  @Mock private WorkflowJobRepository jobRepository;
  @Mock private WorkflowStepRepository stepRepository;
  @Mock private WorkflowLogRepository logRepository;
  @Mock private RepoLookupPort repoLookup;
  @Mock private ApplicationEventPublisher eventPublisher;

  private final ExpressionEvaluator evaluator = new ExpressionEvaluator();
  private WorkflowTriggerService service;

  @BeforeEach
  void setUp() {
    this.service =
        new WorkflowTriggerService(
            this.parserService,
            this.definitionRepository,
            this.runRepository,
            this.jobRepository,
            this.repoLookup,
            this.eventPublisher,
            new MatrixExpander(),
            this.evaluator,
            new JobAdvancer(
                this.definitionRepository, this.parserService, this.evaluator, this.repoLookup),
            this.stepRepository,
            this.logRepository);
    lenient()
        .when(this.repoLookup.findById(REPO_ID))
        .thenReturn(
            Optional.of(new RepoRef(REPO_ID, UUID.randomUUID(), "alice", "app", false, "main")));
    lenient().when(this.runRepository.nextRunNumber(REPO_ID)).thenReturn(1);
    lenient()
        .when(this.runRepository.save(any(WorkflowRun.class)))
        .thenAnswer(i -> i.getArgument(0));
    lenient()
        .when(this.jobRepository.save(any(WorkflowJob.class)))
        .thenAnswer(
            i -> {
              final var job = (WorkflowJob) i.getArgument(0);
              if (job.getId() == null) {
                job.setId(UUID.randomUUID());
              }
              return job;
            });
    lenient()
        .when(this.stepRepository.save(any(WorkflowStep.class)))
        .thenAnswer(
            i -> {
              final var step = (WorkflowStep) i.getArgument(0);
              step.setId(UUID.randomUUID());
              return step;
            });
    lenient()
        .when(this.definitionRepository.findByRepoIdAndFilePath(any(), anyString()))
        .thenReturn(Optional.empty());
    lenient()
        .when(this.definitionRepository.save(any(WorkflowDefinition.class)))
        .thenAnswer(
            i -> {
              final var def = (WorkflowDefinition) i.getArgument(0);
              def.setId(UUID.randomUUID());
              return def;
            });
  }

  private void dispatch(final String yaml) {
    final var model = new WorkflowParserService().parseFromContent(yaml);
    when(this.parserService.parseWorkflows("alice", "app", "refs/heads/main"))
        .thenReturn(List.of(new ParsedWorkflow(".gitnode/workflows/w.yml", yaml, model)));
    this.service.triggerManual(REPO_ID, "w.yml", "refs/heads/main", null, null);
  }

  @Test
  @DisplayName("the call job fails, explains why in its log, and the jobs that need it are skipped")
  void callJobFailsWithAReason() {
    this.dispatch(CALLER);

    final var jobs = ArgumentCaptor.forClass(WorkflowJob.class);
    verify(this.jobRepository, org.mockito.Mockito.atLeastOnce()).save(jobs.capture());
    final var call =
        jobs.getAllValues().stream()
            .filter(j -> "call".equals(j.getJobKey()))
            .findFirst()
            .orElseThrow();
    assertThat(call.getStatus()).isEqualTo(WorkflowJobStatus.FAILURE);
    assertThat(call.getConclusion()).isEqualTo("failure");

    final var after =
        jobs.getAllValues().stream()
            .filter(j -> "after".equals(j.getJobKey()))
            .reduce((a, b) -> b)
            .orElseThrow();
    assertThat(after.getStatus()).isEqualTo(WorkflowJobStatus.SKIPPED);

    final var log = ArgumentCaptor.forClass(WorkflowLog.class);
    verify(this.logRepository).save(log.capture());
    assertThat(log.getValue().getLevel()).isEqualTo("error");
    assertThat(log.getValue().getContent())
        .contains("##[error]")
        .contains("Reusable workflows are not supported yet")
        .contains("./.gitnode/workflows/shared.yml");
  }

  @Test
  @DisplayName("a run whose jobs all end at once is closed as failed instead of staying queued")
  void runWithOnlyUnsupportedJobsIsClosed() {
    this.dispatch(CALLER);

    final var run = ArgumentCaptor.forClass(WorkflowRun.class);
    verify(this.runRepository, org.mockito.Mockito.atLeastOnce()).save(run.capture());
    final var last = run.getAllValues().getLast();
    assertThat(last.getStatus()).isEqualTo(WorkflowRunStatus.FAILURE);
    assertThat(last.getConclusion()).isEqualTo("failure");
    assertThat(last.getCompletedAt()).isNotNull();
    verify(this.eventPublisher)
        .publishEvent(org.mockito.ArgumentMatchers.any(WorkflowRunCompletedEvent.class));
  }

  @Test
  @DisplayName("other jobs of the same workflow still run, and the run stays open for them")
  void otherJobsStillRun() {
    this.dispatch(MIXED);

    final var jobs = ArgumentCaptor.forClass(WorkflowJob.class);
    verify(this.jobRepository, org.mockito.Mockito.atLeastOnce()).save(jobs.capture());
    final var real =
        jobs.getAllValues().stream()
            .filter(j -> "real".equals(j.getJobKey()))
            .findFirst()
            .orElseThrow();
    assertThat(real.getStatus()).isEqualTo(WorkflowJobStatus.QUEUED);
    verify(this.eventPublisher, org.mockito.Mockito.never())
        .publishEvent(org.mockito.ArgumentMatchers.any(WorkflowRunCompletedEvent.class));
  }
}
