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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.actions.entities.WorkflowJob;
import dev.gitnode.os.actions.entities.WorkflowJobStatus;
import dev.gitnode.os.actions.entities.WorkflowStep;
import dev.gitnode.os.actions.repositories.WorkflowJobRepository;
import dev.gitnode.os.actions.repositories.WorkflowStepRepository;
import dev.gitnode.os.actions.websocket.SseEmitterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobCancellation")
class JobCancellationTest {

  @Mock private WorkflowJobRepository jobRepository;
  @Mock private WorkflowStepRepository stepRepository;
  @Mock private SseEmitterRegistry sseEmitterRegistry;
  @Mock private RunnerCommands runnerCommands;

  private JobCancellation cancellation;

  @BeforeEach
  void setUp() {
    this.cancellation =
        new JobCancellation(
            this.jobRepository, this.stepRepository, this.sseEmitterRegistry, this.runnerCommands);
  }

  private static WorkflowJob job(final WorkflowJobStatus status, final UUID runnerId) {
    final var job = new WorkflowJob();
    job.setId(UUID.randomUUID());
    job.setStatus(status);
    job.setRunnerId(runnerId);
    return job;
  }

  @Test
  @DisplayName("a job running on a runner is cancelled, its steps closed and the runner told")
  void cancel_runningJob_notifiesRunner() {
    final var runnerId = UUID.randomUUID();
    final var job = job(WorkflowJobStatus.IN_PROGRESS, runnerId);
    final var step = new WorkflowStep();
    step.setId(UUID.randomUUID());
    step.setStatus("running");
    when(this.stepRepository.findAllByJobIdOrderByStepNumberAsc(job.getId()))
        .thenReturn(List.of(step));

    this.cancellation.cancel(job);

    assertThat(job.getStatus()).isEqualTo(WorkflowJobStatus.CANCELLED);
    assertThat(job.getCompletedAt()).isNotNull();
    assertThat(step.getStatus()).isEqualTo("completed");
    assertThat(step.getConclusion()).isEqualTo("cancelled");
    verify(this.runnerCommands).cancelJob(runnerId, job.getId());
  }

  @Test
  @DisplayName("a queued job is cancelled without bothering any runner")
  void cancel_queuedJob_doesNotNotifyRunner() {
    final var job = job(WorkflowJobStatus.QUEUED, null);
    when(this.stepRepository.findAllByJobIdOrderByStepNumberAsc(job.getId())).thenReturn(List.of());

    this.cancellation.cancel(job);

    assertThat(job.getStatus()).isEqualTo(WorkflowJobStatus.CANCELLED);
    verify(this.runnerCommands, never()).cancelJob(any(), any());
  }
}
