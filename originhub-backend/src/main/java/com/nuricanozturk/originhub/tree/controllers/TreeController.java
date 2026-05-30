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
import com.nuricanozturk.originhub.shared.repo.services.RepoService;
import com.nuricanozturk.originhub.tree.dtos.BlobResponse;
import com.nuricanozturk.originhub.tree.dtos.TreeResponse;
import com.nuricanozturk.originhub.tree.services.TreeNonTxService;
import com.nuricanozturk.originhub.tree.utils.ArchivePathSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
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
  private final @NonNull RepoService repoService;
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
    final var disposition =
        ContentDisposition.attachment().filename(attachmentName, StandardCharsets.UTF_8).build();

    final StreamingResponseBody body =
        outputStream -> {
          try {
            this.treeNonTxService.writeBranchZip(owner, repo, branch, outputStream);
          } catch (final Exception e) {
            log.error("Error writing ZIP for {}/{}/{}: {}", owner, repo, branch, e.getMessage(), e);
          }
        };

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .contentType(MediaType.parseMediaType("application/zip"))
        .body(body);
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
