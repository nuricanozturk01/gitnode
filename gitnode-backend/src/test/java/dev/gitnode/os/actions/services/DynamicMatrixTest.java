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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import dev.gitnode.os.actions.entities.WorkflowJob;
import dev.gitnode.os.actions.entities.WorkflowJobStatus;
import dev.gitnode.os.actions.entities.WorkflowRun;
import dev.gitnode.os.actions.model.WorkflowModel;
import dev.gitnode.os.actions.repositories.WorkflowDefinitionRepository;
import dev.gitnode.os.actions.repositories.WorkflowJobRepository;
import dev.gitnode.os.actions.repositories.WorkflowRunRepository;
import dev.gitnode.os.repo.api.RepoLookupPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("GitHub workflow syntax: job fields and dynamic matrices")
class DynamicMatrixTest {

  private static final String WORKFLOW =
      """
      name: demo
      on: workflow_dispatch
      jobs:
        plan:
          runs-on: [self-hosted, linux]
          outputs:
            matrix: ${{ steps.set.outputs.matrix }}
          continue-on-error: true
          container: node:20
          steps:
            - id: set
              run: echo hi
        dynamic:
          needs: plan
          runs-on: [self-hosted, linux]
          container:
            image: node:20
            env:
              A: b
            options: --cpus 1
          strategy:
            matrix: ${{ fromJSON(needs.plan.outputs.matrix) }}
          steps:
            - run: echo ${{ matrix.name }}
        static:
          runs-on: [self-hosted]
          strategy:
            matrix:
              node: [18, 20]
              ok: [true]
              include:
                - node: 22
                  extra: {a: 1}
          steps:
            - run: echo
      """;

  @Mock private WorkflowParserService parserService;
  @Mock private WorkflowDefinitionRepository definitionRepository;
  @Mock private WorkflowRunRepository runRepository;
  @Mock private WorkflowJobRepository jobRepository;
  @Mock private RepoLookupPort repoLookup;
  @Mock private ApplicationEventPublisher eventPublisher;

  private final ExpressionEvaluator evaluator = new ExpressionEvaluator();
  private WorkflowTriggerService triggerService;
  private WorkflowModel model;
  private final WorkflowRun run = new WorkflowRun();

  @BeforeEach
  void setUp() {
    this.triggerService =
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
                this.definitionRepository, this.parserService, this.evaluator, this.repoLookup));
    this.model = new WorkflowParserService().parseFromContent(WORKFLOW);
    this.run.setId(UUID.randomUUID());
    this.run.setRepoId(UUID.randomUUID());
    lenient()
        .when(this.jobRepository.save(any(WorkflowJob.class)))
        .thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  @DisplayName("job outputs, continue-on-error, container and matrix forms are parsed")
  void parsesJobFields() {
    final var plan = this.model.jobs().get("plan");
    assertThat(plan.outputs()).containsEntry("matrix", "${{ steps.set.outputs.matrix }}");
    assertThat(plan.continueOnError()).isEqualTo("true");
    assertThat(plan.container().image()).isEqualTo("node:20");

    final var container = this.model.jobs().get("dynamic").container();
    assertThat(container.image()).isEqualTo("node:20");
    assertThat(container.env()).containsEntry("A", "b");
    assertThat(container.options()).isEqualTo("--cpus 1");

    final var dynamic = this.model.jobs().get("dynamic").strategy().matrix();
    assertThat(dynamic.isDynamic()).isTrue();
    assertThat(dynamic.expression()).contains("fromJSON");
  }

  @Test
  @DisplayName("a matrix may hold numbers, booleans and nested objects")
  void parsesMatrixScalars() {
    final var matrix = this.model.jobs().get("static").strategy().matrix();
    assertThat(matrix.isDynamic()).isFalse();
    assertThat(matrix.dimensions()).containsEntry("node", List.of("18", "20"));
    assertThat(matrix.dimensions()).containsEntry("ok", List.of("true"));
    assertThat(matrix.include().getFirst()).containsEntry("node", "22");
    assertThat(matrix.include().getFirst().get("extra")).contains("\"a\":1");
  }

  @Test
  @DisplayName("a dynamic matrix becomes one job per combination once its needs are done")
  void expandsDynamicMatrix() {
    final var plan = job("plan", WorkflowJobStatus.SUCCESS, null);
    plan.setOutputs(
        Map.of("matrix", "{\"include\":[{\"name\":\"one\",\"n\":1},{\"name\":\"two\",\"n\":2}]}"));
    plan.setCompletedAt(Instant.now());
    final var placeholder = job("dynamic", WorkflowJobStatus.WAITING, List.of("plan"));
    placeholder.setMatrixExpression("${{ fromJSON(needs.plan.outputs.matrix) }}");

    final var created =
        this.triggerService.expandDynamicMatrix(
            this.run, placeholder, List.of(plan, placeholder), this.model);

    assertThat(created).hasSize(2);
    assertThat(created)
        .extracting(WorkflowJob::getMatrixValues)
        .containsExactly(Map.of("name", "one", "n", "1"), Map.of("name", "two", "n", "2"));
    assertThat(created).allSatisfy(j -> assertThat(j.getJobKey()).isEqualTo("dynamic"));
    assertThat(created)
        .allSatisfy(j -> assertThat(j.getStatus()).isEqualTo(WorkflowJobStatus.WAITING));
    verify(this.jobRepository).delete(placeholder);
  }

  @Test
  @DisplayName("a matrix that is not valid JSON fails the job instead of dropping it")
  void failsPlaceholderOnBadMatrix() {
    final var plan = job("plan", WorkflowJobStatus.SUCCESS, null);
    plan.setOutputs(Map.of("matrix", "not json"));
    final var placeholder = job("dynamic", WorkflowJobStatus.WAITING, List.of("plan"));
    placeholder.setMatrixExpression("${{ fromJSON(needs.plan.outputs.matrix) }}");

    final var created =
        this.triggerService.expandDynamicMatrix(
            this.run, placeholder, List.of(plan, placeholder), this.model);

    assertThat(created).containsExactly(placeholder);
    assertThat(placeholder.getStatus()).isEqualTo(WorkflowJobStatus.FAILURE);
    assertThat(placeholder.getMatrixExpression()).isNull();
  }

  @Test
  @DisplayName("needs.<job>.outputs merges matrix jobs, the last non-empty value wins")
  void mergesOutputsOfMatrixJobs() {
    final var first = job("build", WorkflowJobStatus.SUCCESS, null);
    first.setOutputs(Map.of("v", "1", "w", "keep"));
    first.setCompletedAt(Instant.parse("2026-01-01T00:00:00Z"));
    final var second = job("build", WorkflowJobStatus.SUCCESS, null);
    second.setOutputs(Map.of("v", "2", "w", ""));
    second.setCompletedAt(Instant.parse("2026-01-01T00:00:05Z"));

    assertThat(JobNeeds.outputsOf(List.of(second, first)))
        .containsEntry("v", "2")
        .containsEntry("w", "keep");
    assertThat(JobNeeds.outputsOf(List.of(job("x", WorkflowJobStatus.SUCCESS, null)))).isEmpty();
  }

  private WorkflowJob job(
      final String key, final WorkflowJobStatus status, final List<String> needs) {
    final var job = new WorkflowJob();
    job.setId(UUID.randomUUID());
    job.setRunId(this.run.getId());
    job.setJobKey(key);
    job.setName(key);
    job.setRunnerLabels(List.of("self-hosted", "linux"));
    job.setStatus(status);
    job.setNeeds(needs);
    return job;
  }
}
