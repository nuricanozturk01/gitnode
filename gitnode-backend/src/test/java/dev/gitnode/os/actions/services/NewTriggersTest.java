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

import dev.gitnode.os.actions.entities.WorkflowDefinition;
import dev.gitnode.os.actions.entities.WorkflowJob;
import dev.gitnode.os.actions.entities.WorkflowRun;
import dev.gitnode.os.actions.repositories.WorkflowDefinitionRepository;
import dev.gitnode.os.actions.repositories.WorkflowJobRepository;
import dev.gitnode.os.actions.repositories.WorkflowLogRepository;
import dev.gitnode.os.actions.repositories.WorkflowRunRepository;
import dev.gitnode.os.actions.repositories.WorkflowStepRepository;
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

/** paths / paths-ignore, pull_request types and the create, delete and release triggers. */
@ExtendWith(MockitoExtension.class)
@DisplayName("paths, pull_request types, create, delete and release triggers")
class NewTriggersTest {

  private static final UUID REPO_ID = UUID.randomUUID();
  private static final String SHA = "a".repeat(40);
  private static final String BEFORE = "b".repeat(40);

  private static final String PUSH_PATHS =
      """
      name: Docs
      on:
        push:
          paths:
            - 'docs/**'
      jobs:
        j:
          runs-on: [self-hosted]
          steps:
            - run: echo hi
      """;

  private static final String PUSH_IGNORE =
      """
      name: Code
      on:
        push:
          paths-ignore:
            - 'docs/**'
            - '*.md'
      jobs:
        j:
          runs-on: [self-hosted]
          steps:
            - run: echo hi
      """;

  private static final String PR_DEFAULT =
      """
      name: PR
      on:
        pull_request:
      jobs:
        j:
          runs-on: [self-hosted]
          steps:
            - run: echo hi
      """;

  private static final String PR_TYPES =
      """
      name: PR
      on:
        pull_request:
          types: [closed]
          paths: ['src/**']
      jobs:
        j:
          runs-on: [self-hosted]
          steps:
            - run: echo hi
      """;

  private static final String CREATE_DELETE =
      """
      name: Refs
      on: [create, delete]
      jobs:
        j:
          runs-on: [self-hosted]
          steps:
            - run: echo hi
      """;

  private static final String RELEASE_ALL =
      """
      name: Rel
      on:
        release:
      jobs:
        j:
          runs-on: [self-hosted]
          steps:
            - run: echo hi
      """;

  private static final String RELEASE_PUBLISHED =
      """
      name: Rel
      on:
        release:
          types: [published]
      jobs:
        j:
          runs-on: [self-hosted]
          steps:
            - run: echo hi
      """;

  @Mock private WorkflowParserService parserService;
  @Mock private WorkflowDefinitionRepository definitionRepository;
  @Mock private WorkflowRunRepository runRepository;
  @Mock private WorkflowJobRepository jobRepository;
  @Mock private WorkflowStepRepository stepRepository;
  @Mock private WorkflowLogRepository logRepository;
  @Mock private JobCancellation jobCancellation;
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
            this.logRepository,
            this.jobCancellation);
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
              final var def = (WorkflowDefinition) i.getArgument(0);
              def.setId(UUID.randomUUID());
              return def;
            });
    lenient().when(this.runRepository.nextRunNumber(REPO_ID)).thenReturn(1);
  }

  private static ParsedWorkflow parsed(final String yaml) {
    return new ParsedWorkflow(
        ".gitnode/workflows/x.yml", yaml, new WorkflowParserService().parseFromContent(yaml));
  }

  private void workflowsAt(final String rev, final String yaml) {
    when(this.parserService.parseWorkflows("alice", "app", rev)).thenReturn(List.of(parsed(yaml)));
  }

  private WorkflowRun onlyRun() {
    final var saved = ArgumentCaptor.forClass(WorkflowRun.class);
    verify(this.runRepository).save(saved.capture());
    return saved.getValue();
  }

  // ── parsing ───────────────────────────────────────────────────────────────

  @Test
  @DisplayName("the new triggers and path filters parse in every on: form")
  void parsing() {
    final var on = parsed(CREATE_DELETE).model().on();
    assertThat(on.create()).isNotNull();
    assertThat(on.deleted()).isNotNull();
    assertThat(on.release()).isNull();
    assertThat(on.push()).isNull();

    assertThat(parsed(RELEASE_ALL).model().on().release().types()).isNull();
    assertThat(parsed(RELEASE_PUBLISHED).model().on().release().types())
        .containsExactly("published");

    final var pr = parsed(PR_TYPES).model().on().pullRequest();
    assertThat(pr.types()).containsExactly("closed");
    assertThat(pr.paths()).containsExactly("src/**");
    assertThat(pr.pathsIgnore()).isNull();

    assertThat(parsed(PUSH_IGNORE).model().on().push().pathsIgnore())
        .containsExactly("docs/**", "*.md");
  }

  // ── push paths ────────────────────────────────────────────────────────────

  @Test
  @DisplayName("push paths: runs when a changed file matches")
  void pushPathsMatch() {
    this.workflowsAt(SHA, PUSH_PATHS);
    when(this.parserService.changedFiles("alice", "app", BEFORE, SHA))
        .thenReturn(List.of("docs/a.md", "src/A.java"));

    this.service.triggerOnPush(REPO_ID, "main", "alice", null, SHA, BEFORE);

    final var run = this.onlyRun();
    assertThat(run.getTriggerEvent()).isEqualTo("push");
    assertThat(run.getEventPayload().get("before")).isEqualTo(BEFORE);
    assertThat(run.getEventPayload().get("after")).isEqualTo(SHA);
  }

  @Test
  @DisplayName("push paths: does not run when no changed file matches")
  void pushPathsNoMatch() {
    this.workflowsAt(SHA, PUSH_PATHS);
    when(this.parserService.changedFiles("alice", "app", BEFORE, SHA))
        .thenReturn(List.of("src/A.java"));

    this.service.triggerOnPush(REPO_ID, "main", "alice", null, SHA, BEFORE);

    verify(this.runRepository, never()).save(any());
  }

  @Test
  @DisplayName("push paths-ignore: skipped only when every file is ignored")
  void pushPathsIgnore() {
    this.workflowsAt(SHA, PUSH_IGNORE);
    when(this.parserService.changedFiles("alice", "app", BEFORE, SHA))
        .thenReturn(List.of("docs/a.txt", "README.md"));

    this.service.triggerOnPush(REPO_ID, "main", "alice", null, SHA, BEFORE);

    verify(this.runRepository, never()).save(any());
  }

  @Test
  @DisplayName("push paths: when the files cannot be determined the workflow still runs")
  void pushPathsFailOpen() {
    this.workflowsAt(SHA, PUSH_PATHS);
    when(this.parserService.changedFiles("alice", "app", BEFORE, SHA)).thenReturn(null);

    this.service.triggerOnPush(REPO_ID, "main", "alice", null, SHA, BEFORE);

    verify(this.runRepository).save(any(WorkflowRun.class));
  }

  @Test
  @DisplayName("a workflow without path filters never asks for the changed files")
  void noFilterNoDiff() {
    this.workflowsAt(
        SHA,
        "name: P\non: push\njobs:\n  j:\n    runs-on: [self-hosted]\n    steps:\n      - run: x\n");

    this.service.triggerOnPush(REPO_ID, "main", "alice", null, SHA, BEFORE);

    verify(this.parserService, never()).changedFiles(anyString(), anyString(), any(), anyString());
    verify(this.runRepository).save(any(WorkflowRun.class));
  }

  // ── pull_request types and paths ──────────────────────────────────────────

  @Test
  @DisplayName("pull_request without types: opened, synchronize and reopened run, closed does not")
  void pullRequestDefaultTypes() {
    final var model = parsed(PR_DEFAULT).model();

    assertThat(this.service.hasPullRequestTrigger(model, "main", "opened")).isTrue();
    assertThat(this.service.hasPullRequestTrigger(model, "main", "synchronize")).isTrue();
    assertThat(this.service.hasPullRequestTrigger(model, "main", "reopened")).isTrue();
    assertThat(this.service.hasPullRequestTrigger(model, "main", "closed")).isFalse();
  }

  @Test
  @DisplayName("pull_request with types only reacts to those types")
  void pullRequestExplicitTypes() {
    final var model = parsed(PR_TYPES).model();

    assertThat(this.service.hasPullRequestTrigger(model, "main", "closed")).isTrue();
    assertThat(this.service.hasPullRequestTrigger(model, "main", "synchronize")).isFalse();
  }

  @Test
  @DisplayName(
      "a closed pull request starts a workflow that asks for closed, with the action in the event")
  void pullRequestClosedRuns() {
    this.workflowsAt(SHA, PR_TYPES);
    when(this.parserService.resolveSha("alice", "app", "refs/heads/main"))
        .thenReturn("c".repeat(40));
    when(this.parserService.changedFilesOfPullRequest("alice", "app", "main", SHA))
        .thenReturn(List.of("src/A.java"));

    this.service.triggerOnPullRequest(REPO_ID, "feature", "main", SHA, null, "closed");

    final var run = this.onlyRun();
    assertThat(run.getTriggerEvent()).isEqualTo("pull_request");
    assertThat(run.getEventPayload().get("action")).isEqualTo("closed");
  }

  @Test
  @DisplayName("pull_request paths: no matching file means no run")
  void pullRequestPathsNoMatch() {
    this.workflowsAt(SHA, PR_TYPES);
    when(this.parserService.resolveSha("alice", "app", "refs/heads/main"))
        .thenReturn("c".repeat(40));
    when(this.parserService.changedFilesOfPullRequest("alice", "app", "main", SHA))
        .thenReturn(List.of("docs/a.md"));

    this.service.triggerOnPullRequest(REPO_ID, "feature", "main", SHA, null, "closed");

    verify(this.runRepository, never()).save(any());
  }

  @Test
  @DisplayName("a merged pull request (run at the target tip) uses the merge commit's own changes")
  void mergedPullRequestUsesMergeCommit() {
    this.workflowsAt(SHA, PR_TYPES);
    when(this.parserService.resolveSha("alice", "app", "refs/heads/main")).thenReturn(SHA);
    when(this.parserService.changedFiles("alice", "app", null, SHA))
        .thenReturn(List.of("src/A.java"));

    this.service.triggerOnPullRequest(REPO_ID, "feature", "main", SHA, null, "closed");

    verify(this.runRepository).save(any(WorkflowRun.class));
    verify(this.parserService, never())
        .changedFilesOfPullRequest(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("the 5-argument call keeps working and means opened")
  void pullRequestLegacyCall() {
    this.workflowsAt(SHA, PR_DEFAULT);

    this.service.triggerOnPullRequest(REPO_ID, "feature", "main", SHA, null);

    assertThat(this.onlyRun().getEventPayload().get("action")).isEqualTo("opened");
  }

  // ── create / delete ───────────────────────────────────────────────────────

  @Test
  @DisplayName("create: a new branch runs on: create at the new commit, with ref and ref_type")
  void branchCreated() {
    this.workflowsAt(SHA, CREATE_DELETE);

    this.service.triggerOnRefCreated(REPO_ID, "branch", "feature/x", SHA, null);

    final var run = this.onlyRun();
    assertThat(run.getTriggerEvent()).isEqualTo("create");
    assertThat(run.getTriggerRef()).isEqualTo("refs/heads/feature/x");
    assertThat(run.getTriggerSha()).isEqualTo(SHA);
    assertThat(run.getEventPayload().get("ref")).isEqualTo("feature/x");
    assertThat(run.getEventPayload().get("ref_type")).isEqualTo("branch");
    assertThat(run.getEventPayload().get("master_branch")).isEqualTo("main");
  }

  @Test
  @DisplayName("create: a new tag without a known sha is resolved from its ref")
  void tagCreatedResolvesSha() {
    when(this.parserService.resolveSha("alice", "app", "refs/tags/v1")).thenReturn(SHA);
    this.workflowsAt(SHA, CREATE_DELETE);

    this.service.triggerOnRefCreated(REPO_ID, "tag", "v1", null, null);

    final var run = this.onlyRun();
    assertThat(run.getTriggerRef()).isEqualTo("refs/tags/v1");
    assertThat(run.getEventPayload().get("ref_type")).isEqualTo("tag");
  }

  @Test
  @DisplayName("create: a ref that is already gone starts nothing")
  void createdRefAlreadyGone() {
    when(this.parserService.resolveSha("alice", "app", "refs/heads/x")).thenReturn(null);

    this.service.triggerOnRefCreated(REPO_ID, "branch", "x", null, null);

    verify(this.runRepository, never()).save(any());
  }

  @Test
  @DisplayName("create: workflows without on: create are left alone")
  void createNeedsTheTrigger() {
    this.workflowsAt(SHA, PUSH_PATHS);

    this.service.triggerOnRefCreated(REPO_ID, "branch", "x", SHA, null);

    verify(this.runRepository, never()).save(any());
  }

  @Test
  @DisplayName("delete: runs on: delete from the default branch, naming the deleted ref")
  void refDeleted() {
    when(this.parserService.resolveSha("alice", "app", "refs/heads/main")).thenReturn(SHA);
    this.workflowsAt(SHA, CREATE_DELETE);

    this.service.triggerOnRefDeleted(REPO_ID, "branch", "old", null);

    final var run = this.onlyRun();
    assertThat(run.getTriggerEvent()).isEqualTo("delete");
    assertThat(run.getTriggerRef()).isEqualTo("refs/heads/old");
    assertThat(run.getTriggerSha()).isEqualTo(SHA);
    assertThat(run.getEventPayload().get("ref")).isEqualTo("old");
    assertThat(run.getEventPayload().get("ref_type")).isEqualTo("branch");
  }

  // ── release ───────────────────────────────────────────────────────────────

  @Test
  @DisplayName("release without types runs for a published release at its tag, once")
  void releaseAllTypes() {
    when(this.parserService.resolveSha("alice", "app", "refs/tags/v1")).thenReturn(SHA);
    this.workflowsAt(SHA, RELEASE_ALL);

    this.service.triggerOnRelease(
        REPO_ID, "v1", "First", false, false, List.of("created", "published", "released"), null);

    final var run = this.onlyRun();
    assertThat(run.getTriggerEvent()).isEqualTo("release");
    assertThat(run.getTriggerRef()).isEqualTo("refs/tags/v1");
    assertThat(run.getEventPayload().get("action")).isEqualTo("created");
    final var release = (Map<?, ?>) run.getEventPayload().get("release");
    assertThat(release.get("tag_name")).isEqualTo("v1");
    assertThat(release.get("name")).isEqualTo("First");
    assertThat(release.get("prerelease")).isEqualTo(false);
  }

  @Test
  @DisplayName("release types filter which activity starts the workflow")
  void releaseTypesFilter() {
    when(this.parserService.resolveSha("alice", "app", "refs/tags/v1")).thenReturn(SHA);
    this.workflowsAt(SHA, RELEASE_PUBLISHED);

    this.service.triggerOnRelease(REPO_ID, "v1", null, false, true, List.of("created"), null);
    verify(this.runRepository, never()).save(any());

    this.service.triggerOnRelease(
        REPO_ID, "v1", null, false, false, List.of("created", "published", "released"), null);
    final var run = this.onlyRun();
    assertThat(run.getEventPayload().get("action")).isEqualTo("published");
  }

  @Test
  @DisplayName("release: a tag that is gone falls back to the default branch")
  void releaseFallsBackToDefaultBranch() {
    when(this.parserService.resolveSha("alice", "app", "refs/tags/v1")).thenReturn(null);
    when(this.parserService.resolveSha("alice", "app", "refs/heads/main")).thenReturn(SHA);
    this.workflowsAt(SHA, RELEASE_ALL);

    this.service.triggerOnRelease(REPO_ID, "v1", null, false, false, List.of("created"), null);

    verify(this.runRepository).save(any(WorkflowRun.class));
  }

  @Test
  @DisplayName("the first release type that matches is reported")
  void firstMatchingReleaseType() {
    assertThat(
            WorkflowTriggerService.firstMatchingReleaseType(
                List.of("released", "published"), List.of("created", "published", "released")))
        .isEqualTo("published");
    assertThat(
            WorkflowTriggerService.firstMatchingReleaseType(
                List.of("prereleased"), List.of("created", "published", "released")))
        .isNull();
    assertThat(
            WorkflowTriggerService.firstMatchingReleaseType(null, List.of("created", "published")))
        .isEqualTo("created");
  }
}
