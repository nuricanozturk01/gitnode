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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import dev.gitnode.os.actions.entities.WorkflowRun;
import dev.gitnode.os.actions.repositories.RunLogRow;
import dev.gitnode.os.actions.repositories.WorkflowDefinitionRepository;
import dev.gitnode.os.actions.repositories.WorkflowJobRepository;
import dev.gitnode.os.actions.repositories.WorkflowLogRepository;
import dev.gitnode.os.actions.repositories.WorkflowRunRepository;
import dev.gitnode.os.actions.repositories.WorkflowStepRepository;
import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowRunQueryService: annotations and job summaries")
class WorkflowRunQueryServiceLogsTest {

  private static final UUID RUN_ID = UUID.randomUUID();
  private static final UUID JOB_A = UUID.randomUUID();
  private static final UUID JOB_B = UUID.randomUUID();
  private static final UUID STEP = UUID.randomUUID();

  @Mock private WorkflowRunRepository runRepository;
  @Mock private WorkflowJobRepository jobRepository;
  @Mock private WorkflowStepRepository stepRepository;
  @Mock private WorkflowLogRepository logRepository;
  @Mock private WorkflowDefinitionRepository definitionRepository;
  @Mock private TenantQueryPort tenantQuery;

  private WorkflowRunQueryService service;

  @BeforeEach
  void setUp() {
    this.service =
        new WorkflowRunQueryService(
            this.runRepository,
            this.jobRepository,
            this.stepRepository,
            this.logRepository,
            this.definitionRepository,
            this.tenantQuery);
  }

  private void runExists() {
    when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.of(new WorkflowRun()));
  }

  private static RunLogRow row(
      final UUID job, final String name, final String content, final String level) {
    return new RunLogRow(content, level, job, name, STEP, 2, 7);
  }

  @Test
  @DisplayName("annotations get their level from the prefix, which is stripped from the message")
  void annotationsLevelsAndMessages() {
    this.runExists();
    when(this.logRepository.findAnnotations(eq(RUN_ID), any(Pageable.class)))
        .thenReturn(
            List.of(
                row(JOB_A, "build", "##[error]a.js:3:5: boom", "error"),
                row(JOB_A, "build", "##[warning]careful", "warn"),
                row(JOB_B, "test", "##[notice]fyi", "notice")));

    final var result = this.service.annotations(RUN_ID);

    assertThat(result).extracting("level").containsExactly("error", "warning", "notice");
    assertThat(result).extracting("message").containsExactly("a.js:3:5: boom", "careful", "fyi");
    assertThat(result.getFirst().jobName()).isEqualTo("build");
    assertThat(result.getFirst().stepNumber()).isEqualTo(2);
  }

  @Test
  @DisplayName("annotations are capped like GitHub's")
  void annotationsAreCapped() {
    this.runExists();
    when(this.logRepository.findAnnotations(eq(RUN_ID), any(Pageable.class))).thenReturn(List.of());

    this.service.annotations(RUN_ID);

    org.mockito.Mockito.verify(this.logRepository)
        .findAnnotations(
            RUN_ID,
            org.springframework.data.domain.PageRequest.of(
                0, WorkflowRunQueryService.MAX_ANNOTATIONS));
  }

  @Test
  @DisplayName("summary lines are joined per job in log order")
  void summariesPerJob() {
    this.runExists();
    when(this.logRepository.findSummaryLines(RUN_ID))
        .thenReturn(
            List.of(
                row(JOB_A, "build", "## Title", "summary"),
                row(JOB_A, "build", "- item", "summary"),
                row(JOB_B, "test", "other", "summary")));

    final var result = this.service.summaries(RUN_ID);

    assertThat(result).hasSize(2);
    assertThat(result.getFirst().jobName()).isEqualTo("build");
    assertThat(result.getFirst().markdown()).isEqualTo("## Title\n- item\n");
    assertThat(result.get(1).markdown()).isEqualTo("other\n");
  }

  @Test
  @DisplayName("a run without summaries or annotations yields empty lists")
  void emptyResults() {
    this.runExists();
    when(this.logRepository.findSummaryLines(RUN_ID)).thenReturn(List.of());
    when(this.logRepository.findAnnotations(eq(RUN_ID), any(Pageable.class))).thenReturn(List.of());

    assertThat(this.service.summaries(RUN_ID)).isEmpty();
    assertThat(this.service.annotations(RUN_ID)).isEmpty();
  }

  @Test
  @DisplayName("an unknown run is not found")
  void unknownRun() {
    when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.empty());

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> this.service.annotations(RUN_ID))
        .isInstanceOf(ItemNotFoundException.class);
  }
}
