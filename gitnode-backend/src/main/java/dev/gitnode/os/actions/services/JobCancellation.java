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
import dev.gitnode.os.actions.repositories.WorkflowJobRepository;
import dev.gitnode.os.actions.repositories.WorkflowStepRepository;
import dev.gitnode.os.actions.websocket.SseEmitterRegistry;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

/**
 * Cancels one job: marks it cancelled, closes the steps that never finished so the UI stops
 * spinning, and tells the runner to stop it when it is already running there.
 */
@Component
@RequiredArgsConstructor
@NullMarked
class JobCancellation {

  private final WorkflowJobRepository jobRepository;
  private final WorkflowStepRepository stepRepository;
  private final SseEmitterRegistry sseEmitterRegistry;
  private final RunnerCommands runnerCommands;

  void cancel(final WorkflowJob job) {

    final boolean wasRunning = job.getStatus() == WorkflowJobStatus.IN_PROGRESS;
    job.setStatus(WorkflowJobStatus.CANCELLED);
    job.setConclusion("cancelled");
    job.setCompletedAt(Instant.now());
    this.jobRepository.save(job);
    this.closeOpenSteps(job.getId(), "cancelled");
    if (wasRunning && job.getRunnerId() != null) {
      // The runner stops the job and reports it; until then it stays BUSY.
      this.runnerCommands.cancelJob(job.getRunnerId(), job.getId());
    }
  }

  /** Steps that never finished (running or not started) are closed with the given conclusion. */
  void closeOpenSteps(final UUID jobId, final String runningConclusion) {

    final var now = Instant.now();
    for (final var step : this.stepRepository.findAllByJobIdOrderByStepNumberAsc(jobId)) {
      if ("completed".equals(step.getStatus())) {
        continue;
      }
      final boolean wasRunning = "running".equals(step.getStatus());
      step.setStatus("completed");
      step.setConclusion(wasRunning ? runningConclusion : "cancelled");
      step.setCompletedAt(now);
      this.stepRepository.save(step);
      this.sseEmitterRegistry.complete(step.getId());
    }
  }
}
