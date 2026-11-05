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
import static org.mockito.Mockito.mock;

import dev.gitnode.os.actions.entities.WorkflowJob;
import dev.gitnode.os.actions.entities.WorkflowJobStatus;
import dev.gitnode.os.actions.entities.WorkflowRun;
import dev.gitnode.os.actions.model.WorkflowModel;
import dev.gitnode.os.actions.repositories.WorkflowDefinitionRepository;
import dev.gitnode.os.repo.api.RepoLookupPort;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The e2e workflow: dispatch inputs with defaults, and job conditions on github.event.inputs. */
@DisplayName("workflow_dispatch inputs and github.event in job conditions")
class DispatchInputsTest {

  private static final String WORKFLOW =
      """
      name: E2E
      on:
        schedule:
          - cron: "00 16 * * *"
        workflow_dispatch:
          inputs:
            suite:
              description: E2E suite to run
              type: choice
              default: full
              options: [full, api, scenario, teardown]
            note:
              type: string
      jobs:
        e2e-api:
          if: >-
            github.event_name != 'workflow_dispatch' ||
            github.event.inputs.suite == 'full' ||
            github.event.inputs.suite == 'api'
          runs-on: self-hosted
          steps:
            - run: echo api
        e2e-scenario:
          needs: e2e-api
          if: >-
            always() &&
            (needs.e2e-api.result == 'success' || needs.e2e-api.result == 'skipped') &&
            (github.event_name != 'workflow_dispatch' ||
              github.event.inputs.suite == 'full' ||
              github.event.inputs.suite == 'scenario')
          runs-on: self-hosted
          steps:
            - run: echo scenario
      """;

  private final WorkflowModel model = new WorkflowParserService().parseFromContent(WORKFLOW);
  private final JobAdvancer advancer =
      new JobAdvancer(
          mock(WorkflowDefinitionRepository.class),
          new WorkflowParserService(),
          new ExpressionEvaluator(),
          mock(RepoLookupPort.class));

  private static WorkflowRun run(final String event, final Map<String, String> inputs) {
    final var run = new WorkflowRun();
    run.setId(UUID.randomUUID());
    run.setRepoId(UUID.randomUUID());
    run.setWorkflowName("E2E");
    run.setTriggerEvent(event);
    run.setTriggerRef("refs/heads/main");
    run.setInputs(inputs);
    final var payload = new HashMap<String, Object>();
    payload.put("ref", "refs/heads/main");
    if (inputs != null) {
      payload.put("inputs", inputs);
    }
    run.setEventPayload(payload);
    return run;
  }

  private static WorkflowJob job(final String key, final List<String> needs) {
    final var job = new WorkflowJob();
    job.setId(UUID.randomUUID());
    job.setJobKey(key);
    job.setName(key);
    job.setNeeds(needs);
    job.setStatus(WorkflowJobStatus.QUEUED);
    return job;
  }

  private boolean runs(final WorkflowRun run, final String key, final String apiResult) {
    final var api = job("e2e-api", null);
    if (apiResult != null) {
      api.setStatus(WorkflowJobStatus.valueOf(apiResult));
      api.setConclusion(apiResult.toLowerCase(java.util.Locale.ROOT));
    }
    final var scenario = job("e2e-scenario", List.of("e2e-api"));
    final var target = "e2e-api".equals(key) ? api : scenario;
    return this.advancer.shouldRun(run, target, this.model.jobs().get(key), List.of(api, scenario));
  }

  @Test
  @DisplayName("an omitted input takes the workflow's default; a given one is kept")
  void defaultsAreApplied() {
    assertThat(WorkflowTriggerService.withDispatchDefaults(this.model, null))
        .containsEntry("suite", "full");
    assertThat(WorkflowTriggerService.withDispatchDefaults(this.model, Map.of("suite", "api")))
        .containsEntry("suite", "api");
    assertThat(WorkflowTriggerService.withDispatchDefaults(this.model, Map.of("suite", "")))
        .containsEntry("suite", "full");
    assertThat(WorkflowTriggerService.withDispatchDefaults(this.model, Map.of("note", "hi")))
        .containsEntry("note", "hi")
        .containsEntry("suite", "full");
  }

  @Test
  @DisplayName("a choice without default falls back to its first option")
  void choiceFallsBackToFirstOption() {
    final var m =
        new WorkflowParserService()
            .parseFromContent(
                "name: x\non:\n  workflow_dispatch:\n    inputs:\n      env:\n        type: choice\n        options: [staging, prod]\njobs: {}\n");
    assertThat(WorkflowTriggerService.withDispatchDefaults(m, null))
        .containsEntry("env", "staging");
  }

  @Test
  @DisplayName("github.event.inputs.* is available to job conditions (suite=full runs the API job)")
  void fullSuiteRunsEveryJob() {
    final var run = run("workflow_dispatch", Map.of("suite", "full"));

    assertThat(this.runs(run, "e2e-api", null)).isTrue();
    assertThat(this.runs(run, "e2e-scenario", "SUCCESS")).isTrue();
  }

  @Test
  @DisplayName(
      "suite=api runs only the API job; suite=scenario skips the API job and runs the scenario")
  void singleSuites() {
    final var apiOnly = run("workflow_dispatch", Map.of("suite", "api"));
    assertThat(this.runs(apiOnly, "e2e-api", null)).isTrue();
    assertThat(this.runs(apiOnly, "e2e-scenario", "SUCCESS")).isFalse();

    final var scenarioOnly = run("workflow_dispatch", Map.of("suite", "scenario"));
    assertThat(this.runs(scenarioOnly, "e2e-api", null)).isFalse();
    assertThat(this.runs(scenarioOnly, "e2e-scenario", "SKIPPED")).isTrue();
  }

  @Test
  @DisplayName("a scheduled run is not a dispatch, so every job runs")
  void scheduledRunRunsEverything() {
    final var run = run("schedule", null);

    assertThat(this.runs(run, "e2e-api", null)).isTrue();
    assertThat(this.runs(run, "e2e-scenario", "SUCCESS")).isTrue();
  }

  @Test
  @DisplayName("eventContext flattens nested values and leaves lists out")
  void eventContextFlattens() {
    final var event = new HashMap<String, Object>();
    event.put("action", "opened");
    event.put("inputs", Map.of("suite", "api"));
    event.put("pull_request", Map.of("head", Map.of("ref", "feature/x"), "labels", List.of("a")));

    assertThat(EventPayloads.eventContext(event))
        .containsEntry("github.event.action", "opened")
        .containsEntry("github.event.inputs.suite", "api")
        .containsEntry("github.event.pull_request.head.ref", "feature/x")
        .doesNotContainKey("github.event.pull_request.labels");
    assertThat(EventPayloads.eventContext(null)).isEmpty();
  }
}
