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
package dev.gitnode.os.snippet.controllers;

import dev.gitnode.os.shared.auth.services.JwtUtils;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.ratelimit.RateLimit;
import dev.gitnode.os.shared.repo.dtos.PageResponse;
import dev.gitnode.os.snippet.dtos.SnippetDetail;
import dev.gitnode.os.snippet.dtos.SnippetForm;
import dev.gitnode.os.snippet.dtos.SnippetInfo;
import dev.gitnode.os.snippet.dtos.SnippetRevisionDetail;
import dev.gitnode.os.snippet.dtos.SnippetRevisionInfo;
import dev.gitnode.os.snippet.dtos.SnippetUpdateForm;
import dev.gitnode.os.snippet.services.SnippetService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/snippets")
@RequiredArgsConstructor
@NullMarked
public class SnippetController {

  private final JwtUtils jwtUtils;
  private final SnippetService snippetService;

  @PostMapping
  @RateLimit(limit = 50, windowSeconds = 600, key = "snippet.create")
  public ResponseEntity<SnippetDetail> create(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @Valid @RequestBody final SnippetForm form) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.snippetService.create(tenantId, form));
  }

  @GetMapping
  public ResponseEntity<PageResponse<SnippetInfo>> listPublic(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String ignored,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size,
      @RequestParam(required = false) final @Nullable String q) {

    return ResponseEntity.ok(PageResponse.from(this.snippetService.listPublic(page, size, q)));
  }

  @GetMapping("/me")
  public ResponseEntity<PageResponse<SnippetInfo>> listMine(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(this.snippetService.listMine(tenantId, page, size));
  }

  @GetMapping("/{snippetId}")
  public ResponseEntity<SnippetDetail> get(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader,
      @PathVariable final UUID snippetId) {

    final var callerId = this.extractOptionalCaller(authHeader);
    return ResponseEntity.ok(this.snippetService.get(snippetId, callerId));
  }

  @PatchMapping("/{snippetId}")
  public ResponseEntity<SnippetDetail> update(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable final UUID snippetId,
      @Valid @RequestBody final SnippetUpdateForm form) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(this.snippetService.update(tenantId, snippetId, form));
  }

  @DeleteMapping("/{snippetId}")
  public ResponseEntity<Void> delete(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable final UUID snippetId) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    this.snippetService.delete(tenantId, snippetId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{snippetId}/fork")
  @RateLimit(limit = 30, windowSeconds = 600, key = "snippet.fork")
  public ResponseEntity<SnippetDetail> fork(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable final UUID snippetId) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.snippetService.fork(tenantId, snippetId));
  }

  @GetMapping("/{snippetId}/revisions")
  public ResponseEntity<PageResponse<SnippetRevisionInfo>> listRevisions(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader,
      @PathVariable final UUID snippetId,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "10") final int size) {

    final var callerId = this.extractOptionalCaller(authHeader);
    return ResponseEntity.ok(this.snippetService.listRevisions(snippetId, callerId, page, size));
  }

  @GetMapping("/{snippetId}/revisions/{revisionId}")
  public ResponseEntity<SnippetRevisionDetail> getRevision(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader,
      @PathVariable final UUID snippetId,
      @PathVariable final UUID revisionId) {

    final var callerId = this.extractOptionalCaller(authHeader);
    return ResponseEntity.ok(this.snippetService.getRevision(snippetId, revisionId, callerId));
  }

  @PutMapping("/{snippetId}/repo/{repoId}")
  public ResponseEntity<SnippetDetail> linkRepo(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable final UUID snippetId,
      @PathVariable final UUID repoId) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(this.snippetService.linkRepo(tenantId, snippetId, repoId));
  }

  @DeleteMapping("/{snippetId}/repo/{repoId}")
  public ResponseEntity<SnippetDetail> unlinkRepo(
      final @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable final UUID snippetId,
      @PathVariable final UUID repoId) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(this.snippetService.unlinkRepo(tenantId, snippetId, repoId));
  }

  @GetMapping("/by-owner/{username}")
  public ResponseEntity<PageResponse<SnippetInfo>> listByOwner(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader,
      @PathVariable final String username,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size) {

    final var callerId = this.extractOptionalCaller(authHeader);
    return ResponseEntity.ok(
        PageResponse.from(this.snippetService.listByOwner(username, callerId, page, size)));
  }

  @GetMapping("/repo/{owner}/{repoName}")
  public ResponseEntity<PageResponse<SnippetInfo>> listByRepo(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repoName,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size) {

    final var callerId = this.extractOptionalCaller(authHeader);
    return ResponseEntity.ok(this.snippetService.listByRepo(owner, repoName, callerId, page, size));
  }

  @GetMapping("/{snippetId}/files/{fileId}/raw")
  public ResponseEntity<String> getRawFile(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader,
      @PathVariable final UUID snippetId,
      @PathVariable final UUID fileId) {

    final var callerId = this.extractOptionalCaller(authHeader);
    final var detail = this.snippetService.get(snippetId, callerId);

    final var file =
        detail.files().stream()
            .filter(f -> f.id().equals(fileId))
            .findFirst()
            .orElseThrow(() -> new ItemNotFoundException("fileNotFound"));

    return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(file.content());
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
