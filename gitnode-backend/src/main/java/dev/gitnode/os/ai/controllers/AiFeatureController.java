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
package dev.gitnode.os.ai.controllers;

import dev.gitnode.os.ai.dtos.AiCodeReviewDto;
import dev.gitnode.os.ai.dtos.CodebaseAnalysisDto;
import dev.gitnode.os.ai.dtos.CommitSuggestionRequest;
import dev.gitnode.os.ai.dtos.CommitSuggestionResponse;
import dev.gitnode.os.ai.dtos.PrDescriptionRequest;
import dev.gitnode.os.ai.dtos.PrDescriptionResponse;
import dev.gitnode.os.ai.services.AiCodeReviewService;
import dev.gitnode.os.ai.services.CodebaseAnalysisService;
import dev.gitnode.os.ai.services.CommitSuggestionService;
import dev.gitnode.os.ai.services.PrDescriptionService;
import dev.gitnode.os.shared.auth.services.JwtUtils;
import dev.gitnode.os.shared.ratelimit.RateLimit;
import dev.gitnode.os.shared.repo.dtos.PageResponse;
import dev.gitnode.os.shared.repo.services.RepoService;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@NullMarked
public class AiFeatureController {

  private final CommitSuggestionService commitSuggestionService;
  private final PrDescriptionService prDescriptionService;
  private final AiCodeReviewService codeReviewService;
  private final CodebaseAnalysisService codebaseAnalysisService;
  private final JwtUtils jwtUtils;
  private final RepoService repoService;

  @PostMapping("/suggest/commit-message")
  @RateLimit(limit = 20, windowSeconds = 600, key = "ai.commit-suggest")
  public ResponseEntity<CommitSuggestionResponse> suggestCommitMessage(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @Valid @RequestBody final CommitSuggestionRequest request) {
    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(this.commitSuggestionService.suggest(tenantId, request));
  }

  @PostMapping("/suggest/pr-description")
  @RateLimit(limit = 10, windowSeconds = 600, key = "ai.pr-desc")
  public ResponseEntity<PrDescriptionResponse> suggestPrDescription(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @Valid @RequestBody final PrDescriptionRequest request)
      throws IOException {
    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(tenantId, request.owner(), request.repo());
    return ResponseEntity.ok(this.prDescriptionService.generate(tenantId, request));
  }

  @PostMapping("/repos/{owner}/{repo}/analysis")
  @RateLimit(limit = 3, windowSeconds = 3600, key = "ai.analysis")
  public ResponseEntity<CodebaseAnalysisDto> triggerAnalysis(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @RequestParam(defaultValue = "") final String branch) {
    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(tenantId, owner, repo);
    final var repoInfo = this.repoService.findByOwnerAndName(owner, repo, tenantId);
    final var effectiveBranch = branch.isBlank() ? repoInfo.getDefaultBranch() : branch;
    final var result =
        this.codebaseAnalysisService.trigger(
            repoInfo.getId(), owner, repo, effectiveBranch, tenantId);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
  }

  @GetMapping("/repos/{owner}/{repo}/analysis")
  public ResponseEntity<CodebaseAnalysisDto> getLatestAnalysis(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {
    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(tenantId, owner, repo);
    final var repoInfo = this.repoService.findByOwnerAndName(owner, repo, tenantId);
    return this.codebaseAnalysisService
        .getLatest(repoInfo.getId())
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/repos/{owner}/{repo}/analysis/history")
  public ResponseEntity<PageResponse<CodebaseAnalysisDto>> listAnalyses(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "10") final int size) {
    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(tenantId, owner, repo);
    final var repoInfo = this.repoService.findByOwnerAndName(owner, repo, tenantId);
    return ResponseEntity.ok(this.codebaseAnalysisService.list(repoInfo.getId(), page, size));
  }

  @GetMapping("/repos/{owner}/{repo}/pulls/{number}/review")
  public ResponseEntity<AiCodeReviewDto> getCodeReview(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size) {
    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(tenantId, owner, repo);
    final var repoEntity = this.repoService.findByOwnerAndName(owner, repo, tenantId);
    return this.codeReviewService
        .findReview(repoEntity.getId(), number, page, size)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PostMapping("/repos/{owner}/{repo}/pulls/{number}/review/retry")
  @RateLimit(limit = 5, windowSeconds = 600, key = "ai.pr-review-retry")
  public ResponseEntity<AiCodeReviewDto> retryCodeReview(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {
    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(tenantId, owner, repo);
    final var repoEntity = this.repoService.findByOwnerAndName(owner, repo, tenantId);
    final var review =
        this.codeReviewService.retryReview(
            repoEntity.getId(), owner, repo, number, tenantId, repoEntity.getOwner().getId());
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(review);
  }
}
