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
import dev.gitnode.os.tag.dtos.CreateReleaseForm;
import dev.gitnode.os.tag.dtos.CreateTagForm;
import dev.gitnode.os.tag.dtos.ReleaseInfo;
import dev.gitnode.os.tag.dtos.UpdateReleaseForm;
import dev.gitnode.os.tag.services.ReleaseTxService;
import dev.gitnode.os.tag.services.TagNonTxService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repos/{owner}/{repo}/releases")
@RequiredArgsConstructor
@NullMarked
public class ReleaseController {

  private final ReleaseTxService releaseTxService;
  private final TagNonTxService tagNonTxService;
  private final JwtUtils jwtUtils;
  private final RepoService repoService;

  @GetMapping
  public ResponseEntity<List<ReleaseInfo>> getAll(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader) {

    final var requesterId = authHeader != null ? this.jwtUtils.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    return ResponseEntity.ok(this.releaseTxService.getAll(owner, repo));
  }

  @GetMapping("/latest")
  public ResponseEntity<ReleaseInfo> getLatest(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader) {

    final var requesterId = authHeader != null ? this.jwtUtils.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    return this.releaseTxService
        .findLatest(owner, repo)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/tag/{tagName}")
  public ResponseEntity<ReleaseInfo> getByTag(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final String tagName,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader) {

    final var requesterId = authHeader != null ? this.jwtUtils.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    return this.releaseTxService
        .findByTagName(owner, repo, tagName)
        .map(ResponseEntity::ok)
        .orElseThrow(() -> new ItemNotFoundException("releaseNotFound: " + tagName));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ReleaseInfo> getById(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID id,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader) {

    final var requesterId = authHeader != null ? this.jwtUtils.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    return ResponseEntity.ok(this.releaseTxService.getById(id));
  }

  @PostMapping
  @RateLimit(limit = 30, windowSeconds = 3600, key = "release.create")
  public ResponseEntity<ReleaseInfo> create(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @Valid @RequestBody final CreateReleaseForm form)
      throws IOException {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);

    if (form.isCreateNewTag()) {
      this.createTagIfNeeded(owner, repo, form);
    }

    final var release = this.releaseTxService.create(owner, repo, requesterId, form);
    return ResponseEntity.status(HttpStatus.CREATED).body(release);
  }

  @PatchMapping("/{id}")
  public ResponseEntity<ReleaseInfo> update(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID id,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @RequestBody final UpdateReleaseForm form) {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    return ResponseEntity.ok(this.releaseTxService.update(id, requesterId, owner, form));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID id,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    this.releaseTxService.delete(id, requesterId, owner);
    return ResponseEntity.noContent().build();
  }

  private void createTagIfNeeded(
      final String owner, final String repoName, final CreateReleaseForm form) throws IOException {

    final var tagForm = new CreateTagForm();
    tagForm.setName(form.getTagName());
    tagForm.setSha(form.getTargetCommitish());
    tagForm.setMessage(form.getTagMessage());
    this.tagNonTxService.create(owner, repoName, tagForm);
  }
}
