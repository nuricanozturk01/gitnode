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
package com.nuricanozturk.originhub.commit.controllers;

import com.nuricanozturk.originhub.commit.services.CommitNonTxService;
import com.nuricanozturk.originhub.shared.auth.services.JwtUtils;
import com.nuricanozturk.originhub.shared.commit.dtos.CommitDetail;
import com.nuricanozturk.originhub.shared.commit.dtos.CommitInfo;
import com.nuricanozturk.originhub.shared.commit.dtos.FileDiff;
import com.nuricanozturk.originhub.shared.commit.dtos.PagedResult;
import com.nuricanozturk.originhub.shared.repo.services.RepoService;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repos/{owner}/{repo}/commits")
@RequiredArgsConstructor
@NullMarked
public class CommitController {

  private final CommitNonTxService commitNonTxService;
  private final JwtUtils jwtUtils;
  private final RepoService repoService;

  @GetMapping
  public ResponseEntity<PagedResult<CommitInfo>> getCommits(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestParam(defaultValue = "master") final String branch,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authHeader)
      throws IOException {

    final var requesterId = authHeader != null ? this.jwtUtils.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    final var commits = this.commitNonTxService.getCommits(owner, repo, branch, page, size);
    return ResponseEntity.ok(commits);
  }

  @GetMapping("/{sha}")
  public ResponseEntity<CommitDetail> getCommit(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final String sha,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authHeader)
      throws IOException {

    final var requesterId = authHeader != null ? this.jwtUtils.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    final var commit = this.commitNonTxService.getCommit(owner, repo, sha);
    return ResponseEntity.ok(commit);
  }

  @GetMapping("/{sha}/diff")
  public ResponseEntity<List<FileDiff>> getCommitDiff(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final String sha,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authHeader)
      throws IOException {

    final var requesterId = authHeader != null ? this.jwtUtils.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    final var diff = this.commitNonTxService.getCommitDiff(owner, repo, sha);
    return ResponseEntity.ok(diff);
  }
}
