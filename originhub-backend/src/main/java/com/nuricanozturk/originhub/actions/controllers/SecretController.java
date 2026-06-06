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
package com.nuricanozturk.originhub.actions.controllers;

import com.nuricanozturk.originhub.actions.dtos.response.SecretResponse;
import com.nuricanozturk.originhub.actions.services.SecretVaultService;
import com.nuricanozturk.originhub.shared.auth.services.JwtUtils;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repos/{owner}/{repo}/actions/secrets")
@RequiredArgsConstructor
@Validated
@NullMarked
public class SecretController {

  private final SecretVaultService secretVaultService;
  private final RepoRepository repoRepository;
  private final JwtUtils jwtUtils;

  @GetMapping
  public ResponseEntity<List<SecretResponse>> list(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo) {

    this.jwtUtils.extractUserId(authHeader);
    final UUID repoId = this.requireRepoId(owner, repo);
    final var names = this.secretVaultService.listNames(repoId);
    return ResponseEntity.ok(names.stream().map(SecretResponse::new).toList());
  }

  @PutMapping("/{name}")
  public ResponseEntity<Void> createOrUpdate(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final String name,
      @RequestBody final Map<String, @NotBlank String> body) {

    this.jwtUtils.extractUserId(authHeader);
    final UUID repoId = this.requireRepoId(owner, repo);
    this.secretVaultService.createOrUpdate(repoId, name, body.get("value"));
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{name}")
  public ResponseEntity<Void> delete(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final String name) {

    this.jwtUtils.extractUserId(authHeader);
    final UUID repoId = this.requireRepoId(owner, repo);
    this.secretVaultService.delete(repoId, name);
    return ResponseEntity.noContent().build();
  }

  private UUID requireRepoId(final String owner, final String repo) {
    return this.repoRepository
        .findByOwnerUsernameAndName(owner, repo)
        .map(Repo::getId)
        .orElseThrow(
            () -> new ItemNotFoundException("Repository not found: " + owner + "/" + repo));
  }
}
