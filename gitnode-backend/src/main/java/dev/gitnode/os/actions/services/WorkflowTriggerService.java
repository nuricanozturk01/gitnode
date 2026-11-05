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

import dev.gitnode.os.actions.entities.WorkflowDefinition;
import dev.gitnode.os.actions.entities.WorkflowJob;
import dev.gitnode.os.actions.entities.WorkflowJobStatus;
import dev.gitnode.os.actions.entities.WorkflowLog;
import dev.gitnode.os.actions.entities.WorkflowRun;
import dev.gitnode.os.actions.entities.WorkflowRunStatus;
import dev.gitnode.os.actions.entities.WorkflowStep;
import dev.gitnode.os.actions.model.JobModel;
import dev.gitnode.os.actions.model.MatrixStrategy;
import dev.gitnode.os.actions.model.OnTriggerModel;
import dev.gitnode.os.actions.model.PullRequestTriggerModel;
import dev.gitnode.os.actions.model.PushTriggerModel;
import dev.gitnode.os.actions.model.StrategyModel;
import dev.gitnode.os.actions.model.WorkflowModel;
import dev.gitnode.os.actions.model.WorkflowRunTriggerModel;
import dev.gitnode.os.actions.repositories.WorkflowDefinitionRepository;
import dev.gitnode.os.actions.repositories.WorkflowJobRepository;
import dev.gitnode.os.actions.repositories.WorkflowLogRepository;
import dev.gitnode.os.actions.repositories.WorkflowRunRepository;
import dev.gitnode.os.actions.repositories.WorkflowStepRepository;
import dev.gitnode.os.actions.services.WorkflowParserService.ParsedWorkflow;
import dev.gitnode.os.events.actions.WorkflowJobQueuedEvent;
import dev.gitnode.os.events.actions.WorkflowRunCompletedEvent;
import dev.gitnode.os.events.actions.WorkflowRunQueuedEvent;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.shared.audit.annotations.Audited;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class WorkflowTriggerService {

  private static final String EXPR_OPEN = "${{";
  private static final String EXPR_CLOSE = "}}";

  private final WorkflowParserService parserService;
  private final WorkflowDefinitionRepository definitionRepository;
  private final WorkflowRunRepository runRepository;
  private final WorkflowJobRepository jobRepository;
  private final RepoLookupPort repoLookup;
  private final ApplicationEventPublisher eventPublisher;
  private final MatrixExpander matrixExpander;
  private final ExpressionEvaluator expressionEvaluator;
  private final JobAdvancer jobAdvancer;
  private final WorkflowStepRepository stepRepository;
  private final WorkflowLogRepository logRepository;
  private final JobCancellation jobCancellation;

  @Transactional
  public void triggerOnPush(
      final UUID repoId,
      final String branchName,
      final String pusherUsername,
      final @Nullable UUID actorId) {
    this.triggerOnPush(repoId, branchName, pusherUsername, actorId, null);
  }

  @Transactional
  public void triggerOnPush(
      final UUID repoId,
      final String branchName,
      final String pusherUsername,
      final @Nullable UUID actorId,
      final @Nullable String commitSha) {
    this.triggerOnPush(repoId, branchName, pusherUsername, actorId, commitSha, null);
  }

  /**
   * A branch was pushed. {@code beforeSha} (null when unknown) is the tip before the push; it
   * decides which files count as changed for {@code paths} / {@code paths-ignore}.
   */
  @Transactional
  public void triggerOnPush(
      final UUID repoId,
      final String branchName,
      final String pusherUsername,
      final @Nullable UUID actorId,
      final @Nullable String commitSha,
      final @Nullable String beforeSha) {

    final var repo =
        this.repoLookup
            .findById(repoId)
            .orElseGet(
                () -> {
                  log.warn("Push event for unknown repo id={}", repoId);
                  return null;
                });

    if (repo == null) {
      return;
    }

    final var ownerUsername = repo.ownerUsername();
    final var repoName = repo.name();
    final var branchRef = "refs/heads/" + branchName;

    final var workflows =
        this.parserService.parseWorkflows(
            ownerUsername, repoName, commitSha == null ? branchRef : commitSha);

    if (workflows.isEmpty()) {
      log.debug("No workflows found for {}/{} branch={}", ownerUsername, repoName, branchName);
      return;
    }

    final var changed =
        new ChangedFiles(() -> this.pushedFiles(ownerUsername, repoName, beforeSha, commitSha));
    this.startPushRuns(repoId, workflows, branchName, changed, actorId, commitSha, beforeSha);
  }

  private @Nullable List<String> pushedFiles(
      final String owner,
      final String repoName,
      final @Nullable String beforeSha,
      final @Nullable String commitSha) {
    return commitSha == null
        ? null
        : this.parserService.changedFiles(owner, repoName, beforeSha, commitSha);
  }

  private void startPushRuns(
      final UUID repoId,
      final List<ParsedWorkflow> workflows,
      final String branchName,
      final ChangedFiles changed,
      final @Nullable UUID actorId,
      final @Nullable String commitSha,
      final @Nullable String beforeSha) {

    final var branchRef = "refs/heads/" + branchName;
    final var extra =
        beforeSha == null || beforeSha.isBlank()
            ? null
            : Map.<String, Object>of("before", beforeSha);
    for (final var parsed : workflows) {
      if (hasSchedule(parsed.model())) {
        this.upsertDefinition(
            repoId, parsed); // lets the scheduler find workflows without a push trigger
      }
      if (this.hasPushTrigger(parsed.model(), branchName)
          && this.passesPushPaths(parsed.model(), changed)) {
        this.createRun(repoId, parsed, branchRef, commitSha, actorId, "push", null, null, extra);
      }
    }
  }

  /**
   * The changed files of a push or pull request, worked out once and only when a filter needs them.
   */
  private static final class ChangedFiles {
    private final java.util.function.Supplier<@Nullable List<String>> loader;
    private boolean loaded;
    private @Nullable List<String> files;

    ChangedFiles(final java.util.function.Supplier<@Nullable List<String>> loader) {
      this.loader = loader;
    }

    @Nullable List<String> get() {
      if (!this.loaded) {
        this.files = this.loader.get();
        this.loaded = true;
      }
      return this.files;
    }
  }

  private boolean passesPushPaths(final WorkflowModel model, final ChangedFiles changed) {
    final var push = this.getPushTrigger(model);
    if (push == null || !PathFilters.isFiltered(push.paths(), push.pathsIgnore())) {
      return true;
    }
    warnIfBothPathFilters("push", push.paths(), push.pathsIgnore());
    return PathFilters.allows(changed.get(), push.paths(), push.pathsIgnore());
  }

  private static void warnIfBothPathFilters(
      final String event, final @Nullable List<String> paths, final @Nullable List<String> ignore) {
    if (paths != null && !paths.isEmpty() && ignore != null && !ignore.isEmpty()) {
      log.warn("on.{} uses both paths and paths-ignore; only paths is applied", event);
    }
  }

  /**
   * Starts a run for a schedule that is due. The workflow is parsed fresh from the default branch,
   * as on GitHub, and must still contain that cron entry.
   */
  @Transactional
  public void triggerOnSchedule(
      final UUID repoId, final String workflowFilePath, final String cron) {

    final var repo = this.repoLookup.findById(repoId).orElse(null);
    if (repo == null || repo.defaultBranch() == null) {
      return;
    }
    final var ref = "refs/heads/" + repo.defaultBranch();

    for (final var parsed :
        this.parserService.parseWorkflows(repo.ownerUsername(), repo.name(), ref)) {
      if (parsed.filePath().equals(workflowFilePath) && hasSchedule(parsed.model(), cron)) {
        this.createRun(repoId, parsed, ref, null, null, "schedule", null);
      }
    }
  }

  static boolean hasSchedule(final WorkflowModel model) {
    return model.on() != null && model.on().schedule() != null && !model.on().schedule().isEmpty();
  }

  static boolean hasSchedule(final WorkflowModel model, final String cron) {
    return hasSchedule(model)
        && model.on().schedule().stream().anyMatch(s -> cron.equals(s.cron()));
  }

  /** A tag was pushed: runs workflows whose push trigger lists matching {@code tags}. */
  @Transactional
  public void triggerOnTag(
      final UUID repoId,
      final String tagName,
      final String pusherUsername,
      final @Nullable String commitSha) {
    this.triggerOnTag(repoId, tagName, pusherUsername, null, commitSha);
  }

  @Transactional
  public void triggerOnTag(
      final UUID repoId,
      final String tagName,
      final String pusherUsername,
      final @Nullable UUID actorId,
      final @Nullable String commitSha) {

    final var repo = this.repoLookup.findById(repoId).orElse(null);
    if (repo == null || commitSha == null) {
      return;
    }
    final var ref = "refs/tags/" + tagName;

    for (final var parsed :
        this.parserService.parseWorkflows(repo.ownerUsername(), repo.name(), commitSha)) {
      if (this.hasTagTrigger(parsed.model(), tagName)) {
        this.createRun(repoId, parsed, ref, commitSha, actorId, "push", null);
      }
    }
  }

  /** How deep a chain of workflow_run workflows may go, as on GitHub. */
  static final int MAX_WORKFLOW_RUN_DEPTH = 3;

  /**
   * A run finished: starts the workflows (from the default branch, as on GitHub) whose {@code
   * workflow_run} trigger names it. A workflow triggered this way carries the finished run in
   * {@code github.event.workflow_run}, and chains of such workflows stop after three levels.
   */
  @Transactional
  public void triggerOnWorkflowRunCompleted(final UUID repoId, final UUID completedRunId) {

    final var completed = this.runRepository.findById(completedRunId).orElse(null);
    final var repo = this.repoLookup.findById(repoId).orElse(null);
    if (completed == null || repo == null || repo.defaultBranch() == null) {
      return;
    }
    final int depth = workflowRunDepth(completed) + 1;
    if (depth > MAX_WORKFLOW_RUN_DEPTH) {
      log.info(
          "Not chaining workflow_run beyond {} levels: run {}",
          MAX_WORKFLOW_RUN_DEPTH,
          completedRunId);
      return;
    }

    final var ref = "refs/heads/" + repo.defaultBranch();
    this.parserService.parseWorkflows(repo.ownerUsername(), repo.name(), ref).stream()
        .filter(parsed -> listensTo(parsed, completed))
        .forEach(
            parsed ->
                this.createRun(
                    repoId,
                    parsed,
                    ref,
                    null,
                    completed.getTriggerActor(),
                    "workflow_run",
                    null,
                    null,
                    workflowRunPayload(completed, depth)));
  }

  private static boolean listensTo(final ParsedWorkflow parsed, final WorkflowRun completed) {
    final var on = parsed.model().on();
    return on != null
        && on.workflowRun() != null
        && workflowRunMatches(on.workflowRun(), completed);
  }

  /** Whether the trigger asks for this finished run: workflow name, type and branch filters. */
  static boolean workflowRunMatches(
      final WorkflowRunTriggerModel trigger, final WorkflowRun completed) {

    return namesWorkflow(trigger, completed.getWorkflowName())
        && asksForCompleted(trigger)
        && allowsBranch(trigger, branchOf(completed.getTriggerRef()));
  }

  private static boolean namesWorkflow(final WorkflowRunTriggerModel trigger, final String name) {
    return trigger.workflows() != null
        && trigger.workflows().stream().anyMatch(pattern -> matchesGlob(name, pattern));
  }

  /** Only "completed" exists here: runs are not announced when they start. */
  private static boolean asksForCompleted(final WorkflowRunTriggerModel trigger) {
    return trigger.types() == null
        || trigger.types().isEmpty()
        || trigger.types().contains("completed");
  }

  private static boolean allowsBranch(final WorkflowRunTriggerModel trigger, final String branch) {
    if (matchesAny(branch, trigger.branchesIgnore())) {
      return false;
    }
    return trigger.branches() == null
        || trigger.branches().isEmpty()
        || trigger.branches().stream().anyMatch(pattern -> matchesGlob(branch, pattern));
  }

  private static String branchOf(final @Nullable String ref) {
    return ref == null ? "" : ref.replaceFirst("^refs/(heads|tags)/", "");
  }

  private static int workflowRunDepth(final WorkflowRun run) {
    final var payload = run.getEventPayload();
    if (payload == null || !(payload.get("workflow_run") instanceof final Map<?, ?> info)) {
      return 0;
    }
    return info.get("chain_depth") instanceof final Number n ? n.intValue() : 0;
  }

  private static Map<String, Object> workflowRunPayload(
      final WorkflowRun completed, final int depth) {
    final var info = new java.util.LinkedHashMap<String, Object>();
    info.put("id", completed.getId().toString());
    info.put("name", completed.getWorkflowName());
    info.put("run_number", completed.getRunNumber());
    info.put("run_attempt", completed.getRunAttempt());
    info.put("event", completed.getTriggerEvent());
    info.put("status", "completed");
    info.put("conclusion", completed.getConclusion() == null ? "" : completed.getConclusion());
    info.put("head_branch", branchOf(completed.getTriggerRef()));
    info.put("head_sha", completed.getTriggerSha() == null ? "" : completed.getTriggerSha());
    info.put("chain_depth", depth);
    final var payload = new java.util.LinkedHashMap<String, Object>();
    payload.put("action", "completed");
    payload.put("workflow_run", info);
    return payload;
  }

  /** A pull request was opened (or its head moved): the workflows listening for it start. */
  @Transactional
  public void triggerOnPullRequest(
      final UUID repoId,
      final String sourceBranch,
      final String targetBranch,
      final String sourceSha,
      final @Nullable UUID actorId) {
    this.triggerOnPullRequest(repoId, sourceBranch, targetBranch, sourceSha, actorId, "opened");
  }

  /**
   * A pull request event. {@code action} is what GitHub reports: opened, synchronize, reopened or
   * closed. A workflow only starts when its {@code types} (default opened, synchronize, reopened)
   * contain it and its branch and path filters match.
   */
  @Transactional
  public void triggerOnPullRequest(
      final UUID repoId,
      final String sourceBranch,
      final String targetBranch,
      final String sourceSha,
      final @Nullable UUID actorId,
      final String action) {

    final var repo =
        this.repoLookup
            .findById(repoId)
            .orElseGet(
                () -> {
                  log.warn("PR event for unknown repo id={}", repoId);
                  return null;
                });

    if (repo == null) {
      return;
    }

    final var ownerUsername = repo.ownerUsername();
    final var repoName = repo.name();
    final var workflows = this.parserService.parseWorkflows(ownerUsername, repoName, sourceSha);
    final var changed =
        new ChangedFiles(
            () -> this.pullRequestFiles(ownerUsername, repoName, targetBranch, sourceSha));

    for (final var parsed : workflows) {
      if (this.hasPullRequestTrigger(parsed.model(), targetBranch, action)
          && passesPullRequestPaths(parsed.model(), changed)) {
        this.createRun(
            repoId,
            parsed,
            "refs/heads/" + sourceBranch,
            sourceSha,
            actorId,
            "pull_request",
            null,
            targetBranch,
            Map.of("action", action));
      }
    }
  }

  /**
   * Files a pull request changes. A merged pull request runs at the target tip, which is the merge
   * itself, so its files are what that commit changed against its first parent.
   */
  private @Nullable List<String> pullRequestFiles(
      final String owner, final String repoName, final String targetBranch, final String sha) {
    final var targetTip =
        this.parserService.resolveSha(owner, repoName, "refs/heads/" + targetBranch);
    return sha.equals(targetTip)
        ? this.parserService.changedFiles(owner, repoName, null, sha)
        : this.parserService.changedFilesOfPullRequest(owner, repoName, targetBranch, sha);
  }

  private static boolean passesPullRequestPaths(
      final WorkflowModel model, final ChangedFiles changed) {
    final var pr = model.on() == null ? null : model.on().pullRequest();
    if (pr == null || !PathFilters.isFiltered(pr.paths(), pr.pathsIgnore())) {
      return true;
    }
    warnIfBothPathFilters("pull_request", pr.paths(), pr.pathsIgnore());
    return PathFilters.allows(changed.get(), pr.paths(), pr.pathsIgnore());
  }

  /** Activity types a {@code pull_request} trigger without {@code types} reacts to. */
  static final List<String> DEFAULT_PULL_REQUEST_TYPES =
      List.of("opened", "synchronize", "reopened");

  /** Every release activity type GitNode reports; the others (edited, deleted, ...) never fire. */
  static final List<String> RELEASE_TYPES =
      List.of("created", "published", "released", "prereleased");

  /**
   * A branch or tag was created. Runs the workflows with {@code on: create}, read at the new ref
   * (as GitHub does), with {@code ref} and {@code ref_type} in the event payload.
   */
  @Transactional
  public void triggerOnRefCreated(
      final UUID repoId,
      final String refType,
      final String refName,
      final @Nullable String sha,
      final @Nullable UUID actorId) {

    final var repo = this.repoLookup.findById(repoId).orElse(null);
    if (repo == null) {
      return;
    }
    final var fullRef = refPrefix(refType) + refName;
    final var commit =
        sha != null
            ? sha
            : this.parserService.resolveSha(repo.ownerUsername(), repo.name(), fullRef);
    if (commit == null) {
      log.debug(
          "Created ref {} of {}/{} is gone already", fullRef, repo.ownerUsername(), repo.name());
      return;
    }

    for (final var parsed :
        this.parserService.parseWorkflows(repo.ownerUsername(), repo.name(), commit)) {
      if (parsed.model().on() != null && parsed.model().on().create() != null) {
        this.createRun(
            repoId,
            parsed,
            fullRef,
            commit,
            actorId,
            "create",
            null,
            null,
            refPayload(repo, refType, refName));
      }
    }
  }

  /**
   * A branch or tag was deleted. Runs the workflows with {@code on: delete} read from the default
   * branch, because the deleted ref no longer exists (as on GitHub).
   */
  @Transactional
  public void triggerOnRefDeleted(
      final UUID repoId, final String refType, final String refName, final @Nullable UUID actorId) {

    final var repo = this.repoLookup.findById(repoId).orElse(null);
    if (repo == null || repo.defaultBranch() == null) {
      return;
    }
    final var defaultRef = "refs/heads/" + repo.defaultBranch();
    final var commit = this.parserService.resolveSha(repo.ownerUsername(), repo.name(), defaultRef);
    if (commit == null) {
      return;
    }

    for (final var parsed :
        this.parserService.parseWorkflows(repo.ownerUsername(), repo.name(), commit)) {
      if (parsed.model().on() != null && parsed.model().on().deleted() != null) {
        this.createRun(
            repoId,
            parsed,
            refPrefix(refType) + refName,
            commit,
            actorId,
            "delete",
            null,
            null,
            refPayload(repo, refType, refName));
      }
    }
  }

  private static String refPrefix(final String refType) {
    return "tag".equals(refType) ? "refs/tags/" : "refs/heads/";
  }

  private static Map<String, Object> refPayload(
      final dev.gitnode.os.repo.api.RepoLookupPort.RepoRef repo,
      final String refType,
      final String refName) {
    final var payload = new java.util.LinkedHashMap<String, Object>();
    payload.put("ref", refName);
    payload.put("ref_type", refType);
    payload.put("master_branch", repo.defaultBranch() == null ? "main" : repo.defaultBranch());
    return payload;
  }

  /**
   * A release was created or published. Runs the workflows with {@code on: release} whose {@code
   * types} contain one of {@code actions}, once per workflow, read at the release tag (the default
   * branch when the tag is gone). The first matching action is the one reported.
   */
  @Transactional
  public void triggerOnRelease(
      final UUID repoId,
      final String tagName,
      final @Nullable String releaseName,
      final boolean prerelease,
      final boolean draft,
      final List<String> actions,
      final @Nullable UUID actorId) {

    final var repo = this.repoLookup.findById(repoId).orElse(null);
    if (repo == null) {
      return;
    }
    final var tagRef = "refs/tags/" + tagName;
    final var commit = this.releaseCommit(repo, tagRef);
    if (commit == null) {
      return;
    }

    final var release = new java.util.LinkedHashMap<String, Object>();
    release.put("tag_name", tagName);
    release.put("name", releaseName == null ? "" : releaseName);
    release.put("prerelease", prerelease);
    release.put("draft", draft);

    for (final var parsed :
        this.parserService.parseWorkflows(repo.ownerUsername(), repo.name(), commit)) {
      final var action = releaseActionFor(parsed.model(), actions);
      if (action != null) {
        final var extra = new java.util.LinkedHashMap<String, Object>();
        extra.put("action", action);
        extra.put("release", release);
        this.createRun(repoId, parsed, tagRef, commit, actorId, "release", null, null, extra);
      }
    }
  }

  /** The release tag's commit, or the default branch's when the tag is gone; null when neither. */
  private @Nullable String releaseCommit(
      final dev.gitnode.os.repo.api.RepoLookupPort.RepoRef repo, final String tagRef) {
    final var tagged = this.parserService.resolveSha(repo.ownerUsername(), repo.name(), tagRef);
    if (tagged != null || repo.defaultBranch() == null) {
      return tagged;
    }
    return this.parserService.resolveSha(
        repo.ownerUsername(), repo.name(), "refs/heads/" + repo.defaultBranch());
  }

  private static @Nullable String releaseActionFor(
      final WorkflowModel model, final List<String> actions) {
    final var trigger = model.on() == null ? null : model.on().release();
    return trigger == null ? null : firstMatchingReleaseType(trigger.types(), actions);
  }

  static @Nullable String firstMatchingReleaseType(
      final @Nullable List<String> wanted, final List<String> actions) {
    final var types = wanted == null || wanted.isEmpty() ? RELEASE_TYPES : wanted;
    return actions.stream().filter(types::contains).findFirst().orElse(null);
  }

  @Audited(
      action = "ACTIONS_WORKFLOW_DISPATCH",
      entityType = "WORKFLOW_RUN",
      detailsSpEL = "'repoId=' + #repoId + ', workflow=' + #workflowFilePath + ', ref=' + #ref")
  @Transactional
  public void triggerManual(
      final UUID repoId,
      final String workflowFilePath,
      final String ref,
      final @Nullable Map<String, String> inputs,
      final @Nullable UUID actorId) {

    final var repo =
        this.repoLookup
            .findById(repoId)
            .orElseThrow(() -> new ItemNotFoundException("Repository not found: " + repoId));

    final var ownerUsername = repo.ownerUsername();
    final var repoName = repo.name();

    final var workflows = this.parserService.parseWorkflows(ownerUsername, repoName, ref);

    boolean started = false;
    for (final var parsed : workflows) {
      if (!workflowFilePath.isBlank() && !parsed.filePath().endsWith(workflowFilePath)) {
        continue;
      }
      final boolean hasDispatchTrigger =
          parsed.model().on() != null && parsed.model().on().workflowDispatch() != null;
      if (!hasDispatchTrigger) {
        continue;
      }
      this.createRun(repoId, parsed, ref, null, actorId, "workflow_dispatch", inputs);
      started = true;
      log.info("Manual dispatch queued: repo={} workflow={} ref={}", repoId, workflowFilePath, ref);
    }
    if (!started) {
      this.explainWhyNothingStarted(ownerUsername, repoName, ref, workflowFilePath);
    }
  }

  /** A workflow that is not valid YAML is the usual reason a dispatch starts nothing; say so. */
  private void explainWhyNothingStarted(
      final String ownerUsername,
      final String repoName,
      final String ref,
      final String workflowFilePath) {

    this.parserService.findParseFailures(ownerUsername, repoName, ref).stream()
        .filter(f -> workflowFilePath.isBlank() || f.filePath().endsWith(workflowFilePath))
        .findFirst()
        .ifPresent(
            f -> {
              throw new BadRequestException(
                  "Invalid workflow file " + f.filePath() + ": " + f.reason());
            });
  }

  private void createRun(
      final UUID repoId,
      final ParsedWorkflow parsed,
      final String triggerRef,
      final @Nullable String triggerSha,
      final @Nullable UUID actorId,
      final String triggerEvent,
      final @Nullable Map<String, String> inputs) {
    this.createRun(repoId, parsed, triggerRef, triggerSha, actorId, triggerEvent, inputs, null);
  }

  private void createRun(
      final UUID repoId,
      final ParsedWorkflow parsed,
      final String triggerRef,
      final @Nullable String triggerSha,
      final @Nullable UUID actorId,
      final String triggerEvent,
      final @Nullable Map<String, String> inputs,
      final @Nullable String baseBranch) {
    this.createRun(
        repoId, parsed, triggerRef, triggerSha, actorId, triggerEvent, inputs, baseBranch, null);
  }

  /**
   * {@code extraPayload} entries (for events GitHub documents, like workflow_run) are merged in.
   */
  private void createRun(
      final UUID repoId,
      final ParsedWorkflow parsed,
      final String triggerRef,
      final @Nullable String triggerSha,
      final @Nullable UUID actorId,
      final String triggerEvent,
      final @Nullable Map<String, String> inputs,
      final @Nullable String baseBranch,
      final @Nullable Map<String, Object> extraPayload) {

    final var defId = this.upsertDefinition(repoId, parsed);
    final var runNumber = this.runRepository.nextRunNumber(repoId);

    final var run = new WorkflowRun();
    run.setRepoId(repoId);
    run.setWorkflowDefId(defId);
    run.setWorkflowName(this.resolveWorkflowName(parsed.model(), parsed.filePath()));
    run.setRunNumber(runNumber);
    run.setTriggerEvent(triggerEvent);
    run.setTriggerRef(triggerRef);
    run.setTriggerSha(triggerSha);
    run.setTriggerActor(actorId);
    run.setWorkflowContent(parsed.rawContent());
    this.repoLookup
        .findById(repoId)
        .ifPresent(
            repo -> {
              run.setHeadCommitMessage(
                  this.parserService.readCommitMessage(
                      repo.ownerUsername(),
                      repo.name(),
                      triggerSha != null ? triggerSha : triggerRef));
              run.setEventPayload(
                  EventPayloads.build(
                      triggerEvent,
                      repo,
                      triggerRef,
                      triggerSha,
                      run.getHeadCommitMessage(),
                      baseBranch,
                      inputs));
              if (extraPayload != null && run.getEventPayload() != null) {
                run.getEventPayload().putAll(extraPayload);
              }
            });
    run.setStatus(WorkflowRunStatus.QUEUED);
    run.setInputs(inputs);

    this.applyConcurrency(repoId, parsed.model(), run, triggerRef, triggerEvent);

    final var savedRun = this.runRepository.save(run);

    this.createJobs(savedRun, parsed.model());

    this.eventPublisher.publishEvent(
        new WorkflowRunQueuedEvent(
            savedRun.getId(), repoId, savedRun.getWorkflowName(), triggerEvent, triggerRef));

    log.info(
        "Workflow run queued: runId={}, workflow={}, trigger={}",
        savedRun.getId(),
        savedRun.getWorkflowName(),
        triggerEvent);
  }

  private void createJobs(final WorkflowRun run, final WorkflowModel model) {

    if (model.jobs() == null || model.jobs().isEmpty()) {
      return;
    }

    final var created = new ArrayList<WorkflowJob>();
    for (final Map.Entry<String, JobModel> entry : model.jobs().entrySet()) {
      created.addAll(this.createJobsOf(run, entry.getKey(), entry.getValue(), created, model));
    }

    this.settleWaitingJobs(run, created, model);
    this.completeRunIfFinished(run, created);
  }

  /**
   * The jobs one entry of {@code jobs:} stands for: one, one per matrix combination, or a
   * placeholder.
   */
  private List<WorkflowJob> createJobsOf(
      final WorkflowRun run,
      final String jobKey,
      final JobModel jobDef,
      final List<WorkflowJob> createdSoFar,
      final WorkflowModel model) {

    final var baseName = jobDef.name() != null ? jobDef.name() : jobKey;
    if (isReusableWorkflowCall(jobDef)) {
      return List.of(this.createUnsupportedCallJob(run, jobKey, baseName, jobDef));
    }
    if (isDynamicMatrix(jobDef)) {
      return this.createDynamicMatrixJobs(run, jobDef, jobKey, baseName, createdSoFar, model);
    }
    final var jobs = new ArrayList<WorkflowJob>();
    for (final var matrixValues : this.matrixExpander.expand(jobDef)) {
      jobs.add(
          this.createSingleJob(
              run,
              jobDef,
              jobKey,
              baseName,
              matrixValues,
              Stream.concat(createdSoFar.stream(), jobs.stream()).toList()));
    }
    return jobs;
  }

  /** {@code jobs.<id>.uses: ./.gitnode/workflows/x.yml}: a call of a reusable workflow. */
  private static boolean isReusableWorkflowCall(final JobModel jobDef) {
    return jobDef.uses() != null && !jobDef.uses().isBlank();
  }

  /**
   * Reusable workflows are not run yet. Rather than a job without steps that turns green and does
   * nothing, the job fails with the reason in its log, and the jobs that need it are skipped.
   */
  private WorkflowJob createUnsupportedCallJob(
      final WorkflowRun run, final String jobKey, final String baseName, final JobModel jobDef) {

    final var job = new WorkflowJob();
    job.setRunId(run.getId());
    job.setJobKey(jobKey);
    job.setName(baseName);
    job.setRunnerLabels(RunnerLabels.forJob(null));
    job.setNeeds(jobDef.needs());
    job.setStatus(WorkflowJobStatus.FAILURE);
    job.setConclusion("failure");
    job.setCompletedAt(java.time.Instant.now());
    final var saved = this.jobRepository.save(job);

    final var step = new WorkflowStep();
    step.setJobId(saved.getId());
    step.setStepNumber(1);
    step.setName("Reusable workflow is not supported");
    step.setStatus("completed");
    step.setConclusion("failure");
    step.setStartedAt(saved.getCompletedAt());
    step.setCompletedAt(saved.getCompletedAt());
    final var savedStep = this.stepRepository.save(step);

    final var line = new WorkflowLog();
    line.setStepId(savedStep.getId());
    line.setLineNumber(1);
    line.setLevel("error");
    line.setContent(
        "##[error]Reusable workflows are not supported yet: jobs."
            + jobKey
            + ".uses is '"
            + jobDef.uses()
            + "'. This job did not run.");
    this.logRepository.save(line);
    log.warn(
        "Run {}: job '{}' calls reusable workflow '{}', which is not supported",
        run.getId(),
        jobKey,
        jobDef.uses());
    return saved;
  }

  /**
   * A run whose jobs all finished while it was being created (every job skipped or failed at once)
   * would never get a job-completed event, so it is closed here.
   */
  private void completeRunIfFinished(final WorkflowRun run, final List<WorkflowJob> created) {
    if (created.isEmpty() || !created.stream().allMatch(JobNeeds::isTerminal)) {
      return;
    }
    final boolean failed =
        created.stream().anyMatch(j -> j.getStatus() == WorkflowJobStatus.FAILURE);
    run.setStatus(failed ? WorkflowRunStatus.FAILURE : WorkflowRunStatus.SUCCESS);
    run.setConclusion(failed ? "failure" : "success");
    run.setCompletedAt(java.time.Instant.now());
    this.runRepository.save(run);
    this.eventPublisher.publishEvent(
        new WorkflowRunCompletedEvent(
            run.getId(), run.getRepoId(), run.getWorkflowName(), run.getConclusion()));
  }

  private static boolean isDynamicMatrix(final JobModel jobDef) {
    return jobDef.strategy() != null
        && jobDef.strategy().matrix() != null
        && jobDef.strategy().matrix().isDynamic();
  }

  /**
   * A matrix given as an expression can only be evaluated once the jobs it needs have finished, so
   * the job starts as one WAITING placeholder that {@link #expandDynamicMatrix} replaces. Without
   * needs everything the expression can see is already known and the jobs are created right away.
   */
  private List<WorkflowJob> createDynamicMatrixJobs(
      final WorkflowRun run,
      final JobModel jobDef,
      final String jobKey,
      final String baseName,
      final List<WorkflowJob> createdSoFar,
      final WorkflowModel model) {

    final var placeholder = new WorkflowJob();
    placeholder.setRunId(run.getId());
    placeholder.setJobKey(jobKey);
    placeholder.setName(baseName);
    placeholder.setRunnerLabels(this.resolveLabels(jobDef, Map.of()));
    placeholder.setNeeds(jobDef.needs());
    placeholder.setMatrixExpression(jobDef.strategy().matrix().expression());
    placeholder.setStatus(WorkflowJobStatus.WAITING);
    final var saved = this.jobRepository.save(placeholder);
    if (hasUnmetNeeds(jobDef)) {
      return List.of(saved);
    }
    final var all = new ArrayList<>(createdSoFar);
    all.add(saved);
    return this.expandDynamicMatrix(run, saved, all, model);
  }

  /**
   * Replaces a dynamic-matrix placeholder by the jobs its expression evaluates to. A matrix that
   * cannot be evaluated fails the placeholder (with the reason in the server log) instead of
   * silently dropping the job. Returns the jobs that now stand in for the placeholder.
   */
  @Transactional
  public List<WorkflowJob> expandDynamicMatrix(
      final WorkflowRun run,
      final WorkflowJob placeholder,
      final List<WorkflowJob> allJobs,
      final @Nullable WorkflowModel model) {

    final var jobDef = this.jobAdvancer.jobDefinition(model, placeholder);
    if (jobDef == null || placeholder.getMatrixExpression() == null) {
      return this.failPlaceholder(placeholder, "workflow definition is not available");
    }
    final List<Map<String, String>> combinations;
    try {
      combinations = this.evaluateMatrix(run, placeholder, allJobs, jobDef);
    } catch (final RuntimeException e) {
      return this.failPlaceholder(placeholder, e.getMessage());
    }

    this.jobRepository.delete(placeholder);
    final var created = new ArrayList<WorkflowJob>();
    final var baseName = jobDef.name() != null ? jobDef.name() : placeholder.getJobKey();
    for (final var values : combinations) {
      created.add(
          this.createSingleJob(run, jobDef, placeholder.getJobKey(), baseName, values, allJobs));
    }
    return created;
  }

  /**
   * The combinations the placeholder's matrix expression evaluates to; throws when it is invalid.
   */
  private List<Map<String, String>> evaluateMatrix(
      final WorkflowRun run,
      final WorkflowJob placeholder,
      final List<WorkflowJob> allJobs,
      final JobModel jobDef) {

    final var context = this.jobAdvancer.needsContext(run, placeholder, allJobs);
    // The full expression language is needed here (fromJSON, needs.*.outputs), not just the
    // simple substitution evaluate() does. fromJSON hands an object back as its JSON text.
    final var json =
        ExpressionEngine.asString(
            ExpressionEngine.evaluate(
                unwrapExpression(placeholder.getMatrixExpression()), context, "success"));
    final var strategy = jobDef.strategy();
    final var evaluated =
        new StrategyModel(
            MatrixStrategy.fromJson(json),
            strategy == null ? null : strategy.failFast(),
            strategy == null ? null : strategy.maxParallel());
    return this.matrixExpander.expand(jobDef.withStrategy(evaluated));
  }

  private List<WorkflowJob> failPlaceholder(final WorkflowJob placeholder, final String reason) {
    log.warn(
        "Dynamic matrix of job '{}' (run {}) failed: {}",
        placeholder.getJobKey(),
        placeholder.getRunId(),
        reason);
    placeholder.setStatus(WorkflowJobStatus.FAILURE);
    placeholder.setConclusion("failure");
    placeholder.setMatrixExpression(null);
    placeholder.setCompletedAt(java.time.Instant.now());
    return List.of(this.jobRepository.save(placeholder));
  }

  private static String unwrapExpression(final String expression) {
    final var text = expression.strip();
    if (text.startsWith(EXPR_OPEN) && text.endsWith(EXPR_CLOSE)) {
      return text.substring(EXPR_OPEN.length(), text.length() - EXPR_CLOSE.length()).strip();
    }
    return text;
  }

  /** A job skipped by its own condition may be what other jobs are waiting for. */
  private void settleWaitingJobs(
      final WorkflowRun run, final List<WorkflowJob> created, final WorkflowModel model) {

    for (final var job : this.jobAdvancer.advance(run, created, model)) {
      this.jobRepository.save(job);
      if (job.getStatus() == WorkflowJobStatus.QUEUED) {
        this.publishQueued(run, job);
      }
    }
  }

  private WorkflowJob createSingleJob(
      final WorkflowRun run,
      final JobModel jobDef,
      final String jobKey,
      final String baseName,
      final Map<String, String> matrixValues,
      final List<WorkflowJob> createdSoFar) {

    final var matrixCtx =
        matrixValues.entrySet().stream()
            .collect(Collectors.toMap(e -> "matrix." + e.getKey(), Map.Entry::getValue));
    final var resolvedName = this.expressionEvaluator.evaluate(baseName, matrixCtx);
    final var nameWasInterpolated = !resolvedName.equals(baseName);
    final var jobName =
        nameWasInterpolated ? resolvedName : this.suffixMatrixName(resolvedName, matrixValues);

    final var job = new WorkflowJob();
    job.setRunId(run.getId());
    job.setJobKey(jobKey);
    job.setName(jobName);
    job.setRunnerLabels(this.resolveLabels(jobDef, matrixCtx));
    job.setNeeds(jobDef.needs());
    job.setMatrixValues(matrixValues.isEmpty() ? null : matrixValues);

    if (hasUnmetNeeds(jobDef)) {
      // Its if: is evaluated once the jobs it needs have finished (see JobAdvancer).
      job.setStatus(WorkflowJobStatus.WAITING);
      return this.jobRepository.save(job);
    }

    if (!this.jobAdvancer.shouldRun(run, job, jobDef, createdSoFar)) {
      job.setStatus(WorkflowJobStatus.SKIPPED);
      job.setConclusion("skipped");
      return this.jobRepository.save(job);
    }

    job.setStatus(WorkflowJobStatus.QUEUED);
    final var savedJob = this.jobRepository.save(job);
    this.publishQueued(run, savedJob);
    return savedJob;
  }

  private void publishQueued(final WorkflowRun run, final WorkflowJob job) {
    this.eventPublisher.publishEvent(
        new WorkflowJobQueuedEvent(
            job.getId(), run.getId(), run.getRepoId(), job.getName(), job.getRunnerLabels()));
  }

  private static boolean hasUnmetNeeds(final JobModel jobDef) {
    return jobDef.needs() != null && !jobDef.needs().isEmpty();
  }

  private String suffixMatrixName(final String baseName, final Map<String, String> matrixValues) {

    if (matrixValues.isEmpty()) {
      return baseName;
    }

    final var suffix = String.join(", ", matrixValues.values());
    return baseName + " (" + suffix + ")";
  }

  private Map<String, String> buildGithubContext(final WorkflowRun run) {

    final var ctx = new HashMap<String, String>();
    if (run.getTriggerRef() != null) {
      ctx.put("github.ref", run.getTriggerRef());
    }
    ctx.put("github.event_name", run.getTriggerEvent());
    if (run.getTriggerSha() != null) {
      ctx.put("github.sha", run.getTriggerSha());
    }
    if (run.getInputs() != null) {
      run.getInputs().forEach((k, v) -> ctx.put("inputs." + k, v != null ? v : ""));
    }
    return ctx;
  }

  private void applyConcurrency(
      final UUID repoId,
      final WorkflowModel model,
      final WorkflowRun run,
      final @Nullable String triggerRef,
      final String triggerEvent) {

    final var concurrency = model.concurrency();
    if (concurrency == null || concurrency.group() == null) {
      return;
    }

    final var ctx = new HashMap<String, String>();
    if (triggerRef != null) {
      ctx.put("github.ref", triggerRef);
    }
    ctx.put("github.event_name", triggerEvent);

    final var group = this.expressionEvaluator.evaluate(concurrency.group(), ctx);
    run.setConcurrencyGroup(group);

    if (!concurrency.cancelInProgress()) {
      return;
    }

    final var active =
        this.runRepository.findByRepoIdAndConcurrencyGroupAndStatusIn(
            repoId, group, List.of(WorkflowRunStatus.QUEUED, WorkflowRunStatus.IN_PROGRESS));

    for (final var existing : active) {
      existing.setStatus(WorkflowRunStatus.CANCELLED);
      existing.setConclusion("cancelled");
      existing.setCompletedAt(java.time.Instant.now());
    }

    if (!active.isEmpty()) {
      this.runRepository.saveAll(active);
      this.cancelJobsForRuns(active.stream().map(WorkflowRun::getId).collect(Collectors.toSet()));
    }
  }

  private void cancelJobsForRuns(final java.util.Set<UUID> runIds) {

    for (final var runId : runIds) {
      final var jobs = this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(runId);
      jobs.stream()
          .filter(
              job ->
                  job.getStatus() == WorkflowJobStatus.QUEUED
                      || job.getStatus() == WorkflowJobStatus.WAITING
                      || job.getStatus() == WorkflowJobStatus.IN_PROGRESS)
          .forEach(this.jobCancellation::cancel);
    }
  }

  private UUID upsertDefinition(final UUID repoId, final ParsedWorkflow parsed) {

    final var hash = DigestUtils.sha256Hex(parsed.rawContent());

    return this.definitionRepository
        .findByRepoIdAndFilePath(repoId, parsed.filePath())
        .map(
            existing -> {
              if (!hash.equals(existing.getContentHash())) {
                existing.setContent(parsed.rawContent());
                existing.setContentHash(hash);
                existing.setName(this.resolveWorkflowName(parsed.model(), parsed.filePath()));
                this.definitionRepository.save(existing);
              }
              return existing.getId();
            })
        .orElseGet(
            () -> {
              final var def = new WorkflowDefinition();
              def.setRepoId(repoId);
              def.setFilePath(parsed.filePath());
              def.setContent(parsed.rawContent());
              def.setContentHash(hash);
              def.setName(this.resolveWorkflowName(parsed.model(), parsed.filePath()));
              return this.definitionRepository.save(def).getId();
            });
  }

  /** runs-on labels, with ${{ matrix.* }} resolved (runs-on: ${{ matrix.os }} is very common). */
  private List<String> resolveLabels(final JobModel job, final Map<String, String> matrixCtx) {

    if (job.runsOn() == null || job.runsOn().isEmpty()) {
      return RunnerLabels.forJob(null);
    }
    return RunnerLabels.forJob(
        job.runsOn().stream()
            .map(label -> this.expressionEvaluator.evaluate(label, matrixCtx))
            .toList());
  }

  private String resolveWorkflowName(final WorkflowModel model, final String filePath) {

    if (model.name() != null && !model.name().isBlank()) {
      return model.name();
    }

    final var fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
    return fileName.replaceAll("\\.(yml|yaml)$", "");
  }

  boolean hasPushTrigger(final WorkflowModel model, final String branchName) {

    final var trigger = this.getPushTrigger(model);

    if (trigger == null) {
      return false;
    }

    if (this.matchesBranchIgnore(trigger, branchName)) {
      return false;
    }

    if (trigger.branches() == null || trigger.branches().isEmpty()) {
      // GitHub: a push trigger that only filters tags does not fire for branches.
      return !hasTagFilter(trigger);
    }

    return trigger.branches().stream().anyMatch(p -> matchesGlob(branchName, p));
  }

  /**
   * Whether a pushed tag triggers the workflow: it needs a push trigger that either has no filters
   * at all or whose {@code tags} match (patterns starting with {@code !} exclude) and whose {@code
   * tags-ignore} do not.
   */
  boolean hasTagTrigger(final WorkflowModel model, final String tagName) {

    final var trigger = this.getPushTrigger(model);
    if (trigger == null) {
      return false;
    }
    if (matchesAny(tagName, trigger.tagsIgnore())) {
      return false;
    }
    if (trigger.tags() == null || trigger.tags().isEmpty()) {
      // GitHub: a push trigger that only filters branches does not fire for tags.
      return !hasBranchFilter(trigger) && trigger.tagsIgnore() == null;
    }
    final var includes = trigger.tags().stream().filter(p -> !p.startsWith("!")).toList();
    final var excludes =
        trigger.tags().stream().filter(p -> p.startsWith("!")).map(p -> p.substring(1)).toList();
    return matchesAny(tagName, includes) && !matchesAny(tagName, excludes);
  }

  private static boolean hasTagFilter(final PushTriggerModel trigger) {
    return (trigger.tags() != null && !trigger.tags().isEmpty())
        || (trigger.tagsIgnore() != null && !trigger.tagsIgnore().isEmpty());
  }

  private static boolean hasBranchFilter(final PushTriggerModel trigger) {
    return (trigger.branches() != null && !trigger.branches().isEmpty())
        || (trigger.branchesIgnore() != null && !trigger.branchesIgnore().isEmpty());
  }

  private static boolean matchesAny(final String value, final @Nullable List<String> patterns) {
    return patterns != null && patterns.stream().anyMatch(p -> matchesGlob(value, p));
  }

  boolean hasPullRequestTrigger(final WorkflowModel model, final String branchName) {
    return this.hasPullRequestTrigger(model, branchName, "opened");
  }

  boolean hasPullRequestTrigger(
      final WorkflowModel model, final String branchName, final String action) {

    final var on = model.on();
    if (on == null || on.pullRequest() == null) {
      return false;
    }
    final var pr = on.pullRequest();
    final var types =
        pr.types() == null || pr.types().isEmpty() ? DEFAULT_PULL_REQUEST_TYPES : pr.types();
    return types.contains(action) && pullRequestBranchMatches(pr, branchName);
  }

  private static boolean pullRequestBranchMatches(
      final PullRequestTriggerModel pr, final String branchName) {

    if (matchesAny(branchName, pr.branchesIgnore())) {
      return false;
    }
    return pr.branches() == null
        || pr.branches().isEmpty()
        || matchesAny(branchName, pr.branches());
  }

  private @Nullable PushTriggerModel getPushTrigger(final WorkflowModel model) {

    final OnTriggerModel on = model.on();

    if (on == null) {
      return null;
    }

    return on.push();
  }

  private boolean matchesBranchIgnore(final PushTriggerModel trigger, final String branchName) {

    return trigger.branchesIgnore() != null
        && trigger.branchesIgnore().stream().anyMatch(p -> matchesGlob(branchName, p));
  }

  static boolean matchesGlob(final String value, final String pattern) {

    final var regex =
        pattern
            .replace(".", "\\.")
            .replace("**", "__DS__")
            .replace("*", "[^/]*")
            .replace("__DS__", ".*")
            .replace("?", "[^/]");

    return value.matches(regex);
  }
}
