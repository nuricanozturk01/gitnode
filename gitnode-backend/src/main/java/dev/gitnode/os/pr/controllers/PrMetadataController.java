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

package dev.gitnode.os.pr.controllers;

import dev.gitnode.os.pr.dtos.PrMetadata;
import dev.gitnode.os.pr.services.PrMetadataService;
import dev.gitnode.os.shared.auth.services.JwtUtils;
import dev.gitnode.os.shared.repo.services.RepoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repos/{owner}/{repo}/pulls/{number}/metadata")
@RequiredArgsConstructor
@NullMarked
public class PrMetadataController {

  private final PrMetadataService service;
  private final JwtUtils jwtUtils;
  private final RepoService repoService;

  @GetMapping
  public ResponseEntity<PrMetadata> get(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader) {

    final var viewerId = authHeader != null ? this.jwtUtils.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(viewerId, owner, repo);
    return ResponseEntity.ok(this.service.get(owner, repo, number, viewerId));
  }

  @PutMapping
  public ResponseEntity<PrMetadata> update(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @Valid @RequestBody final PrMetadata.Form form) {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    return ResponseEntity.ok(this.service.update(owner, repo, number, requesterId, form));
  }
}
