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
package dev.gitnode.os.wiki.controllers;

import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.identity.services.JwtUtils;
import dev.gitnode.os.repo.api.CollaboratorPermission;
import dev.gitnode.os.repo.api.RepoAccessPort;
import dev.gitnode.os.repo.dtos.PageResponse;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.ratelimit.RateLimit;
import dev.gitnode.os.wiki.dtos.WikiPage;
import dev.gitnode.os.wiki.dtos.WikiPageForm;
import dev.gitnode.os.wiki.dtos.WikiPageSummary;
import dev.gitnode.os.wiki.dtos.WikiRevision;
import dev.gitnode.os.wiki.services.WikiService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repos/{owner}/{repo}/wiki/pages")
@RequiredArgsConstructor
@NullMarked
public class WikiController {

  private final WikiService wiki;
  private final JwtUtils jwtUtils;
  private final RepoAccessPort repoAccess;
  private final TenantQueryPort tenants;

  @GetMapping
  public ResponseEntity<PageResponse<WikiPageSummary>> list(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestParam(defaultValue = "0") final int page,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String auth)
      throws IOException {
    this.assertCanRead(auth, owner, repo);
    return ResponseEntity.ok(this.wiki.list(owner, repo, page));
  }

  @GetMapping("/{slug}")
  public ResponseEntity<WikiPage> get(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final String slug,
      @RequestParam(required = false) final @Nullable String rev,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String auth)
      throws IOException {
    this.assertCanRead(auth, owner, repo);
    return ResponseEntity.ok(this.wiki.get(owner, repo, slug, rev));
  }

  @GetMapping("/{slug}/history")
  public ResponseEntity<PageResponse<WikiRevision>> history(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final String slug,
      @RequestParam(defaultValue = "0") final int page,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String auth)
      throws IOException {
    this.assertCanRead(auth, owner, repo);
    return ResponseEntity.ok(this.wiki.history(owner, repo, slug, page));
  }

  @PutMapping("/{slug}")
  @RateLimit(limit = 300, windowSeconds = 600, key = "wiki.save")
  public ResponseEntity<WikiPage> save(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final String slug,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth,
      @Valid @RequestBody final WikiPageForm form)
      throws IOException {
    final var editor = this.editor(auth, owner, repo);
    return ResponseEntity.ok(
        this.wiki.save(owner, repo, slug, form.content(), form.message(), form.baseSha(), editor));
  }

  @DeleteMapping("/{slug}")
  public ResponseEntity<Void> delete(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final String slug,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth)
      throws IOException {
    this.wiki.delete(owner, repo, slug, null, this.editor(auth, owner, repo));
    return ResponseEntity.noContent().build();
  }

  private void assertCanRead(final @Nullable String auth, final String owner, final String repo) {
    final UUID userId = auth == null ? null : this.jwtUtils.extractUserId(auth);
    this.repoAccess.assertUserCanAccessRepo(userId, owner, repo);
  }

  /** Writing needs push access, like editing files; returns who the commit is attributed to. */
  private WikiService.Editor editor(final String auth, final String owner, final String repo) {
    final var userId = this.jwtUtils.extractUserId(auth);
    this.repoAccess.assertUserHasPermission(userId, owner, repo, CollaboratorPermission.PUSH);
    final var user =
        this.tenants
            .findById(userId)
            .orElseThrow(() -> new ItemNotFoundException("User not found"));
    return new WikiService.Editor(user.nameForDisplay(), user.email());
  }
}
