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
package dev.gitnode.os.discussion.controllers;

import dev.gitnode.os.discussion.dtos.DiscussionCommentForm;
import dev.gitnode.os.discussion.dtos.DiscussionCommentInfo;
import dev.gitnode.os.discussion.dtos.DiscussionCommentUpdateForm;
import dev.gitnode.os.discussion.dtos.DiscussionDetail;
import dev.gitnode.os.discussion.dtos.DiscussionForm;
import dev.gitnode.os.discussion.dtos.DiscussionInfo;
import dev.gitnode.os.discussion.dtos.DiscussionUpdateForm;
import dev.gitnode.os.discussion.dtos.DiscussionVoteInfo;
import dev.gitnode.os.discussion.entities.DiscussionCategory;
import dev.gitnode.os.discussion.services.DiscussionService;
import dev.gitnode.os.identity.services.JwtUtils;
import dev.gitnode.os.repo.api.RepoAccessPort;
import dev.gitnode.os.repo.dtos.PageResponse;
import dev.gitnode.os.shared.ratelimit.RateLimit;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repos/{owner}/{repo}/discussions")
@RequiredArgsConstructor
@NullMarked
public class DiscussionController {

  private final DiscussionService service;
  private final JwtUtils jwtUtils;
  private final RepoAccessPort repoAccess;

  public record LockForm(boolean locked) {}

  public record AnswerForm(@Nullable UUID commentId) {}

  @PostMapping
  @RateLimit(limit = 60, windowSeconds = 600, key = "discussion.create")
  public ResponseEntity<DiscussionDetail> create(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth,
      @Valid @RequestBody final DiscussionForm form) {
    final var userId = this.writer(auth, owner, repo);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.service.create(owner, repo, userId, form));
  }

  @GetMapping
  public ResponseEntity<PageResponse<DiscussionInfo>> list(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestParam(required = false) final @Nullable DiscussionCategory category,
      @RequestParam(defaultValue = "false") final boolean unanswered,
      @RequestParam(defaultValue = "latest") final String sort,
      @RequestParam(defaultValue = "0") final int page,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String auth) {
    this.reader(auth, owner, repo);
    return ResponseEntity.ok(this.service.list(owner, repo, category, unanswered, sort, page));
  }

  @GetMapping("/{number}")
  public ResponseEntity<DiscussionDetail> get(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String auth) {
    final var viewer = this.reader(auth, owner, repo);
    return ResponseEntity.ok(this.service.get(owner, repo, number, viewer));
  }

  @PatchMapping("/{number}")
  public ResponseEntity<DiscussionDetail> update(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth,
      @Valid @RequestBody final DiscussionUpdateForm form) {
    final var userId = this.writer(auth, owner, repo);
    return ResponseEntity.ok(this.service.update(owner, repo, number, userId, form));
  }

  @DeleteMapping("/{number}")
  public ResponseEntity<Void> delete(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth) {
    this.service.delete(owner, repo, number, this.writer(auth, owner, repo));
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{number}/lock")
  public ResponseEntity<DiscussionDetail> lock(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth,
      @RequestBody final LockForm form) {
    final var userId = this.writer(auth, owner, repo);
    return ResponseEntity.ok(this.service.setLocked(owner, repo, number, userId, form.locked()));
  }

  @PutMapping("/{number}/upvote")
  public ResponseEntity<DiscussionVoteInfo> upvote(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth) {
    final var userId = this.writer(auth, owner, repo);
    return ResponseEntity.ok(this.service.setUpvote(owner, repo, number, userId, true));
  }

  @DeleteMapping("/{number}/upvote")
  public ResponseEntity<DiscussionVoteInfo> removeUpvote(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth) {
    final var userId = this.writer(auth, owner, repo);
    return ResponseEntity.ok(this.service.setUpvote(owner, repo, number, userId, false));
  }

  @PutMapping("/{number}/answer")
  public ResponseEntity<DiscussionDetail> answer(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth,
      @RequestBody final AnswerForm form) {
    final var userId = this.writer(auth, owner, repo);
    return ResponseEntity.ok(this.service.setAnswer(owner, repo, number, userId, form.commentId()));
  }

  @GetMapping("/{number}/comments")
  public ResponseEntity<PageResponse<DiscussionCommentInfo>> comments(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestParam(defaultValue = "0") final int page,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String auth) {
    this.reader(auth, owner, repo);
    return ResponseEntity.ok(this.service.listComments(owner, repo, number, page));
  }

  @GetMapping("/{number}/comments/{commentId}/replies")
  public ResponseEntity<PageResponse<DiscussionCommentInfo>> replies(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @PathVariable final UUID commentId,
      @RequestParam(defaultValue = "0") final int page,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String auth) {
    this.reader(auth, owner, repo);
    return ResponseEntity.ok(this.service.listReplies(owner, repo, number, commentId, page));
  }

  @PostMapping("/{number}/comments")
  @RateLimit(limit = 200, windowSeconds = 600, key = "discussion.comment")
  public ResponseEntity<DiscussionCommentInfo> addComment(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth,
      @Valid @RequestBody final DiscussionCommentForm form) {
    final var userId = this.writer(auth, owner, repo);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.service.addComment(owner, repo, number, userId, form));
  }

  @PatchMapping("/{number}/comments/{commentId}")
  public ResponseEntity<DiscussionCommentInfo> updateComment(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @PathVariable final UUID commentId,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth,
      @Valid @RequestBody final DiscussionCommentUpdateForm form) {
    final var userId = this.writer(auth, owner, repo);
    return ResponseEntity.ok(
        this.service.updateComment(owner, repo, number, commentId, userId, form));
  }

  @DeleteMapping("/{number}/comments/{commentId}")
  public ResponseEntity<Void> deleteComment(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @PathVariable final UUID commentId,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth) {
    this.service.deleteComment(owner, repo, number, commentId, this.writer(auth, owner, repo));
    return ResponseEntity.noContent().build();
  }

  /** Resolves the optional caller and checks read access to the repository. */
  private @Nullable UUID reader(
      final @Nullable String auth, final String owner, final String repo) {
    final var userId = auth == null ? null : this.jwtUtils.extractUserId(auth);
    this.repoAccess.assertUserCanAccessRepo(userId, owner, repo);
    return userId;
  }

  private UUID writer(final String auth, final String owner, final String repo) {
    final var userId = this.jwtUtils.extractUserId(auth);
    this.repoAccess.assertUserCanAccessRepo(userId, owner, repo);
    return userId;
  }
}
