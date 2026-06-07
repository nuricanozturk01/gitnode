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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.actions.entities.ExecutorType;
import com.nuricanozturk.originhub.actions.entities.Runner;
import com.nuricanozturk.originhub.actions.entities.RunnerStatus;
import com.nuricanozturk.originhub.actions.entities.WorkflowRun;
import com.nuricanozturk.originhub.actions.entities.WorkflowRunStatus;
import com.nuricanozturk.originhub.actions.repositories.RunnerRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowRunRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActionsAdminService unit tests")
class ActionsAdminServiceTest {

  @Mock private RunnerRepository runnerRepository;
  @Mock private WorkflowRunRepository workflowRunRepository;

  @InjectMocks private ActionsAdminService service;

  @Test
  @DisplayName("listAllRunners returns correctly mapped data for all runners")
  void listAllRunners_returnsAllMapped() {
    final var r1 = runner("runner-a", RunnerStatus.ONLINE, "linux", "x64");
    final var r2 = runner("runner-b", RunnerStatus.BUSY, "windows", "x86");

    when(runnerRepository.findAll()).thenReturn(List.of(r1, r2));

    final var result = service.listAllRunners();

    assertThat(result).hasSize(2);
    assertThat(result).extracting("name").containsExactly("runner-a", "runner-b");
    assertThat(result.get(0).status()).isEqualTo(RunnerStatus.ONLINE.name());
    assertThat(result.get(1).status()).isEqualTo(RunnerStatus.BUSY.name());
    assertThat(result.get(0).os()).isEqualTo("linux");
    assertThat(result.get(1).arch()).isEqualTo("x86");
  }

  @Test
  @DisplayName("listAllRunners returns empty list when no runners exist")
  void listAllRunners_emptyWhenNoRunners() {
    when(runnerRepository.findAll()).thenReturn(List.of());

    final var result = service.listAllRunners();

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("getRunStats counts runs and runners by status correctly")
  void getRunStats_countsCorrectly() {
    // 5 runs: 2 SUCCESS, 1 FAILURE, 1 CANCELLED, 1 IN_PROGRESS
    final var runs =
        List.of(
            workflowRun(WorkflowRunStatus.SUCCESS),
            workflowRun(WorkflowRunStatus.SUCCESS),
            workflowRun(WorkflowRunStatus.FAILURE),
            workflowRun(WorkflowRunStatus.CANCELLED),
            workflowRun(WorkflowRunStatus.IN_PROGRESS));

    // 3 runners: 1 ONLINE, 1 OFFLINE, 1 BUSY
    final var runners =
        List.of(
            runner("r1", RunnerStatus.ONLINE, "linux", "x64"),
            runner("r2", RunnerStatus.OFFLINE, "linux", "x64"),
            runner("r3", RunnerStatus.BUSY, "linux", "arm64"));

    when(workflowRunRepository.findAll()).thenReturn(runs);
    when(runnerRepository.findAll()).thenReturn(runners);

    final var stats = service.getRunStats();

    assertThat(stats.totalRuns()).isEqualTo(5L);
    assertThat(stats.successRuns()).isEqualTo(2L);
    assertThat(stats.failureRuns()).isEqualTo(1L);
    assertThat(stats.cancelledRuns()).isEqualTo(1L);
    assertThat(stats.inProgressRuns()).isEqualTo(1L);
    assertThat(stats.totalRunners()).isEqualTo(3L);
    assertThat(stats.onlineRunners()).isEqualTo(1L);
    assertThat(stats.busyRunners()).isEqualTo(1L);
  }

  @Test
  @DisplayName("getRunStats returns all zeros when repositories are empty")
  void getRunStats_emptyReturnsZeros() {
    when(workflowRunRepository.findAll()).thenReturn(List.of());
    when(runnerRepository.findAll()).thenReturn(List.of());

    final var stats = service.getRunStats();

    assertThat(stats.totalRuns()).isZero();
    assertThat(stats.successRuns()).isZero();
    assertThat(stats.failureRuns()).isZero();
    assertThat(stats.cancelledRuns()).isZero();
    assertThat(stats.inProgressRuns()).isZero();
    assertThat(stats.totalRunners()).isZero();
    assertThat(stats.onlineRunners()).isZero();
    assertThat(stats.busyRunners()).isZero();
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private Runner runner(
      final String name, final RunnerStatus status, final String os, final String arch) {
    final var r = new Runner();
    r.setId(UUID.randomUUID());
    r.setName(name);
    r.setStatus(status);
    r.setOs(os);
    r.setArch(arch);
    r.setLabels(List.of());
    r.setExecutorType(ExecutorType.SHELL);
    r.setCreatedAt(Instant.now());
    return r;
  }

  private WorkflowRun workflowRun(final WorkflowRunStatus status) {
    final var run = new WorkflowRun();
    run.setId(UUID.randomUUID());
    run.setStatus(status);
    return run;
  }
}
