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
package com.nuricanozturk.originhub.tree.controllers;

import com.nuricanozturk.originhub.shared.auth.services.JwtUtils;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.services.RepoService;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import com.nuricanozturk.originhub.tree.dtos.BlobResponse;
import com.nuricanozturk.originhub.tree.dtos.LanguageStats;
import com.nuricanozturk.originhub.tree.dtos.TreeResponse;
import com.nuricanozturk.originhub.tree.dtos.UpdateFileRequest;
import com.nuricanozturk.originhub.tree.services.LanguageService;
import com.nuricanozturk.originhub.tree.services.TreeNonTxService;
import com.nuricanozturk.originhub.tree.utils.ArchivePathSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.PersonIdent;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.util.UriUtils;

@Slf4j
@RestController
@RequestMapping("/api/repos/{owner}/{repo}")
@RequiredArgsConstructor
public class TreeController {

  private static final @NonNull String TREE = "tree";
  private static final @NonNull String BLOB = "blob";
  private static final @NonNull String RAW = "raw";

  private final @NonNull TreeNonTxService treeNonTxService;
  private final @NonNull LanguageService languageService;
  private final @NonNull RepoService repoService;
  private final @NonNull TenantRepository tenantRepository;
  private final JwtUtils jwtUtils;

  @GetMapping({"/tree/{branch}", "/tree/{branch}/**"})
  public @NonNull ResponseEntity<@NonNull TreeResponse> getTree(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String repo,
      @PathVariable final @NonNull String branch,
      final @NonNull HttpServletRequest request)
      throws IOException {

    final var path = this.extractPath(request, branch, TREE);

    final var treeResponse = this.treeNonTxService.getTree(owner, repo, branch, path);

    return ResponseEntity.ok(treeResponse);
  }

  @GetMapping("/blob/{branch}/**")
  public @NonNull ResponseEntity<@NonNull BlobResponse> getBlob(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String repo,
      @PathVariable final @NonNull String branch,
      final @NonNull HttpServletRequest request)
      throws IOException {

    final var path = this.extractPath(request, branch, BLOB);

    final var blobResponse = this.treeNonTxService.getBlob(owner, repo, branch, path);

    return ResponseEntity.ok(blobResponse);
  }

  @GetMapping("/raw/{branch}/**")
  public ResponseEntity<byte[]> getRaw(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String repo,
      @PathVariable final @NonNull String branch,
      final @NonNull HttpServletRequest request)
      throws IOException {

    final var path = this.extractPath(request, branch, RAW);

    final var content = this.treeNonTxService.getRawContent(owner, repo, branch, path);

    final var fileName = Path.of(path).getFileName().toString();

    final var mediaType = MediaTypeFactory.getMediaType(fileName).orElse(MediaType.TEXT_PLAIN);

    return ResponseEntity.ok().contentType(mediaType).body(content);
  }

  @GetMapping("/archive/{branch}")
  public @NonNull ResponseEntity<@NonNull StreamingResponseBody> downloadBranchArchive(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authHeader,
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String repo,
      @PathVariable final @NonNull String branch)
      throws IOException {

    final var userId = authHeader != null ? this.jwtUtils.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(userId, owner, repo);
    this.treeNonTxService.assertBranchExists(owner, repo, branch);

    final var attachmentName = ArchivePathSupport.attachmentFileName(owner, repo, branch);

    final StreamingResponseBody body =
        outputStream -> {
          try {
            this.treeNonTxService.writeBranchZip(owner, repo, branch, outputStream);
          } catch (final Exception e) {
            log.error("Error writing ZIP for {}/{}/{}: {}", owner, repo, branch, e.getMessage(), e);
          }
        };

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachmentName + "\"")
        .contentType(MediaType.parseMediaType("application/zip"))
        .body(body);
  }

  @GetMapping("/languages")
  public @NonNull ResponseEntity<List<LanguageStats>> getLanguages(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String repo,
      @RequestParam(defaultValue = "main") final @NonNull String branch,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authHeader)
      throws IOException {

    final var requesterId = authHeader != null ? this.jwtUtils.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    return ResponseEntity.ok(this.languageService.detectLanguages(owner, repo, branch));
  }

  @PutMapping(
      value = {"/blob/{branch}", "/blob/{branch}/**"},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public @NonNull ResponseEntity<@NonNull BlobResponse> updateFile(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String repo,
      @PathVariable final @NonNull String branch,
      final @NonNull HttpServletRequest request,
      @RequestBody @Valid final @NonNull UpdateFileRequest body,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final @NonNull String authHeader)
      throws IOException {

    final var userId = this.jwtUtils.extractUserId(authHeader);
    final var tenant =
        this.tenantRepository
            .findById(userId)
            .orElseThrow(() -> new ItemNotFoundException("userNotFound"));

    final var path = this.extractPath(request, branch, BLOB);

    final var rawContent = body.content().getBytes(StandardCharsets.UTF_8);
    final var fullMessage =
        body.commitDescription() != null && !body.commitDescription().isBlank()
            ? body.commitMessage() + "\n\n" + body.commitDescription()
            : body.commitMessage();

    final var authorName =
        tenant.getDisplayName() != null ? tenant.getDisplayName() : tenant.getUsername();
    final var author =
        new PersonIdent(authorName, tenant.getEmail(), Instant.now(), ZoneOffset.UTC);

    final var result =
        this.treeNonTxService.updateFile(
            owner, repo, branch, path, rawContent, fullMessage, author);

    return ResponseEntity.ok(result);
  }

  private @NonNull String extractPath(
      final @NonNull HttpServletRequest request,
      final @NonNull String branch,
      final @NonNull String type) {

    final var uri = UriUtils.decode(request.getRequestURI(), StandardCharsets.UTF_8);
    final var marker = "/" + type + "/" + branch + "/";
    final var idx = uri.indexOf(marker);

    if (idx == -1) {
      return "";
    }

    return uri.substring(idx + marker.length());
  }
}
