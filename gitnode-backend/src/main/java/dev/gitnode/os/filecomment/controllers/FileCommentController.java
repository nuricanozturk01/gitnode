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

package dev.gitnode.os.filecomment.controllers;

import dev.gitnode.os.filecomment.dtos.FileCommentForm;
import dev.gitnode.os.filecomment.dtos.FileCommentInfo;
import dev.gitnode.os.filecomment.dtos.FileCommentUpdateForm;
import dev.gitnode.os.filecomment.services.FileCommentService;
import dev.gitnode.os.identity.services.JwtUtils;
import dev.gitnode.os.repo.api.RepoAccessPort;
import dev.gitnode.os.shared.ratelimit.RateLimit;
import jakarta.validation.Valid;
import java.io.IOException;
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
@RequestMapping("/api/repos/{owner}/{repo}/file-comments")
@RequiredArgsConstructor
@NullMarked
public class FileCommentController {

  private final FileCommentService service;
  private final JwtUtils jwtUtils;
  private final RepoAccessPort repoService;

  @GetMapping
  public ResponseEntity<FileCommentInfo.Page> list(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestParam final String branch,
      @RequestParam final String path,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader)
      throws IOException {

    final var viewerId = authHeader != null ? this.jwtUtils.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(viewerId, owner, repo);
    return ResponseEntity.ok(this.service.list(owner, repo, branch, path, viewerId));
  }

  @PostMapping
  @RateLimit(limit = 60, windowSeconds = 600, key = "file-comment.create")
  public ResponseEntity<FileCommentInfo> create(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @Valid @RequestBody final FileCommentForm form)
      throws IOException {

    final var authorId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(authorId, owner, repo);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.service.create(owner, repo, authorId, form));
  }

  @PatchMapping("/{commentId}")
  public ResponseEntity<FileCommentInfo> update(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID commentId,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @Valid @RequestBody final FileCommentUpdateForm form) {

    final var editorId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(editorId, owner, repo);
    return ResponseEntity.ok(this.service.update(owner, repo, commentId, editorId, form.body()));
  }

  @DeleteMapping("/{commentId}")
  public ResponseEntity<Void> delete(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID commentId,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    this.service.delete(owner, repo, commentId, requesterId);
    return ResponseEntity.noContent().build();
  }
}
