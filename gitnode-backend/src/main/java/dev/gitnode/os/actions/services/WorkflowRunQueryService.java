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

import dev.gitnode.os.actions.ActionsEnumNames;
import dev.gitnode.os.actions.dtos.response.WorkflowJobResponse;
import dev.gitnode.os.actions.dtos.response.WorkflowRunResponse;
import dev.gitnode.os.actions.dtos.response.WorkflowStepResponse;
import dev.gitnode.os.actions.entities.WorkflowJob;
import dev.gitnode.os.actions.entities.WorkflowRun;
import dev.gitnode.os.actions.entities.WorkflowRunStatus;
import dev.gitnode.os.actions.entities.WorkflowStep;
import dev.gitnode.os.actions.repositories.WorkflowJobRepository;
import dev.gitnode.os.actions.repositories.WorkflowLogRepository;
import dev.gitnode.os.actions.repositories.WorkflowRunRepository;
import dev.gitnode.os.actions.repositories.WorkflowStepRepository;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of workflow runs: what the controllers show, so they never touch repositories. */
@Service
@RequiredArgsConstructor
@NullMarked
@Transactional(readOnly = true)
public class WorkflowRunQueryService {

  private final WorkflowRunRepository runRepository;
  private final WorkflowJobRepository jobRepository;
  private final WorkflowStepRepository stepRepository;
  private final WorkflowLogRepository logRepository;

  public Page<WorkflowRunResponse> listRuns(
      final UUID repoId,
      final @Nullable String triggerEvent,
      final @Nullable String triggerRef,
      final Pageable pageable) {

    final var page =
        (triggerEvent != null && triggerRef != null)
            ? this.runRepository.findAllByRepoIdAndTriggerEventAndTriggerRefOrderByCreatedAtDesc(
                repoId, triggerEvent, triggerRef, pageable)
            : this.runRepository.findAllByRepoIdOrderByCreatedAtDesc(repoId, pageable);

    return page.map(run -> this.toResponse(run, List.of()));
  }

  public WorkflowRunResponse getRun(final UUID runId) {

    final var run = this.requireRun(runId);
    final var jobs =
        this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(runId).stream()
            .map(job -> this.toJobResponse(job, true))
            .toList();

    return this.toResponse(run, jobs);
  }

  public WorkflowRunStatus runStatus(final UUID runId) {

    return this.requireRun(runId).getStatus();
  }

  /** Log lines already stored for a step, oldest first. */
  public List<WorkflowExecutionService.LogLine> logLines(final UUID stepId) {

    return this.logRepository.findAllByStepIdOrderByLineNumberAsc(stepId).stream()
        .map(
            log ->
                new WorkflowExecutionService.LogLine(
                    log.getLineNumber(), log.getContent(), log.getLevel()))
        .toList();
  }

  public boolean isStepCompleted(final UUID stepId) {

    return this.stepRepository
        .findById(stepId)
        .map(step -> "completed".equals(step.getStatus()))
        .orElse(false);
  }

  private WorkflowRun requireRun(final UUID runId) {

    return this.runRepository
        .findById(runId)
        .orElseThrow(() -> new ItemNotFoundException("Workflow run not found: " + runId));
  }

  private WorkflowRunResponse toResponse(
      final WorkflowRun run, final List<WorkflowJobResponse> jobs) {

    return new WorkflowRunResponse(
        run.getId(),
        run.getRunNumber(),
        run.getWorkflowName(),
        ActionsEnumNames.lowerCase(run.getStatus()),
        run.getConclusion(),
        run.getTriggerEvent(),
        run.getTriggerRef(),
        run.getTriggerSha(),
        run.getStartedAt(),
        run.getCompletedAt(),
        run.getCreatedAt(),
        jobs);
  }

  private WorkflowJobResponse toJobResponse(final WorkflowJob job, final boolean includeSteps) {

    final var steps =
        includeSteps
            ? this.stepRepository.findAllByJobIdOrderByStepNumberAsc(job.getId()).stream()
                .map(this::toStepResponse)
                .toList()
            : List.<WorkflowStepResponse>of();

    return new WorkflowJobResponse(
        job.getId(),
        job.getName(),
        ActionsEnumNames.lowerCase(job.getStatus()),
        job.getConclusion(),
        job.getRunnerLabels(),
        job.getNeeds(),
        job.getMatrixValues(),
        job.getStartedAt(),
        job.getCompletedAt(),
        job.getCreatedAt(),
        steps);
  }

  private WorkflowStepResponse toStepResponse(final WorkflowStep step) {

    return new WorkflowStepResponse(
        step.getId(),
        step.getStepNumber(),
        step.getName(),
        step.getUses(),
        step.getStatus(),
        step.getConclusion(),
        step.getStartedAt(),
        step.getCompletedAt());
  }
}
