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

import com.nuricanozturk.originhub.actions.dtos.response.WorkflowDetailResponse;
import com.nuricanozturk.originhub.actions.dtos.response.WorkflowSummaryResponse;
import com.nuricanozturk.originhub.actions.services.WorkflowDefinitionService;
import com.nuricanozturk.originhub.shared.auth.services.JwtUtils;
import com.nuricanozturk.originhub.shared.collaborator.dtos.CollaboratorPermission;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.repo.services.RepoService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repos/{owner}/{repo}/actions")
@RequiredArgsConstructor
@NullMarked
public class WorkflowController {

  private final WorkflowDefinitionService definitionService;
  private final RepoRepository repoRepository;
  private final RepoService repoService;
  private final JwtUtils jwtUtils;

  @GetMapping("/workflows")
  public ResponseEntity<List<WorkflowSummaryResponse>> list(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo) {

    final var userId = this.jwtUtils.tryExtractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(userId, owner, repo);
    final UUID repoId = this.requireRepoId(owner, repo);
    return ResponseEntity.ok(this.definitionService.listForRepo(repoId));
  }

  @GetMapping("/workflows/detail")
  public ResponseEntity<WorkflowDetailResponse> detail(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestParam final String filePath) {

    final var userId = this.jwtUtils.tryExtractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(userId, owner, repo);
    final UUID repoId = this.requireRepoId(owner, repo);
    return ResponseEntity.ok(this.definitionService.getDetail(repoId, filePath));
  }

  @PutMapping("/workflows/enable")
  public ResponseEntity<Void> enable(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestParam final String filePath) {

    final var userId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserHasPermission(
        userId, owner, repo, CollaboratorPermission.ACTIONS_WRITE);
    this.definitionService.setEnabled(this.requireRepoId(owner, repo), filePath, true);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/workflows/disable")
  public ResponseEntity<Void> disable(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestParam final String filePath) {

    final var userId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserHasPermission(
        userId, owner, repo, CollaboratorPermission.ACTIONS_WRITE);
    this.definitionService.setEnabled(this.requireRepoId(owner, repo), filePath, false);
    return ResponseEntity.noContent().build();
  }

  private UUID requireRepoId(final String owner, final String repo) {

    return this.repoRepository
        .findByOwnerUsernameAndName(owner, repo)
        .map(Repo::getId)
        .orElseThrow(
            () -> new ItemNotFoundException("Repository not found: " + owner + "/" + repo));
  }
}
