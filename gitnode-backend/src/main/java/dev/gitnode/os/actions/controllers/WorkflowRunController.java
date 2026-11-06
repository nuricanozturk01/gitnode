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

import dev.gitnode.os.actions.dtos.request.WorkflowDispatchRequest;
import dev.gitnode.os.actions.dtos.response.AnnotationResponse;
import dev.gitnode.os.actions.dtos.response.JobSummaryResponse;
import dev.gitnode.os.actions.dtos.response.WorkflowRunResponse;
import dev.gitnode.os.actions.entities.WorkflowRunStatus;
import dev.gitnode.os.actions.services.WorkflowExecutionService;
import dev.gitnode.os.actions.services.WorkflowRunQueryService;
import dev.gitnode.os.actions.services.WorkflowTriggerService;
import dev.gitnode.os.actions.websocket.RunStatusSseRegistry;
import dev.gitnode.os.identity.api.TokenUserResolver;
import dev.gitnode.os.repo.api.CollaboratorPermission;
import dev.gitnode.os.repo.api.RepoAccessPort;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/repos/{owner}/{repo}/actions")
@RequiredArgsConstructor
@NullMarked
public class WorkflowRunController {

  private final WorkflowRunQueryService runQueries;
  private final RepoLookupPort repoLookup;
  private final RepoAccessPort repoService;
  private final WorkflowTriggerService triggerService;
  private final WorkflowExecutionService executionService;
  private final RunStatusSseRegistry runStatusSseRegistry;
  private final TokenUserResolver jwtUtils;

  record PageResponse<T>(List<T> content, long totalElements, int totalPages, int page, int size) {}

  /** Query parameters of the run list; every field is optional. */
  record RunListQuery(
      @Nullable String triggerEvent,
      @Nullable String triggerRef,
      @Nullable String workflow,
      @Nullable String branch,
      @Nullable String status,
      @Nullable String actor,
      @Nullable String q) {}

  @GetMapping("/runs")
  public ResponseEntity<PageResponse<WorkflowRunResponse>> listRuns(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @ModelAttribute final RunListQuery query,
      @PageableDefault(size = 20) final Pageable pageable) {

    final var userId = this.jwtUtils.tryExtractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(userId, owner, repo);
    final var repoId = this.requireRepoId(owner, repo);

    final var filter =
        new WorkflowRunQueryService.RunFilter(
            query.workflow(),
            query.triggerEvent(),
            query.triggerRef(),
            query.branch(),
            query.status(),
            query.actor(),
            query.q());
    final var page = this.runQueries.listRuns(repoId, filter, pageable);

    return ResponseEntity.ok(
        new PageResponse<>(
            page.getContent(),
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

    return ResponseEntity.ok(this.runQueries.getRun(runId));
  }

  @GetMapping("/runs/{runId}/annotations")
  public ResponseEntity<List<AnnotationResponse>> annotations(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID runId) {

    this.assertCanRead(authHeader, owner, repo);
    return ResponseEntity.ok(this.runQueries.annotations(runId));
  }

  @GetMapping("/runs/{runId}/summary")
  public ResponseEntity<List<JobSummaryResponse>> summary(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID runId) {

    this.assertCanRead(authHeader, owner, repo);
    return ResponseEntity.ok(this.runQueries.summaries(runId));
  }

  private void assertCanRead(
      final @Nullable String authHeader, final String owner, final String repo) {
    final var userId = this.jwtUtils.tryExtractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(userId, owner, repo);
    this.requireRepoId(owner, repo);
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

    final var status = this.runQueries.runStatus(runId);

    final var emitter = this.runStatusSseRegistry.subscribe(runId);

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

  record RerunRequest(boolean failedOnly, @Nullable UUID jobId) {}

  @PostMapping("/runs/{runId}/rerun")
  public ResponseEntity<Void> rerun(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID runId,
      @RequestBody(required = false) final @Nullable RerunRequest body) {

    final var userId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserHasPermission(
        userId, owner, repo, CollaboratorPermission.ACTIONS_WRITE);
    this.requireRepoId(owner, repo);
    this.executionService.rerun(
        runId, body != null && body.failedOnly(), body == null ? null : body.jobId());
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

    return this.repoLookup
        .find(owner, repo)
        .map(RepoRef::id)
        .orElseThrow(
            () -> new ItemNotFoundException("Repository not found: " + owner + "/" + repo));
  }
}
