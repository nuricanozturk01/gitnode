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

import com.nuricanozturk.originhub.actions.entities.Runner;
import com.nuricanozturk.originhub.actions.entities.RunnerStatus;
import com.nuricanozturk.originhub.actions.entities.WorkflowJob;
import com.nuricanozturk.originhub.actions.entities.WorkflowStep;
import com.nuricanozturk.originhub.actions.model.JobModel;
import com.nuricanozturk.originhub.actions.model.ServiceModel;
import com.nuricanozturk.originhub.actions.model.WorkflowModel;
import com.nuricanozturk.originhub.actions.repositories.RunnerRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowDefinitionRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowJobRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowRunRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowStepRepository;
import com.nuricanozturk.originhub.actions.websocket.RunnerSessionRegistry;
import com.nuricanozturk.originhub.actions.websocket.ServerMessage;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class JobDispatcher {

  private final WorkflowJobRepository jobRepository;
  private final WorkflowRunRepository runRepository;
  private final WorkflowDefinitionRepository definitionRepository;
  private final WorkflowStepRepository stepRepository;
  private final RunnerRepository runnerRepository;
  private final RunnerSessionRegistry sessionRegistry;
  private final WorkflowParserService parserService;
  private final RepoRepository repoRepository;
  private final ExpressionEvaluator expressionEvaluator;

  @Value("${originhub.actions.runner.heartbeat-timeout-seconds:60}")
  private int heartbeatTimeoutSeconds;

  private record CachedWorkflow(WorkflowModel model, Instant cachedAt) {}

  private static final Duration YAML_CACHE_TTL = Duration.ofMinutes(5);
  private static final int DEFAULT_JOB_TIMEOUT_MINUTES = 30;

  private final ConcurrentHashMap<UUID, CachedWorkflow> yamlCache = new ConcurrentHashMap<>();

  @Scheduled(fixedDelayString = "${originhub.actions.dispatch.interval-ms:2000}")
  @Transactional
  public void dispatch() {

    this.markStaleRunnersOffline();

    final var queuedJobs = this.jobRepository.findAllQueued();
    if (queuedJobs.isEmpty()) {
      return;
    }

    final var onlineRunners = this.runnerRepository.findAllByStatus(RunnerStatus.ONLINE);
    if (onlineRunners.isEmpty()) {
      return;
    }

    for (final var job : queuedJobs) {
      final var runner = this.findMatchingRunner(job, onlineRunners);
      if (runner == null) {
        continue;
      }

      if (!this.sessionRegistry.isConnected(runner.getId())) {
        continue;
      }

      this.assignJob(job, runner);
    }
  }

  private void assignJob(final WorkflowJob job, final Runner runner) {

    try {
      final var ctx = this.resolveJobContext(job);
      final var steps =
          ctx != null
              ? this.createSteps(job, ctx.jobModel())
              : Collections.<WorkflowStep>emptyList();
      final var payload = this.buildJobPayload(job, steps, ctx);

      this.sessionRegistry
          .get(runner.getId())
          .ifPresent(session -> session.send(ServerMessage.jobAssigned(payload)));

      job.setRunnerId(runner.getId());
      runner.setStatus(RunnerStatus.BUSY);
      this.runnerRepository.save(runner);

      log.info(
          "Job dispatched: jobId={}, runnerId={}, steps={}",
          job.getId(),
          runner.getId(),
          steps.size());

    } catch (final Exception ex) {
      log.error("Failed to dispatch job {}", job.getId(), ex);
    }
  }

  public void invalidateYamlCache(final UUID workflowDefId) {
    this.yamlCache.remove(workflowDefId);
  }

  private @Nullable JobContext resolveJobContext(final WorkflowJob job) {

    final var run = this.runRepository.findById(job.getRunId()).orElse(null);
    if (run == null || run.getWorkflowDefId() == null) {
      return null;
    }
    final var defId = run.getWorkflowDefId();
    final var model = this.resolveWorkflowModel(defId);
    if (model == null || model.jobs() == null) {
      return null;
    }
    final var jobModel = model.jobs().get(this.findJobKey(model, job.getName()));
    return new JobContext(run, jobModel);
  }

  private @Nullable WorkflowModel resolveWorkflowModel(final UUID defId) {
    final var cached = this.yamlCache.get(defId);
    if (cached != null
        && Duration.between(cached.cachedAt(), Instant.now()).compareTo(YAML_CACHE_TTL) < 0) {
      return cached.model();
    }
    final var def = this.definitionRepository.findById(defId).orElse(null);
    if (def == null) {
      return null;
    }
    final var model = this.parserService.parseFromContent(def.getContent());
    if (model != null) {
      this.yamlCache.put(defId, new CachedWorkflow(model, Instant.now()));
    }
    return model;
  }

  private List<WorkflowStep> createSteps(final WorkflowJob job, final @Nullable JobModel jobModel) {

    if (jobModel == null || jobModel.steps() == null) {
      return Collections.emptyList();
    }

    final var created = new ArrayList<WorkflowStep>();
    final var stepModels = jobModel.steps();
    for (int i = 0; i < stepModels.size(); i++) {
      final var stepModel = stepModels.get(i);
      final var step = new WorkflowStep();
      step.setJobId(job.getId());
      step.setStepNumber(i + 1);
      step.setName(stepModel.name());
      step.setUses(stepModel.uses());
      created.add(this.stepRepository.save(step));
    }
    return created;
  }

  private JobPayload buildJobPayload(
      final WorkflowJob job, final List<WorkflowStep> steps, final @Nullable JobContext ctx) {

    final var run = java.util.Optional.ofNullable(ctx).map(JobContext::run).orElse(null);
    final var jobModel = java.util.Optional.ofNullable(ctx).map(JobContext::jobModel).orElse(null);
    final List<com.nuricanozturk.originhub.actions.model.StepModel> stepModels =
        java.util.Optional.ofNullable(jobModel)
            .map(com.nuricanozturk.originhub.actions.model.JobModel::steps)
            .orElse(List.of());

    final var exprCtx = buildExpressionContext(run, job);
    final var stepPayloads = this.buildStepPayloads(steps, stepModels, exprCtx);
    final var services =
        buildServicesPayload(
            java.util.Optional.ofNullable(jobModel)
                .map(com.nuricanozturk.originhub.actions.model.JobModel::services)
                .orElse(null));
    final var env =
        java.util.Optional.ofNullable(run)
            .map(this::buildEnvMap)
            .orElseGet(java.util.LinkedHashMap::new);

    return new JobPayload(
        job.getId().toString(),
        job.getRunId().toString(),
        job.getName(),
        job.getRunnerLabels(),
        stepPayloads,
        services,
        env,
        Map.of(),
        java.util.Optional.ofNullable(job.getMatrixValues()).orElse(Map.of()),
        java.util.Optional.ofNullable(job.getNeeds()).orElse(List.of()),
        DEFAULT_JOB_TIMEOUT_MINUTES);
  }

  private java.util.LinkedHashMap<String, String> buildEnvMap(
      final com.nuricanozturk.originhub.actions.entities.WorkflowRun run) {

    final var env = new java.util.LinkedHashMap<String, String>();
    this.repoRepository
        .findByIdWithOwner(run.getRepoId())
        .ifPresent(
            repo -> {
              env.put("ORIGINHUB_OWNER", repo.getOwner().getUsername());
              env.put("ORIGINHUB_REPO", repo.getName());
            });
    if (run.getTriggerSha() != null) {
      env.put("ORIGINHUB_SHA", run.getTriggerSha());
    }
    if (run.getTriggerRef() != null) {
      env.put("ORIGINHUB_REF", run.getTriggerRef());
    }
    return env;
  }

  private List<StepPayload> buildStepPayloads(
      final List<WorkflowStep> steps,
      final List<com.nuricanozturk.originhub.actions.model.StepModel> stepModels,
      final Map<String, String> exprCtx) {

    final var stepPayloads = new ArrayList<StepPayload>();
    for (int i = 0; i < steps.size(); i++) {
      final var step = steps.get(i);
      if (i < stepModels.size()) {
        final var sm = stepModels.get(i);
        final var rawRun = sm.run() != null ? sm.run() : "";
        final var rawCond = sm.condition() != null ? sm.condition() : "";
        final var withValues =
            sm.with() != null ? this.interpolateMap(sm.with(), exprCtx) : Map.<String, String>of();
        final var envValues =
            sm.env() != null ? this.interpolateMap(sm.env(), exprCtx) : Map.<String, String>of();
        stepPayloads.add(
            new StepPayload(
                step.getId().toString(),
                step.getStepNumber(),
                sm.name(),
                sm.uses(),
                this.expressionEvaluator.evaluate(rawRun, exprCtx),
                withValues,
                envValues,
                this.expressionEvaluator.evaluate(rawCond, exprCtx)));
      } else {
        stepPayloads.add(
            new StepPayload(
                step.getId().toString(),
                step.getStepNumber(),
                step.getName(),
                step.getUses(),
                "",
                Map.of(),
                Map.of(),
                ""));
      }
    }
    return stepPayloads;
  }

  private static Map<String, String> buildExpressionContext(
      final com.nuricanozturk.originhub.actions.entities.@Nullable WorkflowRun run,
      final WorkflowJob job) {

    final var ctx = new java.util.LinkedHashMap<String, String>();

    if (run != null) {
      addRunContextEntries(ctx, run);
    }

    final var matrix = job.getMatrixValues();
    if (matrix != null) {
      matrix.forEach((k, v) -> ctx.put("matrix." + k, v != null ? v : ""));
    }

    return java.util.Collections.unmodifiableMap(ctx);
  }

  private static void addRunContextEntries(
      final java.util.LinkedHashMap<String, String> ctx,
      final com.nuricanozturk.originhub.actions.entities.WorkflowRun run) {

    if (run.getTriggerSha() != null) {
      ctx.put("github.sha", run.getTriggerSha());
    }
    if (run.getTriggerRef() != null) {
      ctx.put("github.ref", run.getTriggerRef());
    }
    if (run.getTriggerEvent() != null) {
      ctx.put("github.event_name", run.getTriggerEvent());
    }
    final var inputs = run.getInputs();
    if (inputs != null) {
      inputs.forEach((k, v) -> ctx.put("inputs." + k, v != null ? v : ""));
    }
  }

  private Map<String, String> interpolateMap(
      final Map<String, String> raw, final Map<String, String> ctx) {

    final var result = new java.util.LinkedHashMap<String, String>(raw.size());
    raw.forEach(
        (k, v) -> result.put(k, v != null ? this.expressionEvaluator.evaluate(v, ctx) : ""));
    return java.util.Collections.unmodifiableMap(result);
  }

  private static List<Map<String, Object>> buildServicesPayload(
      final @Nullable Map<String, ServiceModel> services) {

    if (services == null || services.isEmpty()) {
      return List.of();
    }

    final var result = new ArrayList<Map<String, Object>>();
    for (final var entry : services.entrySet()) {
      final var service = entry.getValue();
      final var payload = new java.util.LinkedHashMap<String, Object>();
      payload.put("name", entry.getKey());
      payload.put("image", service.image());
      payload.put("env", service.env() != null ? service.env() : Map.of());
      payload.put("ports", service.ports() != null ? service.ports() : List.of());
      result.add(payload);
    }
    return result;
  }

  private record JobContext(
      com.nuricanozturk.originhub.actions.entities.WorkflowRun run, @Nullable JobModel jobModel) {}

  private @Nullable String findJobKey(final WorkflowModel model, final String jobName) {
    if (model.jobs() == null) {
      return null;
    }
    final var baseName = stripMatrixSuffix(jobName);
    for (final var entry : model.jobs().entrySet()) {
      if (jobKeyMatches(entry.getKey(), entry.getValue(), jobName, baseName)) {
        return entry.getKey();
      }
    }
    return null;
  }

  private static boolean jobKeyMatches(
      final String key, final JobModel jm, final String jobName, final String baseName) {
    final var name = jm.name() != null ? jm.name() : key;
    return name.equals(jobName)
        || key.equals(jobName)
        || name.equals(baseName)
        || key.equals(baseName);
  }

  private static String stripMatrixSuffix(final String jobName) {
    final var idx = jobName.lastIndexOf(" (");
    return idx > 0 && jobName.endsWith(")") ? jobName.substring(0, idx) : jobName;
  }

  private @Nullable Runner findMatchingRunner(
      final WorkflowJob job, final List<Runner> onlineRunners) {

    final var required = job.getRunnerLabels();
    for (final var runner : onlineRunners) {
      if (this.runnerMatches(runner, required)) {
        return runner;
      }
    }
    return null;
  }

  private boolean runnerMatches(final Runner runner, final List<String> required) {
    return runner.getStatus() == RunnerStatus.ONLINE
        && runner.getLabels().containsAll(required)
        && this.sessionRegistry.isConnected(runner.getId());
  }

  private void markStaleRunnersOffline() {
    final var threshold = Instant.now().minus(this.heartbeatTimeoutSeconds, ChronoUnit.SECONDS);
    final int count = this.runnerRepository.markStaleRunnersOffline(threshold);
    if (count > 0) {
      log.info("Marked {} stale runner(s) offline", count);
    }
  }

  // ── payload records ───────────────────────────────────────────────────────

  public record JobPayload(
      String id,
      String runId,
      String name,
      List<String> runnerLabels,
      List<StepPayload> steps,
      List<Map<String, Object>> services,
      Map<String, String> env,
      Map<String, String> secrets,
      Map<String, String> matrixValues,
      List<String> needs,
      int timeoutMinutes) {}

  public record StepPayload(
      String id,
      int number,
      @Nullable String name,
      @Nullable String uses,
      String run,
      Map<String, String> with,
      Map<String, String> env,
      String condition) {}
}
