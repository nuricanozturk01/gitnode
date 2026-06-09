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

import dev.gitnode.os.pr.dtos.PrCommentForm;
import dev.gitnode.os.pr.dtos.PrCommentInfo;
import dev.gitnode.os.pr.dtos.PrCommentUpdateForm;
import dev.gitnode.os.pr.services.PullRequestCommentService;
import dev.gitnode.os.shared.auth.services.JwtUtils;
import dev.gitnode.os.shared.collaborator.dtos.CollaboratorPermission;
import dev.gitnode.os.shared.repo.dtos.PageResponse;
import dev.gitnode.os.shared.repo.services.RepoService;
import jakarta.validation.Valid;
import java.util.UUID;
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
@RequestMapping("/api/repos/{owner}/{repo}/pulls/{number}")
@RequiredArgsConstructor
@NullMarked
public class PullRequestCommitController {

  private final PullRequestCommentService prService;
  private final JwtUtils tokenService;
  private final RepoService repoService;

  @PostMapping("/comments")
  public ResponseEntity<PrCommentInfo> addComment(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @Valid @RequestBody final PrCommentForm form) {

    final var authorId = this.tokenService.extractUserId(authHeader);
    this.repoService.assertUserHasAnyPermission(
        authorId,
        owner,
        repo,
        CollaboratorPermission.PULL_REQUEST_REVIEW,
        CollaboratorPermission.PULL_REQUEST_MERGE);
    final var comment = this.prService.addComment(owner, repo, number, authorId, form);
    return ResponseEntity.status(HttpStatus.CREATED).body(comment);
  }

  @PatchMapping("/comments/{commentId}")
  @SuppressWarnings("all")
  public ResponseEntity<PrCommentInfo> updateComment(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @PathVariable final UUID commentId,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @Valid @RequestBody final PrCommentUpdateForm form) {

    final var requesterId = this.tokenService.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    final var response = this.prService.updateComment(commentId, requesterId, form);
    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/comments/{commentId}")
  @SuppressWarnings("unused")
  public ResponseEntity<Void> deleteComment(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @PathVariable final UUID commentId,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {

    final var requesterId = this.tokenService.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    this.prService.deleteComment(commentId, requesterId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/comments")
  public ResponseEntity<PageResponse<PrCommentInfo>> getComments(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "50") final int size,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader) {

    final var requesterId = authHeader != null ? this.tokenService.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    final var comments = this.prService.getComments(owner, repo, number, page, size);
    return ResponseEntity.ok(PageResponse.from(comments));
  }
}
