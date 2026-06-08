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
package dev.gitnode.os.task.controllers;

import dev.gitnode.os.shared.ratelimit.RateLimit;
import dev.gitnode.os.shared.repo.dtos.PageResponse;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.task.dtos.ProjectForm;
import dev.gitnode.os.task.dtos.ProjectInfo;
import dev.gitnode.os.task.dtos.ProjectRepoInfo;
import dev.gitnode.os.task.dtos.ProjectUpdateForm;
import dev.gitnode.os.task.services.ProjectService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{owner}")
@RequiredArgsConstructor
@NullMarked
public class ProjectController {

  private final ProjectService projectService;

  @PostMapping
  @RateLimit(limit = 50, windowSeconds = 3600, key = "project.create")
  public ResponseEntity<ProjectInfo> create(
      @PathVariable final String owner,
      @AuthenticationPrincipal final Tenant caller,
      @Valid @RequestBody final ProjectForm form) {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.projectService.create(owner, caller, form));
  }

  @GetMapping
  public ResponseEntity<PageResponse<ProjectInfo>> getAll(
      @PathVariable final String owner,
      @AuthenticationPrincipal final @Nullable Tenant viewer,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "12") final int size) {

    return ResponseEntity.ok(this.projectService.getAll(owner, viewer, page, size));
  }

  @GetMapping("/{projectCode}")
  public ResponseEntity<ProjectInfo> get(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @AuthenticationPrincipal final @Nullable Tenant viewer) {

    return ResponseEntity.ok(this.projectService.get(owner, projectCode, viewer));
  }

  @PatchMapping("/{projectCode}")
  public ResponseEntity<ProjectInfo> update(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @AuthenticationPrincipal final Tenant caller,
      @Valid @RequestBody final ProjectUpdateForm form) {

    return ResponseEntity.ok(this.projectService.update(owner, projectCode, caller, form));
  }

  @DeleteMapping("/{projectCode}")
  public ResponseEntity<Void> delete(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @AuthenticationPrincipal final Tenant caller) {

    this.projectService.delete(owner, projectCode, caller);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{projectCode}/repos/{repoId}")
  public ResponseEntity<Void> linkRepo(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @PathVariable final UUID repoId,
      @AuthenticationPrincipal final Tenant caller) {

    this.projectService.linkRepo(owner, projectCode, repoId, caller);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{projectCode}/repos/{repoId}")
  public ResponseEntity<Void> unlinkRepo(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @PathVariable final UUID repoId,
      @AuthenticationPrincipal final Tenant caller) {

    this.projectService.unlinkRepo(owner, projectCode, repoId, caller);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{projectCode}/repos")
  public ResponseEntity<List<ProjectRepoInfo>> getLinkedRepos(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @AuthenticationPrincipal final @Nullable Tenant viewer) {

    return ResponseEntity.ok(this.projectService.getLinkedRepos(owner, projectCode, viewer));
  }

  @SuppressWarnings("unused")
  @GetMapping("/by-repo/{repoId}")
  public ResponseEntity<PageResponse<ProjectInfo>> getByRepo(
      @PathVariable final String owner,
      @PathVariable final UUID repoId,
      @AuthenticationPrincipal final @Nullable Tenant viewer,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "12") final int size) {

    return ResponseEntity.ok(this.projectService.getLinkedProjects(repoId, viewer, page, size));
  }
}
