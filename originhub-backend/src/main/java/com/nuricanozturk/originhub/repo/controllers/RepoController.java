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
package com.nuricanozturk.originhub.repo.controllers;

import com.nuricanozturk.originhub.shared.auth.services.JwtUtils;
import com.nuricanozturk.originhub.shared.repo.dtos.PageResponse;
import com.nuricanozturk.originhub.shared.repo.dtos.RepoForm;
import com.nuricanozturk.originhub.shared.repo.dtos.RepoInfo;
import com.nuricanozturk.originhub.shared.repo.events.RepoRenamedEvent;
import com.nuricanozturk.originhub.shared.repo.services.RepoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repo")
@RequiredArgsConstructor
@NullMarked
public class RepoController {

  private final JwtUtils tokenService;
  private final RepoService repoService;
  private final ApplicationEventPublisher eventPublisher;

  @PostMapping
  public ResponseEntity<RepoInfo> create(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      final @Valid @RequestBody RepoForm repoForm) {

    final var tenantId = this.tokenService.extractUserId(authHeader);

    final var repoInfo = this.repoService.create(tenantId, repoForm);

    return ResponseEntity.ok(repoInfo);
  }

  @GetMapping("/{owner}/{repo}")
  public ResponseEntity<RepoInfo> getRepo(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader) {

    final var requesterId = authHeader != null ? this.tokenService.extractUserId(authHeader) : null;
    final var repoInfo = this.repoService.findByOwnerAndName(owner, repo, requesterId);

    return ResponseEntity.ok(repoInfo);
  }

  @GetMapping("/{owner}")
  public ResponseEntity<PageResponse<RepoInfo>> listUserRepos(
      @PathVariable final String owner,
      @PageableDefault final Pageable pageable,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader) {

    final var requesterId = authHeader != null ? this.tokenService.extractUserId(authHeader) : null;
    final var repos = this.repoService.findAllByOwner(owner, pageable, requesterId);
    return ResponseEntity.ok(PageResponse.from(repos));
  }

  @DeleteMapping("/{owner}/{repo}")
  public ResponseEntity<Void> delete(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo) {

    final var tenantId = this.tokenService.extractUserId(authHeader);

    this.repoService.delete(tenantId, repo, owner);

    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{owner}/{repo}")
  public ResponseEntity<RepoInfo> update(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @Valid @RequestBody final RepoForm form) {

    final var tenantId = this.tokenService.extractUserId(authHeader);

    final var updatedRepo = this.repoService.update(tenantId, owner, repo, form);

    if (!repo.equals(updatedRepo.getName())) {
      this.eventPublisher.publishEvent(new RepoRenamedEvent(owner, repo, form.getName()));
    }

    return ResponseEntity.ok(updatedRepo);
  }
}
