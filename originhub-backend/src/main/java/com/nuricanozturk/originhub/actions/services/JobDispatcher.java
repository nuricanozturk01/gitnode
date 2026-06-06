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
import com.nuricanozturk.originhub.actions.model.WorkflowModel;
import com.nuricanozturk.originhub.actions.repositories.RunnerRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowDefinitionRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowJobRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowRunRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowStepRepository;
import com.nuricanozturk.originhub.actions.websocket.RunnerSessionRegistry;
import com.nuricanozturk.originhub.actions.websocket.ServerMessage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

  @Value("${originhub.actions.runner.heartbeat-timeout-seconds:60}")
  private int heartbeatTimeoutSeconds;

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
      final var steps = this.createStepsForJob(job);
      final var payload = this.buildJobPayload(job, steps);

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

  private List<WorkflowStep> createStepsForJob(final WorkflowJob job) {

    final var run = this.runRepository.findById(job.getRunId()).orElse(null);

    if (run == null || run.getWorkflowDefId() == null) {
      return Collections.emptyList();
    }

    final var definition = this.definitionRepository.findById(run.getWorkflowDefId()).orElse(null);

    if (definition == null) {
      return Collections.emptyList();
    }

    final var model = this.parserService.parseFromContent(definition.getContent());
    if (model == null || model.jobs() == null) {
      return Collections.emptyList();
    }

    final var jobModel = model.jobs().get(this.findJobKey(model, job.getName()));
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

  private JobPayload buildJobPayload(final WorkflowJob job, final List<WorkflowStep> steps) {

    final var run = this.runRepository.findById(job.getRunId()).orElse(null);

    final var stepPayloads = new ArrayList<StepPayload>();
    for (final var step : steps) {
      stepPayloads.add(
          new StepPayload(
              step.getId().toString(),
              step.getStepNumber(),
              step.getName(),
              step.getUses(),
              "", // run — needs full step model; enriched below
              Map.of(),
              Map.of(),
              ""));
    }

    // Enrich step payloads from the model (run/with/env/if).
    if (run != null && run.getWorkflowDefId() != null) {
      this.enrichStepPayloads(job, steps, stepPayloads);
    }

    return new JobPayload(
        job.getId().toString(),
        job.getRunId().toString(),
        job.getName(),
        job.getRunnerLabels(),
        stepPayloads,
        List.of(),
        run != null && run.getTriggerRef() != null
            ? Map.of(
                "ORIGINHUB_SHA",
                run.getTriggerSha() != null ? run.getTriggerSha() : "",
                "ORIGINHUB_REF",
                run.getTriggerRef())
            : Map.of(),
        Map.of(),
        Map.of(),
        job.getNeeds() != null ? job.getNeeds() : List.of(),
        30);
  }

  private void enrichStepPayloads(
      final WorkflowJob job, final List<WorkflowStep> steps, final List<StepPayload> payloads) {

    try {
      final var run = this.runRepository.findById(job.getRunId()).orElse(null);
      if (run == null || run.getWorkflowDefId() == null) {
        return;
      }
      final var def = this.definitionRepository.findById(run.getWorkflowDefId()).orElse(null);
      if (def == null) {
        return;
      }
      final var model = this.parserService.parseFromContent(def.getContent());
      if (model == null || model.jobs() == null) {
        return;
      }
      final var jobModel = model.jobs().get(this.findJobKey(model, job.getName()));
      if (jobModel == null || jobModel.steps() == null) {
        return;
      }
      final var stepModels = jobModel.steps();
      for (int i = 0; i < Math.min(steps.size(), stepModels.size()); i++) {
        final var sm = stepModels.get(i);
        final var old = payloads.get(i);
        payloads.set(
            i,
            new StepPayload(
                old.id(),
                old.number(),
                sm.name(),
                sm.uses(),
                sm.run() != null ? sm.run() : "",
                sm.with() != null ? sm.with() : Map.of(),
                sm.env() != null ? sm.env() : Map.of(),
                sm.condition() != null ? sm.condition() : ""));
      }
    } catch (final Exception ex) {
      log.warn("Failed to enrich step payloads for job {}", job.getId(), ex);
    }
  }

  private @Nullable String findJobKey(final WorkflowModel model, final String jobName) {
    if (model.jobs() == null) {
      return null;
    }
    for (final var entry : model.jobs().entrySet()) {
      final var jm = entry.getValue();
      final var name = jm.name() != null ? jm.name() : entry.getKey();
      if (name.equals(jobName) || entry.getKey().equals(jobName)) {
        return entry.getKey();
      }
    }
    return null;
  }

  private @Nullable Runner findMatchingRunner(
      final WorkflowJob job, final List<Runner> onlineRunners) {

    final var required = job.getRunnerLabels();

    for (final var runner : onlineRunners) {
      if (runner.getStatus() == RunnerStatus.ONLINE
          && runner.getLabels().containsAll(required)
          && this.sessionRegistry.isConnected(runner.getId())) {
        return runner;
      }
    }

    return null;
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
      List<Object> services,
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
