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
package com.nuricanozturk.originhub.branch.controllers;

import com.nuricanozturk.originhub.branch.dtos.DefaultBranchForm;
import com.nuricanozturk.originhub.branch.services.BranchNonTxService;
import com.nuricanozturk.originhub.shared.auth.services.JwtUtils;
import com.nuricanozturk.originhub.shared.branch.dtos.BranchForm;
import com.nuricanozturk.originhub.shared.branch.dtos.BranchInfo;
import com.nuricanozturk.originhub.shared.repo.services.RepoService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
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
@RequestMapping("/api/repos/{owner}/{repo}/branches")
@RequiredArgsConstructor
@NullMarked
public class BranchController {

  private final BranchNonTxService branchNonTxService;
  private final JwtUtils jwtUtils;
  private final RepoService repoService;

  @PostMapping
  public ResponseEntity<BranchInfo> create(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @Valid @RequestBody final BranchForm form)
      throws IOException {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    final var branch = this.branchNonTxService.create(owner, repo, form);
    return ResponseEntity.status(HttpStatus.CREATED).body(branch);
  }

  @DeleteMapping("/{branch}")
  public ResponseEntity<Void> delete(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final String branch,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader)
      throws IOException {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    this.branchNonTxService.delete(owner, repo, branch);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/default")
  public ResponseEntity<Void> setDefaultBranch(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @Valid @RequestBody final DefaultBranchForm form)
      throws IOException {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    this.branchNonTxService.setDefaultBranch(owner, repo, form.getBranchName());
    return ResponseEntity.noContent().build();
  }

  @GetMapping
  public ResponseEntity<List<BranchInfo>> getAll(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader)
      throws IOException {

    final var requesterId = authHeader != null ? this.jwtUtils.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    final var branches = this.branchNonTxService.getAll(owner, repo);
    return ResponseEntity.ok(branches);
  }

  @GetMapping("/{branch}")
  public ResponseEntity<BranchInfo> get(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final String branch,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader)
      throws IOException {

    final var requesterId = authHeader != null ? this.jwtUtils.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    final var response = this.branchNonTxService.get(owner, repo, branch);
    return ResponseEntity.ok(response);
  }
}
