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
package dev.gitnode.os.pr.controllers;

import dev.gitnode.os.pr.dtos.PrDetail;
import dev.gitnode.os.pr.dtos.PrForm;
import dev.gitnode.os.pr.dtos.PrInfo;
import dev.gitnode.os.pr.dtos.PrMergeForm;
import dev.gitnode.os.pr.dtos.PrUpdateForm;
import dev.gitnode.os.pr.services.PullRequestService;
import dev.gitnode.os.shared.auth.services.JwtUtils;
import dev.gitnode.os.shared.collaborator.dtos.CollaboratorPermission;
import dev.gitnode.os.shared.commit.dtos.CommitInfo;
import dev.gitnode.os.shared.commit.dtos.FileDiff;
import dev.gitnode.os.shared.ratelimit.RateLimit;
import dev.gitnode.os.shared.repo.dtos.PageResponse;
import dev.gitnode.os.shared.repo.services.RepoService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repos/{owner}/{repo}/pulls")
@RequiredArgsConstructor
@NullMarked
public class PullRequestController {

  private final PullRequestService prService;
  private final JwtUtils tokenService;
  private final RepoService repoService;

  @PostMapping
  @RateLimit(limit = 50, windowSeconds = 600, key = "pr.create")
  public ResponseEntity<PrDetail> create(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @Valid @RequestBody final PrForm form)
      throws IOException {

    final var authorId = this.tokenService.extractUserId(authHeader);
    this.repoService.assertUserHasPermission(
        authorId, owner, repo, CollaboratorPermission.PULL_REQUEST_CREATE);
    final var createdPr = this.prService.create(owner, repo, authorId, form);
    return ResponseEntity.status(HttpStatus.CREATED).body(createdPr);
  }

  @PatchMapping("/{number}")
  public ResponseEntity<PrDetail> update(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @Valid @RequestBody final PrUpdateForm form) {

    final var requesterId = this.tokenService.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    final var updatedPr = this.prService.update(owner, repo, number, form);
    return ResponseEntity.ok(updatedPr);
  }

  @DeleteMapping("/{number}")
  public ResponseEntity<Void> close(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {

    final var requesterId = this.tokenService.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    this.prService.close(owner, repo, number);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{number}/merge")
  @RateLimit(limit = 50, windowSeconds = 600, key = "pr.merge")
  public ResponseEntity<PrDetail> merge(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @Valid @RequestBody final PrMergeForm form)
      throws IOException {

    final var mergedById = this.tokenService.extractUserId(authHeader);
    this.repoService.assertUserHasPermission(
        mergedById, owner, repo, CollaboratorPermission.PULL_REQUEST_MERGE);
    final var mergedPr = this.prService.merge(owner, repo, number, mergedById, form);
    return ResponseEntity.ok(mergedPr);
  }

  @GetMapping
  public ResponseEntity<PageResponse<PrInfo>> getAll(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestParam(defaultValue = "OPEN") final String status,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "25") final int size,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader) {

    final var requesterId = authHeader != null ? this.tokenService.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    final var prs = this.prService.getAll(owner, repo, status, page, size);
    return ResponseEntity.ok(PageResponse.from(prs));
  }

  @GetMapping("/{number}")
  public ResponseEntity<PrDetail> get(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader) {

    final var requesterId = authHeader != null ? this.tokenService.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    final var pr = this.prService.get(owner, repo, number);
    return ResponseEntity.ok(pr);
  }

  @GetMapping("/{number}/commits")
  public ResponseEntity<List<CommitInfo>> getPrCommits(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader)
      throws IOException {

    final var requesterId = authHeader != null ? this.tokenService.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    final var prCommits = this.prService.getPrCommits(owner, repo, number);
    return ResponseEntity.ok(prCommits);
  }

  @GetMapping("/{number}/diff")
  public ResponseEntity<List<FileDiff>> getPrDiff(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader)
      throws IOException {

    final var requesterId = authHeader != null ? this.tokenService.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    final var diffs = this.prService.getPrDiff(owner, repo, number);
    return ResponseEntity.ok(diffs);
  }
}
