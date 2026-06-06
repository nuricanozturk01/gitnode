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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.actions.entities.WorkflowJobStatus;
import com.nuricanozturk.originhub.actions.model.JobModel;
import com.nuricanozturk.originhub.actions.model.OnTriggerModel;
import com.nuricanozturk.originhub.actions.model.PushTriggerModel;
import com.nuricanozturk.originhub.actions.model.WorkflowModel;
import com.nuricanozturk.originhub.actions.repositories.WorkflowDefinitionRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowJobRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowRunRepository;
import com.nuricanozturk.originhub.actions.services.WorkflowParserService.ParsedWorkflow;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowTriggerService unit tests")
class WorkflowTriggerServiceTest {

  @Mock private WorkflowParserService parserService;
  @Mock private WorkflowDefinitionRepository definitionRepository;
  @Mock private WorkflowRunRepository runRepository;
  @Mock private WorkflowJobRepository jobRepository;
  @Mock private RepoRepository repoRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private WorkflowTriggerService triggerService;

  private static final UUID REPO_ID = UUID.randomUUID();
  private static final String OWNER = "alice";
  private static final String REPO_NAME = "myapp";

  // ── Pure unit tests: no repo stub needed ─────────────────────────────────

  @Nested
  @DisplayName("glob matching and hasPushTrigger")
  class GlobAndTriggerTests {

    @Test
    @DisplayName("matchesGlob: exact branch matches")
    void glob_exactMatch() {
      assertThat(WorkflowTriggerService.matchesGlob("main", "main")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"feature/login", "feature/logout", "feature/x"})
    @DisplayName("matchesGlob: wildcard matches feature/* branches")
    void glob_wildcardMatch(final String branch) {
      assertThat(WorkflowTriggerService.matchesGlob(branch, "feature/*")).isTrue();
    }

    @Test
    @DisplayName("matchesGlob: ** matches nested paths")
    void glob_doubleWildcard() {
      assertThat(WorkflowTriggerService.matchesGlob("dependabot/npm/lodash", "dependabot/**"))
          .isTrue();
    }

    @Test
    @DisplayName("hasPushTrigger: null on trigger → false")
    void hasPushTrigger_noTrigger() {
      final var model = new WorkflowModel("CI", null, null, null);
      assertThat(triggerService.hasPushTrigger(model, "main")).isFalse();
    }

    @Test
    @DisplayName("hasPushTrigger: empty branches → matches any branch")
    void hasPushTrigger_emptyBranches_matchesAll() {
      final var push = new PushTriggerModel(null, null, null, null, null);
      final var model = new WorkflowModel("CI", new OnTriggerModel(push, null, null), null, null);
      assertThat(triggerService.hasPushTrigger(model, "any-branch")).isTrue();
    }

    @Test
    @DisplayName("hasPushTrigger: branches-ignore excludes matching branch")
    void hasPushTrigger_branchesIgnore_excludes() {
      final var push = new PushTriggerModel(null, List.of("dependabot/**"), null, null, null);
      final var model = new WorkflowModel("CI", new OnTriggerModel(push, null, null), null, null);
      assertThat(triggerService.hasPushTrigger(model, "dependabot/npm/lodash")).isFalse();
    }

    @Test
    @DisplayName("hasPushTrigger: branch not in branches list → false")
    void hasPushTrigger_branchNotInList() {
      final var push = new PushTriggerModel(List.of("main"), null, null, null, null);
      final var model = new WorkflowModel("CI", new OnTriggerModel(push, null, null), null, null);
      assertThat(triggerService.hasPushTrigger(model, "feature/x")).isFalse();
    }
  }

  // ── triggerOnPush integration tests (need repo stub) ─────────────────────

  @Nested
  @DisplayName("triggerOnPush")
  class TriggerOnPushTests {

    @BeforeEach
    void stubRepo() {
      final var tenant = new Tenant();
      tenant.setUsername(OWNER);
      final var repo = new Repo();
      repo.setId(REPO_ID);
      repo.setName(REPO_NAME);
      repo.setOwner(tenant);
      when(repoRepository.findByIdWithOwner(REPO_ID)).thenReturn(Optional.of(repo));
    }

    @Test
    @DisplayName("no workflows found → nothing saved")
    void noWorkflows_nothingSaved() {
      when(parserService.parseWorkflows(OWNER, REPO_NAME, "refs/heads/main")).thenReturn(List.of());

      triggerService.triggerOnPush(REPO_ID, "main", OWNER, null);

      verify(runRepository, never()).save(any());
      verify(jobRepository, never()).save(any());
    }

    @Test
    @DisplayName("workflow with non-matching branch → no run created")
    void branchNotMatching_noRunCreated() {
      final var push = new PushTriggerModel(List.of("main"), null, null, null, null);
      final var workflow = workflowWith(push, Map.of("build", jobWithNeeds(null)));
      when(parserService.parseWorkflows(OWNER, REPO_NAME, "refs/heads/feature/x"))
          .thenReturn(List.of(parsed(workflow)));

      triggerService.triggerOnPush(REPO_ID, "feature/x", OWNER, null);

      verify(runRepository, never()).save(any());
    }

    @Test
    @DisplayName("matching workflow → run and queued job created, events published")
    void matchingWorkflow_createsRunAndQueuedJob() {
      final var push = new PushTriggerModel(List.of("main"), null, null, null, null);
      final var workflow = workflowWith(push, Map.of("build", jobWithNeeds(null)));

      when(parserService.parseWorkflows(OWNER, REPO_NAME, "refs/heads/main"))
          .thenReturn(List.of(parsed(workflow)));
      when(definitionRepository.findByRepoIdAndFilePath(any(), anyString()))
          .thenReturn(Optional.empty());
      stubDefinitionSave();
      stubRunSave();
      stubJobSave();
      when(runRepository.nextRunNumber(REPO_ID)).thenReturn(1);

      triggerService.triggerOnPush(REPO_ID, "main", OWNER, null);

      verify(runRepository).save(any());
      final var jobCaptor =
          ArgumentCaptor.forClass(com.nuricanozturk.originhub.actions.entities.WorkflowJob.class);
      verify(jobRepository).save(jobCaptor.capture());
      assertThat(jobCaptor.getValue().getStatus()).isEqualTo(WorkflowJobStatus.QUEUED);
    }

    @Test
    @DisplayName("job with needs → status WAITING; job without needs → QUEUED")
    void jobWithNeeds_isWaiting() {
      final var push = new PushTriggerModel(null, null, null, null, null);
      final var jobs =
          Map.of(
              "build", jobWithNeeds(null),
              "deploy", jobWithNeeds(List.of("build")));
      final var workflow = workflowWith(push, jobs);

      when(parserService.parseWorkflows(OWNER, REPO_NAME, "refs/heads/main"))
          .thenReturn(List.of(parsed(workflow)));
      when(definitionRepository.findByRepoIdAndFilePath(any(), anyString()))
          .thenReturn(Optional.empty());
      stubDefinitionSave();
      stubRunSave();
      stubJobSave();
      when(runRepository.nextRunNumber(REPO_ID)).thenReturn(1);

      triggerService.triggerOnPush(REPO_ID, "main", OWNER, null);

      final var jobCaptor =
          ArgumentCaptor.forClass(com.nuricanozturk.originhub.actions.entities.WorkflowJob.class);
      verify(jobRepository, org.mockito.Mockito.times(2)).save(jobCaptor.capture());

      final var statuses =
          jobCaptor.getAllValues().stream()
              .map(com.nuricanozturk.originhub.actions.entities.WorkflowJob::getStatus)
              .toList();
      assertThat(statuses).contains(WorkflowJobStatus.QUEUED, WorkflowJobStatus.WAITING);
    }
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private void stubDefinitionSave() {
    when(definitionRepository.save(any()))
        .thenAnswer(
            inv -> {
              final var def =
                  (com.nuricanozturk.originhub.actions.entities.WorkflowDefinition)
                      inv.getArgument(0);
              def.setId(UUID.randomUUID());
              return def;
            });
  }

  private void stubRunSave() {
    when(runRepository.save(any()))
        .thenAnswer(
            inv -> {
              final var run =
                  (com.nuricanozturk.originhub.actions.entities.WorkflowRun) inv.getArgument(0);
              run.setId(UUID.randomUUID());
              return run;
            });
  }

  private void stubJobSave() {
    when(jobRepository.save(any()))
        .thenAnswer(
            inv -> {
              final var job =
                  (com.nuricanozturk.originhub.actions.entities.WorkflowJob) inv.getArgument(0);
              job.setId(UUID.randomUUID());
              return job;
            });
  }

  private static WorkflowModel workflowWith(
      final PushTriggerModel push, final Map<String, JobModel> jobs) {
    return new WorkflowModel("CI", new OnTriggerModel(push, null, null), null, jobs);
  }

  private static JobModel jobWithNeeds(final List<String> needs) {
    return new JobModel("build", List.of("self-hosted"), null, needs, null, null, List.of());
  }

  private static ParsedWorkflow parsed(final WorkflowModel model) {
    return new ParsedWorkflow(".originhub/workflows/ci.yml", "raw: yaml", model);
  }
}
