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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.actions.entities.WorkflowJob;
import dev.gitnode.os.actions.entities.WorkflowRun;
import dev.gitnode.os.actions.model.WorkflowRunTriggerModel;
import dev.gitnode.os.actions.repositories.WorkflowDefinitionRepository;
import dev.gitnode.os.actions.repositories.WorkflowJobRepository;
import dev.gitnode.os.actions.repositories.WorkflowRunRepository;
import dev.gitnode.os.actions.services.WorkflowParserService.ParsedWorkflow;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import java.util.List;
import java.util.Map;
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
@DisplayName("on: workflow_run")
class WorkflowRunTriggerTest {

  private static final String DEPLOY =
      """
      name: Deploy
      on:
        workflow_run:
          workflows: [CI]
          types: [completed]
          branches: [main]
      jobs:
        ship:
          runs-on: [self-hosted]
          steps:
            - run: echo ${{ github.event.workflow_run.conclusion }}
      """;

  private static final UUID REPO_ID = UUID.randomUUID();

  @Mock private WorkflowParserService parserService;
  @Mock private WorkflowDefinitionRepository definitionRepository;
  @Mock private WorkflowRunRepository runRepository;
  @Mock private WorkflowJobRepository jobRepository;
  @Mock private dev.gitnode.os.actions.repositories.WorkflowStepRepository stepRepository;
  @Mock private dev.gitnode.os.actions.repositories.WorkflowLogRepository logRepository;
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
    lenient()
        .when(this.runRepository.save(any(WorkflowRun.class)))
        .thenAnswer(i -> i.getArgument(0));
    lenient()
        .when(this.jobRepository.save(any(WorkflowJob.class)))
        .thenAnswer(i -> i.getArgument(0));
    lenient()
        .when(this.definitionRepository.findByRepoIdAndFilePath(any(), anyString()))
        .thenReturn(Optional.empty());
    lenient()
        .when(this.definitionRepository.save(any()))
        .thenAnswer(
            i -> {
              final var def = (dev.gitnode.os.actions.entities.WorkflowDefinition) i.getArgument(0);
              def.setId(UUID.randomUUID());
              return def;
            });
  }

  private static WorkflowRun completedRun(
      final String name, final String ref, final String conclusion) {
    final var run = new WorkflowRun();
    run.setId(UUID.randomUUID());
    run.setRepoId(REPO_ID);
    run.setWorkflowName(name);
    run.setRunNumber(7);
    run.setTriggerEvent("push");
    run.setTriggerRef(ref);
    run.setTriggerSha("abc123");
    run.setConclusion(conclusion);
    return run;
  }

  private void deployWorkflowOnDefaultBranch() {
    final var model = new WorkflowParserService().parseFromContent(DEPLOY);
    when(this.parserService.parseWorkflows("alice", "app", "refs/heads/main"))
        .thenReturn(List.of(new ParsedWorkflow(".gitnode/workflows/deploy.yml", DEPLOY, model)));
  }

  @Test
  @DisplayName("the trigger parses: workflows, types and branches")
  void parsesTheTrigger() {
    final var on = new WorkflowParserService().parseFromContent(DEPLOY).on();

    assertThat(on.workflowRun().workflows()).containsExactly("CI");
    assertThat(on.workflowRun().types()).containsExactly("completed");
    assertThat(on.workflowRun().branches()).containsExactly("main");
    assertThat(on.push()).isNull();
  }

  @Test
  @DisplayName("filters: workflow name (globs allowed), type and branch")
  void filters() {
    final var trigger =
        new WorkflowRunTriggerModel(
            List.of("CI", "Build *"), List.of("completed"), List.of("main"), null);

    assertThat(
            WorkflowTriggerService.workflowRunMatches(
                trigger, completedRun("CI", "refs/heads/main", "success")))
        .isTrue();
    assertThat(
            WorkflowTriggerService.workflowRunMatches(
                trigger, completedRun("Build linux", "refs/heads/main", "failure")))
        .isTrue();
    assertThat(
            WorkflowTriggerService.workflowRunMatches(
                trigger, completedRun("Lint", "refs/heads/main", "success")))
        .isFalse();
    assertThat(
            WorkflowTriggerService.workflowRunMatches(
                trigger, completedRun("CI", "refs/heads/dev", "success")))
        .isFalse();

    final var ignoring = new WorkflowRunTriggerModel(List.of("CI"), null, null, List.of("wip/**"));
    assertThat(
            WorkflowTriggerService.workflowRunMatches(
                ignoring, completedRun("CI", "refs/heads/wip/x", "success")))
        .isFalse();
    assertThat(
            WorkflowTriggerService.workflowRunMatches(
                ignoring, completedRun("CI", "refs/heads/main", "success")))
        .isTrue();

    final var requestedOnly =
        new WorkflowRunTriggerModel(List.of("CI"), List.of("requested"), null, null);
    assertThat(
            WorkflowTriggerService.workflowRunMatches(
                requestedOnly, completedRun("CI", "refs/heads/main", "success")))
        .isFalse();
  }

  @Test
  @DisplayName("a finished run starts the workflow that listens for it, with the run in the event")
  void startsTheListeningWorkflow() {
    final var completed = completedRun("CI", "refs/heads/main", "failure");
    when(this.runRepository.findById(completed.getId())).thenReturn(Optional.of(completed));
    when(this.runRepository.nextRunNumber(REPO_ID)).thenReturn(8);
    this.deployWorkflowOnDefaultBranch();

    this.service.triggerOnWorkflowRunCompleted(REPO_ID, completed.getId());

    final var saved = ArgumentCaptor.forClass(WorkflowRun.class);
    verify(this.runRepository).save(saved.capture());
    final var run = saved.getValue();
    assertThat(run.getTriggerEvent()).isEqualTo("workflow_run");
    assertThat(run.getTriggerRef()).isEqualTo("refs/heads/main");
    assertThat(run.getWorkflowName()).isEqualTo("Deploy");
    final var info = (Map<?, ?>) run.getEventPayload().get("workflow_run");
    assertThat(info.get("conclusion")).isEqualTo("failure");
    assertThat(info.get("name")).isEqualTo("CI");
    assertThat(info.get("head_branch")).isEqualTo("main");
    assertThat(run.getEventPayload().get("action")).isEqualTo("completed");
  }

  @Test
  @DisplayName("a run nobody listens for starts nothing")
  void unrelatedRunStartsNothing() {
    final var completed = completedRun("Lint", "refs/heads/main", "success");
    when(this.runRepository.findById(completed.getId())).thenReturn(Optional.of(completed));
    this.deployWorkflowOnDefaultBranch();

    this.service.triggerOnWorkflowRunCompleted(REPO_ID, completed.getId());

    verify(this.runRepository, never()).save(any());
  }

  @Test
  @DisplayName("chains of workflow_run workflows stop after three levels")
  void chainsAreCapped() {
    final var completed = completedRun("CI", "refs/heads/main", "success");
    completed.setEventPayload(
        new java.util.HashMap<>(
            Map.of(
                "workflow_run",
                Map.of("chain_depth", WorkflowTriggerService.MAX_WORKFLOW_RUN_DEPTH))));
    when(this.runRepository.findById(completed.getId())).thenReturn(Optional.of(completed));

    this.service.triggerOnWorkflowRunCompleted(REPO_ID, completed.getId());

    verify(this.runRepository, never()).save(any());
    verify(this.parserService, never()).parseWorkflows(anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("an unknown run or repository is ignored")
  void unknownRunIsIgnored() {
    final var id = UUID.randomUUID();
    when(this.runRepository.findById(id)).thenReturn(Optional.empty());

    this.service.triggerOnWorkflowRunCompleted(REPO_ID, id);

    verify(this.runRepository, never()).save(any());
  }
}
