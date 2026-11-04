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
import dev.gitnode.os.actions.entities.WorkflowRun;
import dev.gitnode.os.actions.entities.WorkflowRunStatus;
import dev.gitnode.os.actions.model.JobModel;
import dev.gitnode.os.actions.model.MatrixStrategy;
import dev.gitnode.os.actions.model.OnTriggerModel;
import dev.gitnode.os.actions.model.PushTriggerModel;
import dev.gitnode.os.actions.model.StrategyModel;
import dev.gitnode.os.actions.model.WorkflowModel;
import dev.gitnode.os.actions.model.WorkflowRunTriggerModel;
import dev.gitnode.os.actions.repositories.WorkflowDefinitionRepository;
import dev.gitnode.os.actions.repositories.WorkflowJobRepository;
import dev.gitnode.os.actions.repositories.WorkflowRunRepository;
import dev.gitnode.os.actions.services.WorkflowParserService.ParsedWorkflow;
import dev.gitnode.os.events.actions.WorkflowJobQueuedEvent;
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

    for (final var parsed : workflows) {
      if (hasSchedule(parsed.model())) {
        this.upsertDefinition(
            repoId, parsed); // lets the scheduler find workflows without a push trigger
      }
      if (!this.hasPushTrigger(parsed.model(), branchName)) {
        continue;
      }

      this.createRun(repoId, parsed, branchRef, commitSha, actorId, "push", null);
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

  @Transactional
  public void triggerOnPullRequest(
      final UUID repoId,
      final String sourceBranch,
      final String targetBranch,
      final String sourceSha,
      final @Nullable UUID actorId) {

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
    final var branchRef = "refs/heads/" + sourceBranch;

    final var workflows = this.parserService.parseWorkflows(ownerUsername, repoName, sourceSha);

    for (final var parsed : workflows) {
      if (!this.hasPullRequestTrigger(parsed.model(), targetBranch)) {
        continue;
      }

      this.createRun(
          repoId, parsed, branchRef, sourceSha, actorId, "pull_request", null, targetBranch);
    }
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
      final var jobKey = entry.getKey();
      final var jobDef = entry.getValue();
      final var baseName = jobDef.name() != null ? jobDef.name() : jobKey;
      if (isDynamicMatrix(jobDef)) {
        created.addAll(this.createDynamicMatrixJobs(run, jobDef, jobKey, baseName, created, model));
        continue;
      }
      final var combinations = this.matrixExpander.expand(jobDef);

      for (final var matrixValues : combinations) {
        created.add(this.createSingleJob(run, jobDef, jobKey, baseName, matrixValues, created));
      }
    }

    this.settleWaitingJobs(run, created, model);
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
    }

    if (!active.isEmpty()) {
      this.runRepository.saveAll(active);
      this.cancelJobsForRuns(active.stream().map(WorkflowRun::getId).collect(Collectors.toSet()));
    }
  }

  private void cancelJobsForRuns(final java.util.Set<UUID> runIds) {

    for (final var runId : runIds) {
      final var jobs = this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(runId);
      for (final var job : jobs) {
        if (job.getStatus() == WorkflowJobStatus.QUEUED
            || job.getStatus() == WorkflowJobStatus.WAITING
            || job.getStatus() == WorkflowJobStatus.IN_PROGRESS) {
          job.setStatus(WorkflowJobStatus.CANCELLED);
          job.setConclusion("cancelled");
        }
      }
      this.jobRepository.saveAll(jobs);
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

    final var on = model.on();

    if (on == null || on.pullRequest() == null) {
      return false;
    }

    final var pr = on.pullRequest();

    if (pr.branchesIgnore() != null
        && pr.branchesIgnore().stream().anyMatch(p -> matchesGlob(branchName, p))) {
      return false;
    }

    if (pr.branches() == null || pr.branches().isEmpty()) {
      return true;
    }

    return pr.branches().stream().anyMatch(p -> matchesGlob(branchName, p));
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
