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
import com.nuricanozturk.originhub.shared.branch.dtos.BranchForm;
import com.nuricanozturk.originhub.shared.branch.dtos.BranchInfo;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repos/{owner}/{repo}/branches")
@RequiredArgsConstructor
public class BranchController {

  private final @NonNull BranchNonTxService branchNonTxService;

  @PostMapping
  public @NonNull ResponseEntity<BranchInfo> create(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String repo,
      @Valid @RequestBody final @NonNull BranchForm form)
      throws IOException {

    final var branch = this.branchNonTxService.create(owner, repo, form);

    return ResponseEntity.status(HttpStatus.CREATED).body(branch);
  }

  @DeleteMapping("/{branch}")
  public @NonNull ResponseEntity<Void> delete(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String repo,
      @PathVariable final @NonNull String branch)
      throws IOException {

    this.branchNonTxService.delete(owner, repo, branch);

    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/default")
  public @NonNull ResponseEntity<Void> setDefaultBranch(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String repo,
      @Valid @RequestBody final @NonNull DefaultBranchForm form)
      throws IOException {

    this.branchNonTxService.setDefaultBranch(owner, repo, form.getBranchName());

    return ResponseEntity.noContent().build();
  }

  @GetMapping
  public @NonNull ResponseEntity<List<BranchInfo>> getAll(
      @PathVariable final @NonNull String owner, @PathVariable final @NonNull String repo)
      throws IOException {

    final var branches = this.branchNonTxService.getAll(owner, repo);

    return ResponseEntity.ok(branches);
  }

  @GetMapping("/{branch}")
  public @NonNull ResponseEntity<BranchInfo> get(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String repo,
      @PathVariable final @NonNull String branch)
      throws IOException {

    final var response = this.branchNonTxService.get(owner, repo, branch);

    return ResponseEntity.ok(response);
  }
}
