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
package dev.gitnode.os.actions.controllers;

import dev.gitnode.os.actions.ActionsEnumNames;
import dev.gitnode.os.actions.dtos.request.WorkflowDispatchRequest;
import dev.gitnode.os.actions.dtos.response.WorkflowJobResponse;
import dev.gitnode.os.actions.dtos.response.WorkflowRunResponse;
import dev.gitnode.os.actions.dtos.response.WorkflowStepResponse;
import dev.gitnode.os.actions.entities.WorkflowJob;
import dev.gitnode.os.actions.entities.WorkflowRun;
import dev.gitnode.os.actions.entities.WorkflowRunStatus;
import dev.gitnode.os.actions.entities.WorkflowStep;
import dev.gitnode.os.actions.repositories.WorkflowJobRepository;
import dev.gitnode.os.actions.repositories.WorkflowRunRepository;
import dev.gitnode.os.actions.repositories.WorkflowStepRepository;
import dev.gitnode.os.actions.services.WorkflowExecutionService;
import dev.gitnode.os.actions.services.WorkflowTriggerService;
import dev.gitnode.os.actions.websocket.RunStatusSseRegistry;
import dev.gitnode.os.shared.auth.services.JwtUtils;
import dev.gitnode.os.shared.collaborator.dtos.CollaboratorPermission;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.repo.entities.Repo;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import dev.gitnode.os.shared.repo.services.RepoService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/repos/{owner}/{repo}/actions")
@RequiredArgsConstructor
@NullMarked
public class WorkflowRunController {

  private final WorkflowRunRepository runRepository;
  private final WorkflowJobRepository jobRepository;
  private final WorkflowStepRepository stepRepository;
  private final RepoRepository repoRepository;
  private final RepoService repoService;
  private final WorkflowTriggerService triggerService;
  private final WorkflowExecutionService executionService;
  private final RunStatusSseRegistry runStatusSseRegistry;
  private final JwtUtils jwtUtils;

  record PageResponse<T>(List<T> content, long totalElements, int totalPages, int page, int size) {}

  @GetMapping("/runs")
  public ResponseEntity<PageResponse<WorkflowRunResponse>> listRuns(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestParam(required = false) final @Nullable String triggerEvent,
      @RequestParam(required = false) final @Nullable String triggerRef,
      @PageableDefault(size = 20) final Pageable pageable) {

    final var userId = this.jwtUtils.tryExtractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(userId, owner, repo);
    final var repoId = this.requireRepoId(owner, repo);

    final var page =
        (triggerEvent != null && triggerRef != null)
            ? this.runRepository.findAllByRepoIdAndTriggerEventAndTriggerRefOrderByCreatedAtDesc(
                repoId, triggerEvent, triggerRef, pageable)
            : this.runRepository.findAllByRepoIdOrderByCreatedAtDesc(repoId, pageable);

    return ResponseEntity.ok(
        new PageResponse<>(
            page.getContent().stream().map(run -> this.toResponse(run, List.of())).toList(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.getSize()));
  }

  @GetMapping("/runs/{runId}")
  public ResponseEntity<WorkflowRunResponse> getRun(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID runId) {

    final var userId = this.jwtUtils.tryExtractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(userId, owner, repo);
    this.requireRepoId(owner, repo);

    final var run =
        this.runRepository
            .findById(runId)
            .orElseThrow(() -> new ItemNotFoundException("Workflow run not found: " + runId));

    final var jobs =
        this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(runId).stream()
            .map(job -> this.toJobResponse(job, true))
            .toList();

    return ResponseEntity.ok(this.toResponse(run, jobs));
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

  @GetMapping(value = "/runs/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamRunEvents(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID runId) {

    final var userId = this.jwtUtils.tryExtractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(userId, owner, repo);
    this.requireRepoId(owner, repo);

    final var run =
        this.runRepository
            .findById(runId)
            .orElseThrow(() -> new ItemNotFoundException("Workflow run not found: " + runId));

    final var emitter = this.runStatusSseRegistry.subscribe(runId);

    final var status = run.getStatus();
    if (status == WorkflowRunStatus.SUCCESS
        || status == WorkflowRunStatus.FAILURE
        || status == WorkflowRunStatus.CANCELLED
        || status == WorkflowRunStatus.SKIPPED) {
      try {
        emitter.send(SseEmitter.event().name("run_completed").data(runId.toString()));
      } catch (final Exception ignored) {
      }
      emitter.complete();
    }

    return emitter;
  }

  @PostMapping("/runs/{runId}/cancel")
  public ResponseEntity<Void> cancelRun(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID runId) {

    final var userId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserHasPermission(
        userId, owner, repo, CollaboratorPermission.ACTIONS_WRITE);
    this.executionService.cancelRun(runId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/runs/{runId}")
  public ResponseEntity<Void> deleteRun(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID runId) {

    final var userId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserHasPermission(
        userId, owner, repo, CollaboratorPermission.ACTIONS_WRITE);
    this.executionService.deleteRun(runId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/workflows/dispatches")
  public ResponseEntity<Void> dispatch(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @Valid @RequestBody final WorkflowDispatchRequest body) {

    final UUID actorId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserHasPermission(
        actorId, owner, repo, CollaboratorPermission.ACTIONS_WRITE);
    final UUID repoId = this.requireRepoId(owner, repo);
    this.triggerService.triggerManual(repoId, body.filePath(), body.ref(), body.inputs(), actorId);
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  private UUID requireRepoId(final String owner, final String repo) {

    return this.repoRepository
        .findByOwnerUsernameAndName(owner, repo)
        .map(Repo::getId)
        .orElseThrow(
            () -> new ItemNotFoundException("Repository not found: " + owner + "/" + repo));
  }
}
