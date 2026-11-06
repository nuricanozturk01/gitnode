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

import dev.gitnode.os.identity.api.TokenUserResolver;
import dev.gitnode.os.repo.api.RepoAccessPort;
import dev.gitnode.os.shared.pagination.ListPaging;
import dev.gitnode.os.shared.ratelimit.RateLimit;
import dev.gitnode.os.tag.dtos.CreateTagForm;
import dev.gitnode.os.tag.dtos.TagInfo;
import dev.gitnode.os.tag.services.ReleaseTxService;
import dev.gitnode.os.tag.services.TagNonTxService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
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
@RequestMapping("/api/repos/{owner}/{repo}/tags")
@RequiredArgsConstructor
@NullMarked
public class TagController {

  private static final int DEFAULT_LIST_MAX = 1000;

  @Value("${gitnode.limits.list-max-items:1000}")
  private int listMax = DEFAULT_LIST_MAX;

  private final TagNonTxService tagNonTxService;
  private final TokenUserResolver jwtUtils;
  private final RepoAccessPort repoService;
  private final ReleaseTxService releaseTxService;

  @GetMapping
  public ResponseEntity<List<TagInfo>> getAll(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestParam(required = false) final @Nullable Integer page,
      @RequestParam(required = false) final @Nullable Integer size,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader)
      throws IOException {

    final var requesterId = authHeader != null ? this.jwtUtils.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    final var tags = this.tagNonTxService.getAll(owner, repo);
    final var visible =
        this.releaseTxService.canManage(owner, repo, requesterId)
            ? tags
            : tags.stream().map(TagInfo::withoutDraftRelease).toList();
    return ListPaging.respond(visible, page, size, this.listMax);
  }

  @GetMapping("/{tag}")
  public ResponseEntity<TagInfo> get(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final String tag,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader)
      throws IOException {

    final var requesterId = authHeader != null ? this.jwtUtils.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    final var result = this.tagNonTxService.get(owner, repo, tag);
    return ResponseEntity.ok(
        this.releaseTxService.canManage(owner, repo, requesterId)
            ? result
            : result.withoutDraftRelease());
  }

  @PostMapping
  @RateLimit(limit = 50, windowSeconds = 3600, key = "tag.create")
  public ResponseEntity<TagInfo> create(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @Valid @RequestBody final CreateTagForm form)
      throws IOException {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    this.releaseTxService.assertCanManage(owner, repo, requesterId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.tagNonTxService.create(owner, repo, form));
  }

  @DeleteMapping("/{tag}")
  public ResponseEntity<Void> delete(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final String tag,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader)
      throws IOException {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    this.releaseTxService.assertCanManage(owner, repo, requesterId);
    this.tagNonTxService.delete(owner, repo, tag);
    return ResponseEntity.noContent().build();
  }
}
