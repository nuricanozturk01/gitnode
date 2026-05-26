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
import com.nuricanozturk.originhub.shared.repo.dtos.PageResponse;
import com.nuricanozturk.originhub.snippet.dtos.SnippetDetail;
import com.nuricanozturk.originhub.snippet.dtos.SnippetForm;
import com.nuricanozturk.originhub.snippet.dtos.SnippetInfo;
import com.nuricanozturk.originhub.snippet.dtos.SnippetRevisionDetail;
import com.nuricanozturk.originhub.snippet.dtos.SnippetRevisionInfo;
import com.nuricanozturk.originhub.snippet.dtos.SnippetUpdateForm;
import com.nuricanozturk.originhub.snippet.services.SnippetService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/snippets")
@RequiredArgsConstructor
public class SnippetController {

  private final @NonNull JwtUtils jwtUtils;
  private final @NonNull SnippetService snippetService;

  @PostMapping
  public @NonNull ResponseEntity<SnippetDetail> create(
      final @NonNull @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @Valid @RequestBody final @NonNull SnippetForm form) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.snippetService.create(tenantId, form));
  }

  @GetMapping
  public @NonNull ResponseEntity<PageResponse<SnippetInfo>> listPublic(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size,
      @RequestParam(required = false) final @Nullable String q) {

    return ResponseEntity.ok(PageResponse.from(this.snippetService.listPublic(page, size, q)));
  }

  @GetMapping("/me")
  public @NonNull ResponseEntity<java.util.List<SnippetInfo>> listMine(
      final @NonNull @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(this.snippetService.listMine(tenantId));
  }

  @GetMapping("/{snippetId}")
  public @NonNull ResponseEntity<SnippetDetail> get(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader,
      @PathVariable final @NonNull UUID snippetId) {

    final var callerId = this.extractOptionalCaller(authHeader);
    return ResponseEntity.ok(this.snippetService.get(snippetId, callerId));
  }

  @PatchMapping("/{snippetId}")
  public @NonNull ResponseEntity<SnippetDetail> update(
      final @NonNull @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable final @NonNull UUID snippetId,
      @Valid @RequestBody final @NonNull SnippetUpdateForm form) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(this.snippetService.update(tenantId, snippetId, form));
  }

  @DeleteMapping("/{snippetId}")
  public @NonNull ResponseEntity<Void> delete(
      final @NonNull @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable final @NonNull UUID snippetId) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    this.snippetService.delete(tenantId, snippetId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{snippetId}/fork")
  public @NonNull ResponseEntity<SnippetDetail> fork(
      final @NonNull @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader,
      @PathVariable final @NonNull UUID snippetId) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.snippetService.fork(tenantId, snippetId));
  }

  @GetMapping("/{snippetId}/revisions")
  public @NonNull ResponseEntity<PageResponse<SnippetRevisionInfo>> listRevisions(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader,
      @PathVariable final @NonNull UUID snippetId,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "10") final int size) {

    final var callerId = this.extractOptionalCaller(authHeader);
    return ResponseEntity.ok(this.snippetService.listRevisions(snippetId, callerId, page, size));
  }

  @GetMapping("/{snippetId}/revisions/{revisionId}")
  public @NonNull ResponseEntity<SnippetRevisionDetail> getRevision(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader,
      @PathVariable final @NonNull UUID snippetId,
      @PathVariable final @NonNull UUID revisionId) {

    final var callerId = this.extractOptionalCaller(authHeader);
    return ResponseEntity.ok(this.snippetService.getRevision(snippetId, revisionId, callerId));
  }

  @GetMapping("/{snippetId}/files/{fileId}/raw")
  public @NonNull ResponseEntity<String> getRawFile(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader,
      @PathVariable final @NonNull UUID snippetId,
      @PathVariable final @NonNull UUID fileId) {

    final var callerId = this.extractOptionalCaller(authHeader);
    final var detail = this.snippetService.get(snippetId, callerId);

    final var file =
        detail.files().stream()
            .filter(f -> f.id().equals(fileId))
            .findFirst()
            .orElseThrow(
                () ->
                    new com.nuricanozturk.originhub.shared.errorhandling.exceptions
                        .ItemNotFoundException("fileNotFound"));

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
