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

import com.nuricanozturk.originhub.actions.dtos.request.RunnerHeartbeatRequest;
import com.nuricanozturk.originhub.actions.dtos.request.RunnerRegistrationRequest;
import com.nuricanozturk.originhub.actions.dtos.response.RegistrationTokenResponse;
import com.nuricanozturk.originhub.actions.dtos.response.RunnerRegistrationResponse;
import com.nuricanozturk.originhub.actions.dtos.response.RunnerResponse;
import com.nuricanozturk.originhub.actions.services.RunnerRegistryService;
import com.nuricanozturk.originhub.shared.auth.services.JwtUtils;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.ratelimit.RateLimit;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@NullMarked
public class RunnerController {

  private final RunnerRegistryService runnerRegistryService;
  private final JwtUtils jwtUtils;
  private final RepoRepository repoRepository;

  /** Generate a one-time registration token (requires repo owner auth). */
  @PostMapping("/repos/{owner}/{repo}/actions/runners/registration-token")
  @RateLimit(limit = 10, windowSeconds = 3600, key = "runner.reg-token")
  public ResponseEntity<RegistrationTokenResponse> createRegistrationToken(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo) {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    final var repoId = this.requireRepoId(owner, repo);
    return ResponseEntity.ok(
        this.runnerRegistryService.generateRegistrationToken(repoId, requesterId));
  }

  /** Register a runner using a one-time registration token. No user auth required. */
  @PostMapping("/actions/runners/register")
  @RateLimit(limit = 20, windowSeconds = 3600, key = "runner.register")
  public ResponseEntity<RunnerRegistrationResponse> register(
      @Valid @RequestBody final RunnerRegistrationRequest request) {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.runnerRegistryService.register(request));
  }

  /** Heartbeat — called by runner agent periodically. Auth via runner JWT. */
  @PostMapping("/actions/runners/{runnerId}/heartbeat")
  public ResponseEntity<Void> heartbeat(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final UUID runnerId,
      @Valid @RequestBody final RunnerHeartbeatRequest request) {

    final var token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
    this.runnerRegistryService.heartbeat(runnerId, token, request.status());
    return ResponseEntity.noContent().build();
  }

  /** List runners for a repository (requires user auth). */
  @GetMapping("/repos/{owner}/{repo}/actions/runners")
  public ResponseEntity<List<RunnerResponse>> list(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo) {

    this.jwtUtils.extractUserId(authHeader);
    final var repoId = this.requireRepoId(owner, repo);
    return ResponseEntity.ok(this.runnerRegistryService.listByRepo(repoId));
  }

  /** Delete a runner (requires user auth). */
  @DeleteMapping("/repos/{owner}/{repo}/actions/runners/{runnerId}")
  public ResponseEntity<Void> delete(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID runnerId) {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    final var repoId = this.requireRepoId(owner, repo);
    this.runnerRegistryService.delete(runnerId, requesterId, repoId);
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
