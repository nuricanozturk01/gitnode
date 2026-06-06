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
package com.nuricanozturk.originhub.snippet.controllers;

import com.nuricanozturk.originhub.shared.auth.services.JwtUtils;
import com.nuricanozturk.originhub.shared.ratelimit.RateLimit;
import com.nuricanozturk.originhub.shared.repo.dtos.PageResponse;
import com.nuricanozturk.originhub.snippet.dtos.SnippetCommentForm;
import com.nuricanozturk.originhub.snippet.dtos.SnippetCommentInfo;
import com.nuricanozturk.originhub.snippet.services.SnippetCommentService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/snippets/{snippetId}/comments")
@RequiredArgsConstructor
@NullMarked
public class SnippetCommentController {

  private final JwtUtils jwtUtils;
  private final SnippetCommentService commentService;

  @GetMapping
  public ResponseEntity<PageResponse<SnippetCommentInfo>> list(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader,
      @PathVariable final UUID snippetId,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "10") final int size) {

    final var callerId = this.extractOptionalCaller(authHeader);
    return ResponseEntity.ok(this.commentService.listComments(snippetId, callerId, page, size));
  }

  @PostMapping
  @RateLimit(limit = 150, windowSeconds = 600, key = "snippet.comment")
  public ResponseEntity<SnippetCommentInfo> add(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable final UUID snippetId,
      @Valid @RequestBody final SnippetCommentForm form) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.commentService.addComment(tenantId, snippetId, form));
  }

  @DeleteMapping("/{commentId}")
  public ResponseEntity<Void> delete(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable final UUID snippetId,
      @PathVariable final UUID commentId) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    this.commentService.deleteComment(tenantId, snippetId, commentId);
    return ResponseEntity.noContent().build();
  }

  private @Nullable UUID extractOptionalCaller(final @Nullable String authHeader) {
    if (authHeader == null || authHeader.isBlank()) {
      return null;
    }
    try {
      return this.jwtUtils.extractUserId(authHeader);
    } catch (final Exception _) {
      return null;
    }
  }
}
