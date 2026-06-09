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
package dev.gitnode.os.actions.controllers;

import dev.gitnode.os.actions.dtos.response.ArtifactResponse;
import dev.gitnode.os.actions.entities.WorkflowArtifact;
import dev.gitnode.os.actions.services.ArtifactStoreService;
import dev.gitnode.os.actions.services.CacheStoreService;
import dev.gitnode.os.shared.auth.services.JwtUtils;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@NullMarked
public class ArtifactController {

  private final ArtifactStoreService artifactStoreService;
  private final CacheStoreService cacheStoreService;
  private final JwtUtils jwtUtils;

  @PostMapping("/api/actions/artifacts/upload")
  public ResponseEntity<ArtifactResponse> upload(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @RequestParam final UUID runId,
      @RequestParam @Nullable final UUID jobId,
      @RequestParam final String name,
      @RequestParam(defaultValue = "30") final int retentionDays,
      @RequestPart("file") final MultipartFile file) {

    this.jwtUtils.extractUserId(authHeader);
    final var artifact = this.artifactStoreService.upload(runId, jobId, name, file, retentionDays);
    return ResponseEntity.ok(this.toResponse(artifact));
  }

  @GetMapping("/api/actions/artifacts/{runId}/{name}")
  public ResponseEntity<Resource> download(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final UUID runId,
      @PathVariable final String name) {

    this.jwtUtils.extractUserId(authHeader);
    final Resource resource = this.artifactStoreService.download(runId, name);

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(name + ".tar.gz").build().toString())
        .body(resource);
  }

  record PageResponse<T>(List<T> content, long totalElements, int totalPages, int page, int size) {}

  @GetMapping("/api/repos/{owner}/{repo}/actions/runs/{runId}/artifacts")
  public ResponseEntity<PageResponse<ArtifactResponse>> listByRun(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID runId,
      @PageableDefault(size = 20) final Pageable pageable) {

    this.jwtUtils.extractUserId(authHeader);
    final var page = this.artifactStoreService.listByRun(runId, pageable).map(this::toResponse);
    return ResponseEntity.ok(
        new PageResponse<>(
            page.getContent(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.getSize()));
  }

  @GetMapping("/api/actions/cache")
  public ResponseEntity<Resource> getCache(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @RequestParam final UUID repoId,
      @RequestParam final String key,
      @RequestParam(required = false) @Nullable final List<String> restoreKeys) {

    this.jwtUtils.extractUserId(authHeader);
    final Optional<Resource> hit = this.cacheStoreService.get(repoId, key, restoreKeys);

    return hit.map(r -> ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(r))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cache miss"));
  }

  @PostMapping("/api/actions/cache")
  public ResponseEntity<Void> putCache(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @RequestParam final UUID repoId,
      @RequestParam final String key,
      @RequestParam(required = false) @Nullable final List<String> restoreKeys,
      @RequestPart("file") final MultipartFile file) {

    this.jwtUtils.extractUserId(authHeader);
    this.cacheStoreService.put(repoId, key, restoreKeys, file);
    return ResponseEntity.noContent().build();
  }

  private ArtifactResponse toResponse(final WorkflowArtifact a) {
    return new ArtifactResponse(
        a.getId(),
        a.getName(),
        a.getSizeBytes(),
        a.getContentType(),
        a.getExpiresAt(),
        a.getCreatedAt());
  }
}
