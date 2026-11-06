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

import dev.gitnode.os.identity.api.TokenUserResolver;
import dev.gitnode.os.pr.dtos.MergeStrategy;
import dev.gitnode.os.pr.dtos.PrMergeRequirements;
import dev.gitnode.os.pr.services.PrAutoMergeService;
import dev.gitnode.os.repo.api.CollaboratorPermission;
import dev.gitnode.os.repo.api.RepoAccessPort;
import dev.gitnode.os.shared.ratelimit.RateLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repos/{owner}/{repo}/pulls/{number}/auto-merge")
@RequiredArgsConstructor
@NullMarked
public class PrAutoMergeController {

  private final TokenUserResolver tokenService;
  private final RepoAccessPort repoService;
  private final PrAutoMergeService autoMerge;

  public record AutoMergeForm(@NotNull MergeStrategy strategy) {}

  @PutMapping
  @RateLimit(limit = 50, windowSeconds = 600, key = "pr.auto-merge")
  public ResponseEntity<PrMergeRequirements> enable(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @Valid @RequestBody final AutoMergeForm form)
      throws IOException {
    final var requesterId = this.tokenService.extractUserId(authHeader);
    this.repoService.assertUserHasPermission(
        requesterId, owner, repo, CollaboratorPermission.PULL_REQUEST_MERGE);
    return ResponseEntity.ok(
        this.autoMerge.enable(owner, repo, number, requesterId, form.strategy()));
  }

  @DeleteMapping
  public ResponseEntity<PrMergeRequirements> disable(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader)
      throws IOException {
    final var requesterId = this.tokenService.extractUserId(authHeader);
    this.repoService.assertUserHasPermission(
        requesterId, owner, repo, CollaboratorPermission.PULL_REQUEST_MERGE);
    return ResponseEntity.ok(this.autoMerge.disable(owner, repo, number, requesterId));
  }
}
