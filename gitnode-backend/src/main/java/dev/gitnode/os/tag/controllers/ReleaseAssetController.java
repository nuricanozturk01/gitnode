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

package dev.gitnode.os.tag.controllers;

import dev.gitnode.os.shared.auth.services.JwtUtils;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.ratelimit.RateLimit;
import dev.gitnode.os.shared.repo.services.RepoService;
import dev.gitnode.os.tag.dtos.ReleaseAssetInfo;
import dev.gitnode.os.tag.services.ReleaseAssetService;
import dev.gitnode.os.tag.services.ReleaseTxService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Binary files attached to releases. Drafts and their files are visible to maintainers only. */
@RestController
@RequestMapping("/api/repos/{owner}/{repo}/releases/{releaseId}/assets")
@RequiredArgsConstructor
@NullMarked
public class ReleaseAssetController {

  private final ReleaseAssetService assets;
  private final ReleaseTxService releases;
  private final JwtUtils jwtUtils;
  private final RepoService repoService;

  @GetMapping
  public ResponseEntity<List<ReleaseAssetInfo>> list(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID releaseId,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader) {
    this.assertVisible(owner, repo, releaseId, authHeader);
    return ResponseEntity.ok(this.assets.list(releaseId));
  }

  @GetMapping("/{assetId}")
  public ResponseEntity<Resource> download(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID releaseId,
      @PathVariable final UUID assetId,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader) {
    this.assertVisible(owner, repo, releaseId, authHeader);
    final var download = this.assets.open(releaseId, assetId);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(download.info().name(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .contentLength(download.info().size())
        .body(download.resource());
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RateLimit(limit = 60, windowSeconds = 3600, key = "release.asset.upload")
  public ResponseEntity<ReleaseAssetInfo> upload(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID releaseId,
      @RequestParam("file") final MultipartFile file,
      @RequestParam(value = "name", required = false) final @Nullable String name,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {
    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    this.releases.assertCanManage(owner, repo, requesterId);
    this.releases.getById(owner, repo, releaseId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.assets.upload(releaseId, requesterId, file, name));
  }

  @DeleteMapping("/{assetId}")
  public ResponseEntity<Void> delete(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID releaseId,
      @PathVariable final UUID assetId,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {
    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    this.releases.assertCanManage(owner, repo, requesterId);
    this.releases.getById(owner, repo, releaseId);
    this.assets.delete(releaseId, assetId);
    return ResponseEntity.noContent().build();
  }

  private void assertVisible(
      final String owner,
      final String repo,
      final UUID releaseId,
      final @Nullable String authHeader) {
    final var requesterId = authHeader != null ? this.jwtUtils.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    final var release = this.releases.getById(owner, repo, releaseId);
    if (release.isDraft() && !this.releases.canManage(owner, repo, requesterId)) {
      throw new ItemNotFoundException("Release not found.");
    }
  }
}
