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
package com.nuricanozturk.originhub.actions.controllers;

import com.nuricanozturk.originhub.actions.dtos.request.WorkflowDispatchRequest;
import com.nuricanozturk.originhub.actions.dtos.response.WorkflowJobResponse;
import com.nuricanozturk.originhub.actions.dtos.response.WorkflowRunResponse;
import com.nuricanozturk.originhub.actions.repositories.WorkflowJobRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowRunRepository;
import com.nuricanozturk.originhub.actions.services.WorkflowTriggerService;
import com.nuricanozturk.originhub.shared.auth.services.JwtUtils;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repos/{owner}/{repo}/actions")
@RequiredArgsConstructor
@NullMarked
public class WorkflowRunController {

  private final WorkflowRunRepository runRepository;
  private final WorkflowJobRepository jobRepository;
  private final RepoRepository repoRepository;
  private final WorkflowTriggerService triggerService;
  private final JwtUtils jwtUtils;

  @GetMapping("/runs")
  public ResponseEntity<Page<WorkflowRunResponse>> listRuns(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PageableDefault(size = 20) final Pageable pageable) {

    this.jwtUtils.extractUserId(authHeader);
    final var repoId = this.requireRepoId(owner, repo);

    final var page =
        this.runRepository
            .findAllByRepoIdOrderByCreatedAtDesc(repoId, pageable)
            .map(run -> this.toResponse(run, List.of()));

    return ResponseEntity.ok(page);
  }

  @GetMapping("/runs/{runId}")
  public ResponseEntity<WorkflowRunResponse> getRun(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID runId) {

    this.jwtUtils.extractUserId(authHeader);
    this.requireRepoId(owner, repo);

    final var run =
        this.runRepository
            .findById(runId)
            .orElseThrow(() -> new ItemNotFoundException("Workflow run not found: " + runId));

    final var jobs =
        this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(runId).stream()
            .map(this::toJobResponse)
            .toList();

    return ResponseEntity.ok(this.toResponse(run, jobs));
  }

  private WorkflowRunResponse toResponse(
      final com.nuricanozturk.originhub.actions.entities.WorkflowRun run,
      final List<WorkflowJobResponse> jobs) {

    return new WorkflowRunResponse(
        run.getId(),
        run.getRunNumber(),
        run.getWorkflowName(),
        run.getStatus().name().toLowerCase(),
        run.getConclusion(),
        run.getTriggerEvent(),
        run.getTriggerRef(),
        run.getTriggerSha(),
        run.getStartedAt(),
        run.getCompletedAt(),
        run.getCreatedAt(),
        jobs);
  }

  private WorkflowJobResponse toJobResponse(
      final com.nuricanozturk.originhub.actions.entities.WorkflowJob job) {

    return new WorkflowJobResponse(
        job.getId(),
        job.getName(),
        job.getStatus().name().toLowerCase(),
        job.getConclusion(),
        job.getRunnerLabels(),
        job.getNeeds(),
        job.getStartedAt(),
        job.getCompletedAt(),
        job.getCreatedAt());
  }

  @PostMapping("/workflows/{workflowId}/dispatches")
  public ResponseEntity<Void> dispatch(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final String workflowId,
      @Valid @RequestBody final WorkflowDispatchRequest body) {

    final UUID actorId = this.jwtUtils.extractUserId(authHeader);
    final UUID repoId = this.requireRepoId(owner, repo);
    this.triggerService.triggerManual(repoId, workflowId, body.ref(), body.inputs(), actorId);
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
