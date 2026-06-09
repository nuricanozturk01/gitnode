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
import static org.mockito.Mockito.when;

import dev.gitnode.os.actions.entities.ExecutorType;
import dev.gitnode.os.actions.entities.Runner;
import dev.gitnode.os.actions.entities.RunnerStatus;
import dev.gitnode.os.actions.entities.WorkflowRunStatus;
import dev.gitnode.os.actions.repositories.RunnerRepository;
import dev.gitnode.os.actions.repositories.WorkflowRunRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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

    when(runnerRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(r1, r2)));

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
    when(runnerRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

    final var result = service.listAllRunners();

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("getRunStats counts runs and runners by status correctly")
  void getRunStats_countsCorrectly() {
    when(workflowRunRepository.count()).thenReturn(5L);
    when(workflowRunRepository.countByStatus(WorkflowRunStatus.SUCCESS)).thenReturn(2L);
    when(workflowRunRepository.countByStatus(WorkflowRunStatus.FAILURE)).thenReturn(1L);
    when(workflowRunRepository.countByStatus(WorkflowRunStatus.CANCELLED)).thenReturn(1L);
    when(workflowRunRepository.countByStatus(WorkflowRunStatus.IN_PROGRESS)).thenReturn(1L);
    when(runnerRepository.count()).thenReturn(3L);
    when(runnerRepository.countByStatus(RunnerStatus.ONLINE)).thenReturn(1L);
    when(runnerRepository.countByStatus(RunnerStatus.BUSY)).thenReturn(1L);

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
    when(workflowRunRepository.count()).thenReturn(0L);
    when(workflowRunRepository.countByStatus(any(WorkflowRunStatus.class))).thenReturn(0L);
    when(runnerRepository.count()).thenReturn(0L);
    when(runnerRepository.countByStatus(any(RunnerStatus.class))).thenReturn(0L);

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
}
