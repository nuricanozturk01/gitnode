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
package com.nuricanozturk.originhub.task.controllers;

import com.nuricanozturk.originhub.task.dtos.ProjectForm;
import com.nuricanozturk.originhub.task.dtos.ProjectInfo;
import com.nuricanozturk.originhub.task.dtos.ProjectRepoInfo;
import com.nuricanozturk.originhub.task.dtos.ProjectUpdateForm;
import com.nuricanozturk.originhub.task.services.ProjectService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{owner}")
@RequiredArgsConstructor
public class ProjectController {

  private final @NonNull ProjectService projectService;

  @PostMapping
  public @NonNull ResponseEntity<ProjectInfo> create(
      @PathVariable final @NonNull String owner,
      @Valid @RequestBody final @NonNull ProjectForm form) {

    return ResponseEntity.status(HttpStatus.CREATED).body(this.projectService.create(owner, form));
  }

  @GetMapping
  public @NonNull ResponseEntity<List<ProjectInfo>> getAll(
      @PathVariable final @NonNull String owner) {

    return ResponseEntity.ok(this.projectService.getAll(owner));
  }

  @GetMapping("/{projectCode}")
  public @NonNull ResponseEntity<ProjectInfo> get(
      @PathVariable final @NonNull String owner, @PathVariable final @NonNull String projectCode) {

    return ResponseEntity.ok(this.projectService.get(owner, projectCode));
  }

  @PatchMapping("/{projectCode}")
  public @NonNull ResponseEntity<ProjectInfo> update(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String projectCode,
      @Valid @RequestBody final @NonNull ProjectUpdateForm form) {

    return ResponseEntity.ok(this.projectService.update(owner, projectCode, form));
  }

  @DeleteMapping("/{projectCode}")
  public @NonNull ResponseEntity<Void> delete(
      @PathVariable final @NonNull String owner, @PathVariable final @NonNull String projectCode) {

    this.projectService.delete(owner, projectCode);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{projectCode}/repos/{repoId}")
  public @NonNull ResponseEntity<Void> linkRepo(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String projectCode,
      @PathVariable final @NonNull UUID repoId) {

    this.projectService.linkRepo(owner, projectCode, repoId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{projectCode}/repos/{repoId}")
  public @NonNull ResponseEntity<Void> unlinkRepo(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String projectCode,
      @PathVariable final @NonNull UUID repoId) {

    this.projectService.unlinkRepo(owner, projectCode, repoId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{projectCode}/repos")
  public @NonNull ResponseEntity<List<ProjectRepoInfo>> getLinkedRepos(
      @PathVariable final @NonNull String owner, @PathVariable final @NonNull String projectCode) {

    return ResponseEntity.ok(this.projectService.getLinkedRepos(owner, projectCode));
  }
}
