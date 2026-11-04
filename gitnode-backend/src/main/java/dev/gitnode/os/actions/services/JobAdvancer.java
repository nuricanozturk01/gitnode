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

import dev.gitnode.os.actions.entities.WorkflowJob;
import dev.gitnode.os.actions.entities.WorkflowJobStatus;
import dev.gitnode.os.actions.entities.WorkflowRun;
import dev.gitnode.os.actions.model.JobModel;
import dev.gitnode.os.actions.model.WorkflowModel;
import dev.gitnode.os.actions.repositories.WorkflowDefinitionRepository;
import dev.gitnode.os.repo.api.RepoLookupPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Decides when jobs that wait on other jobs may start, with GitHub's semantics: a job runs once
 * every job in its {@code needs} has finished, and only if its {@code if:} allows it. Without an
 * {@code if:} that means all needs succeeded; with one, {@code always()}, {@code failure()} and
 * {@code needs.<job>.result} can run a job after a failure or a skip.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class JobAdvancer {

  private final WorkflowDefinitionRepository definitionRepository;
  private final WorkflowParserService parserService;
  private final ExpressionEvaluator expressionEvaluator;
  private final RepoLookupPort repoLookup;

  /**
   * The parsed workflow a run was created from, or null when it is unavailable. The copy stored
   * with the run wins over the definition, which may have been edited since.
   */
  public @Nullable WorkflowModel loadModel(final WorkflowRun run) {
    if (run.getWorkflowContent() != null) {
      return this.parserService.parseFromContent(run.getWorkflowContent());
    }
    final var defId = run.getWorkflowDefId();
    if (defId == null) {
      return null;
    }
    return this.definitionRepository
        .findById(defId)
        .map(def -> this.parserService.parseFromContent(def.getContent()))
        .orElse(null);
  }

  /**
   * Moves every WAITING job whose needs have finished to QUEUED or SKIPPED, repeating until nothing
   * changes so that skipping a job cascades to its dependents. The jobs in {@code allJobs} are
   * updated in place; the jobs that changed are returned so the caller can persist them and publish
   * events for those that became QUEUED.
   */
  public List<WorkflowJob> advance(
      final WorkflowRun run, final List<WorkflowJob> allJobs, final @Nullable WorkflowModel model) {

    final var changed = new ArrayList<WorkflowJob>(this.failFast(allJobs, model));
    boolean progress;
    do {
      progress = false;
      for (final var job : allJobs) {
        if (job.getStatus() != WorkflowJobStatus.WAITING || !this.needsFinished(job, allJobs)) {
          continue;
        }
        if (this.shouldRun(run, job, this.jobDefinition(model, job), allJobs)) {
          job.setStatus(WorkflowJobStatus.QUEUED);
        } else {
          job.setStatus(WorkflowJobStatus.SKIPPED);
          job.setConclusion("skipped");
          job.setCompletedAt(Instant.now());
        }
        if (!changed.contains(job)) {
          changed.add(job);
        }
        progress = true;
      }
    } while (progress);
    return changed;
  }

  /**
   * strategy.fail-fast (default true): when one job of a matrix fails, the matrix jobs that have
   * not started yet are cancelled. Jobs that already run are left to finish.
   */
  private List<WorkflowJob> failFast(
      final List<WorkflowJob> allJobs, final @Nullable WorkflowModel model) {

    final var cancelled = new ArrayList<WorkflowJob>();
    for (final var failed : allJobs) {
      if (this.triggersFailFast(failed, model)) {
        cancelled.addAll(this.cancelNotStartedSiblings(failed, allJobs));
      }
    }
    return cancelled;
  }

  private boolean triggersFailFast(final WorkflowJob job, final @Nullable WorkflowModel model) {
    return job.getStatus() == WorkflowJobStatus.FAILURE
        && job.getMatrixValues() != null
        && job.getJobKey() != null
        && this.failsFast(model, job);
  }

  private List<WorkflowJob> cancelNotStartedSiblings(
      final WorkflowJob failed, final List<WorkflowJob> allJobs) {

    final var cancelled = new ArrayList<WorkflowJob>();
    for (final var sibling : allJobs) {
      if (failed.getJobKey().equals(sibling.getJobKey()) && this.notStarted(sibling)) {
        sibling.setStatus(WorkflowJobStatus.CANCELLED);
        sibling.setConclusion("cancelled");
        sibling.setCompletedAt(Instant.now());
        cancelled.add(sibling);
      }
    }
    return cancelled;
  }

  private boolean failsFast(final @Nullable WorkflowModel model, final WorkflowJob job) {
    final var def = this.jobDefinition(model, job);
    return def == null
        || def.strategy() == null
        || !Boolean.FALSE.equals(def.strategy().failFast());
  }

  private boolean notStarted(final WorkflowJob job) {
    return job.getStatus() == WorkflowJobStatus.QUEUED
        || job.getStatus() == WorkflowJobStatus.WAITING;
  }

  /** True when the job's condition (or the default success()) lets it run. */
  public boolean shouldRun(
      final WorkflowRun run,
      final WorkflowJob job,
      final @Nullable JobModel jobDef,
      final List<WorkflowJob> allJobs) {

    final var needs = job.getNeeds() == null ? List.<String>of() : job.getNeeds();
    final var results = new ArrayList<String>();
    final var context = this.baseContext(run, job);
    for (final String need : needs) {
      final var result = JobNeeds.aggregate(JobNeeds.jobsFor(allJobs, need));
      results.add(result);
      context.put("needs." + need + ".result", result);
      JobNeeds.outputsOf(JobNeeds.jobsFor(allJobs, need))
          .forEach((name, value) -> context.put("needs." + need + ".outputs." + name, value));
    }
    final String status = needs.isEmpty() ? JobNeeds.SUCCESS : JobNeeds.statusFor(results);

    final var condition = jobDef == null ? null : jobDef.condition();
    if (condition == null || condition.isBlank()) {
      return JobNeeds.SUCCESS.equals(status);
    }
    return this.expressionEvaluator.evaluateCondition(condition, context, status);
  }

  /**
   * The expression context for a job whose needs have finished: github.*, inputs.*, matrix.* and
   * needs.&lt;job&gt;.result / needs.&lt;job&gt;.outputs.&lt;name&gt;.
   */
  public Map<String, String> needsContext(
      final WorkflowRun run, final WorkflowJob job, final List<WorkflowJob> allJobs) {

    final var context = this.baseContext(run, job);
    final var needs = job.getNeeds() == null ? List.<String>of() : job.getNeeds();
    for (final String need : needs) {
      final var needed = JobNeeds.jobsFor(allJobs, need);
      context.put("needs." + need + ".result", JobNeeds.aggregate(needed));
      JobNeeds.outputsOf(needed)
          .forEach((name, value) -> context.put("needs." + need + ".outputs." + name, value));
    }
    return context;
  }

  /** True when every job this one needs has finished (a job without needs never "waits"). */
  public boolean needsFinished(final WorkflowJob job, final List<WorkflowJob> allJobs) {
    final var needs = job.getNeeds();
    if (needs == null || needs.isEmpty()) {
      return false; // not a job that waits on others
    }
    // A need that does not exist can never finish; treat it as finished (skipped).
    return needs.stream()
        .allMatch(need -> JobNeeds.jobsFor(allJobs, need).stream().allMatch(JobNeeds::isTerminal));
  }

  /** The definition of {@code job} in the parsed workflow, or null when it cannot be found. */
  public @Nullable JobModel jobDefinition(
      final @Nullable WorkflowModel model, final WorkflowJob job) {
    if (model == null || model.jobs() == null) {
      return null;
    }
    if (job.getJobKey() != null && model.jobs().containsKey(job.getJobKey())) {
      return model.jobs().get(job.getJobKey());
    }
    return model.jobs().get(job.getName());
  }

  @SuppressWarnings("checkstyle:CyclomaticComplexity") // one guarded entry per context key
  private Map<String, String> baseContext(final WorkflowRun run, final WorkflowJob job) {
    final var ctx = new LinkedHashMap<String, String>();
    if (run.getTriggerRef() != null) {
      ctx.put("github.ref", run.getTriggerRef());
    }
    if (run.getTriggerSha() != null) {
      ctx.put("github.sha", run.getTriggerSha());
    }
    if (run.getTriggerEvent() != null) {
      ctx.put("github.event_name", run.getTriggerEvent());
    }
    ctx.put("github.workflow", run.getWorkflowName());
    ctx.put("github.run_number", String.valueOf(run.getRunNumber()));
    if (job.getJobKey() != null) {
      ctx.put("github.job", job.getJobKey());
    }
    if (run.getInputs() != null) {
      run.getInputs().forEach((k, v) -> ctx.put("inputs." + k, v != null ? v : ""));
    }
    if (job.getMatrixValues() != null) {
      job.getMatrixValues().forEach((k, v) -> ctx.put("matrix." + k, v != null ? v : ""));
    }
    this.repoLookup
        .findById(run.getRepoId())
        .ifPresent(
            repo -> {
              ctx.put("github.repository", repo.ownerUsername() + "/" + repo.name());
              ctx.put("github.repository_owner", repo.ownerUsername());
            });
    return ctx;
  }
}
