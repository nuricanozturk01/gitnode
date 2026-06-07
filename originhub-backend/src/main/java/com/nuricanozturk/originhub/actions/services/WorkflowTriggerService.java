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

import com.nuricanozturk.originhub.actions.entities.WorkflowDefinition;
import com.nuricanozturk.originhub.actions.entities.WorkflowJob;
import com.nuricanozturk.originhub.actions.entities.WorkflowJobStatus;
import com.nuricanozturk.originhub.actions.entities.WorkflowRun;
import com.nuricanozturk.originhub.actions.entities.WorkflowRunStatus;
import com.nuricanozturk.originhub.actions.model.JobModel;
import com.nuricanozturk.originhub.actions.model.OnTriggerModel;
import com.nuricanozturk.originhub.actions.model.PushTriggerModel;
import com.nuricanozturk.originhub.actions.model.WorkflowModel;
import com.nuricanozturk.originhub.actions.repositories.WorkflowDefinitionRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowJobRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowRunRepository;
import com.nuricanozturk.originhub.actions.services.WorkflowParserService.ParsedWorkflow;
import com.nuricanozturk.originhub.events.actions.WorkflowJobQueuedEvent;
import com.nuricanozturk.originhub.events.actions.WorkflowRunQueuedEvent;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
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

  private final WorkflowParserService parserService;
  private final WorkflowDefinitionRepository definitionRepository;
  private final WorkflowRunRepository runRepository;
  private final WorkflowJobRepository jobRepository;
  private final RepoRepository repoRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final MatrixExpander matrixExpander;
  private final ExpressionEvaluator expressionEvaluator;

  @Transactional
  public void triggerOnPush(
      final UUID repoId,
      final String branchName,
      final String pusherUsername,
      final @Nullable UUID actorId) {

    final var repo =
        this.repoRepository
            .findByIdWithOwner(repoId)
            .orElseGet(
                () -> {
                  log.warn("Push event for unknown repo id={}", repoId);
                  return null;
                });

    if (repo == null) {
      return;
    }

    final var ownerUsername = repo.getOwner().getUsername();
    final var repoName = repo.getName();
    final var branchRef = "refs/heads/" + branchName;

    final var workflows = this.parserService.parseWorkflows(ownerUsername, repoName, branchRef);

    if (workflows.isEmpty()) {
      log.debug("No workflows found for {}/{} branch={}", ownerUsername, repoName, branchName);
      return;
    }

    for (final var parsed : workflows) {
      if (!this.hasPushTrigger(parsed.model(), branchName)) {
        continue;
      }

      this.createRun(repoId, parsed, branchRef, null, actorId, "push", null);
    }
  }

  @Transactional
  public void triggerOnPullRequest(
      final UUID repoId, final String sourceBranch, final @Nullable UUID actorId) {

    final var repo =
        this.repoRepository
            .findByIdWithOwner(repoId)
            .orElseGet(
                () -> {
                  log.warn("PR event for unknown repo id={}", repoId);
                  return null;
                });

    if (repo == null) {
      return;
    }

    final var ownerUsername = repo.getOwner().getUsername();
    final var repoName = repo.getName();
    final var branchRef = "refs/heads/" + sourceBranch;

    final var workflows = this.parserService.parseWorkflows(ownerUsername, repoName, branchRef);

    for (final var parsed : workflows) {
      if (!this.hasPullRequestTrigger(parsed.model(), sourceBranch)) {
        continue;
      }

      this.createRun(repoId, parsed, branchRef, null, actorId, "pull_request", null);
    }
  }

  @Transactional
  public void triggerManual(
      final UUID repoId,
      final String workflowFilePath,
      final String ref,
      final @Nullable Map<String, String> inputs,
      final @Nullable UUID actorId) {

    final var repo =
        this.repoRepository
            .findByIdWithOwner(repoId)
            .orElseThrow(() -> new ItemNotFoundException("Repository not found: " + repoId));

    final var ownerUsername = repo.getOwner().getUsername();
    final var repoName = repo.getName();

    final var workflows = this.parserService.parseWorkflows(ownerUsername, repoName, ref);

    for (final var parsed : workflows) {
      if (!workflowFilePath.isBlank()
          && !parsed.filePath().endsWith(workflowFilePath)
          && !parsed.filePath().equals(workflowFilePath)) {
        continue;
      }
      final boolean hasDispatchTrigger =
          parsed.model().on() != null && parsed.model().on().workflowDispatch() != null;
      if (!hasDispatchTrigger) {
        continue;
      }
      this.createRun(repoId, parsed, ref, null, actorId, "workflow_dispatch", inputs);
      log.info("Manual dispatch queued: repo={} workflow={} ref={}", repoId, workflowFilePath, ref);
    }
  }

  private void createRun(
      final UUID repoId,
      final ParsedWorkflow parsed,
      final String triggerRef,
      final @Nullable String triggerSha,
      final @Nullable UUID actorId,
      final String triggerEvent,
      final @Nullable Map<String, String> inputs) {

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

    final var context = this.buildGithubContext(run);

    for (final Map.Entry<String, JobModel> entry : model.jobs().entrySet()) {
      final var jobKey = entry.getKey();
      final var jobDef = entry.getValue();
      final var baseName = jobDef.name() != null ? jobDef.name() : jobKey;
      final var combinations = this.matrixExpander.expand(jobDef);

      for (final var matrixValues : combinations) {
        this.createSingleJob(run, jobDef, baseName, matrixValues, context);
      }
    }
  }

  private void createSingleJob(
      final WorkflowRun run,
      final JobModel jobDef,
      final String baseName,
      final Map<String, String> matrixValues,
      final Map<String, String> context) {

    final var job = new WorkflowJob();
    job.setRunId(run.getId());
    job.setName(this.suffixMatrixName(baseName, matrixValues));
    job.setRunnerLabels(this.resolveLabels(jobDef));
    job.setNeeds(jobDef.needs());
    job.setMatrixValues(matrixValues.isEmpty() ? null : matrixValues);

    if (jobDef.condition() != null
        && !this.expressionEvaluator.evaluateCondition(jobDef.condition(), context, "success")) {
      job.setStatus(WorkflowJobStatus.SKIPPED);
      job.setConclusion("skipped");
      this.jobRepository.save(job);
      return;
    }

    final boolean hasUnmetNeeds = jobDef.needs() != null && !jobDef.needs().isEmpty();
    job.setStatus(hasUnmetNeeds ? WorkflowJobStatus.WAITING : WorkflowJobStatus.QUEUED);

    final var savedJob = this.jobRepository.save(job);

    if (!hasUnmetNeeds) {
      this.eventPublisher.publishEvent(
          new WorkflowJobQueuedEvent(
              savedJob.getId(),
              run.getId(),
              run.getRepoId(),
              savedJob.getName(),
              savedJob.getRunnerLabels()));
    }
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

  private List<String> resolveLabels(final JobModel job) {

    if (job.runsOn() != null && !job.runsOn().isEmpty()) {
      return job.runsOn();
    }

    return List.of("self-hosted");
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
      return true;
    }

    return trigger.branches().stream().anyMatch(p -> matchesGlob(branchName, p));
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
