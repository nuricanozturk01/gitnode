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
package dev.gitnode.os.actions.controllers;

import dev.gitnode.os.actions.dtos.request.RunnerHeartbeatRequest;
import dev.gitnode.os.actions.dtos.request.RunnerRegistrationRequest;
import dev.gitnode.os.actions.dtos.response.RegistrationTokenResponse;
import dev.gitnode.os.actions.dtos.response.RunnerRegistrationResponse;
import dev.gitnode.os.actions.dtos.response.RunnerResponse;
import dev.gitnode.os.actions.services.RunnerRegistryService;
import dev.gitnode.os.shared.auth.services.JwtUtils;
import dev.gitnode.os.shared.ratelimit.RateLimit;
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

  @PostMapping("/actions/runners/registration-token")
  @RateLimit(limit = 10, windowSeconds = 3600, key = "runner.reg-token")
  public ResponseEntity<RegistrationTokenResponse> createRegistrationToken(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(
        this.runnerRegistryService.generateRegistrationToken(tenantId, tenantId));
  }

  @PostMapping("/actions/runners/register")
  @RateLimit(limit = 20, windowSeconds = 3600, key = "runner.register")
  public ResponseEntity<RunnerRegistrationResponse> register(
      @Valid @RequestBody final RunnerRegistrationRequest request) {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.runnerRegistryService.register(request));
  }

  @PostMapping("/actions/runners/{runnerId}/heartbeat")
  public ResponseEntity<Void> heartbeat(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final UUID runnerId,
      @Valid @RequestBody final RunnerHeartbeatRequest request) {

    final var token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
    this.runnerRegistryService.heartbeat(runnerId, token, request.status());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/actions/runners")
  public ResponseEntity<List<RunnerResponse>> list(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(this.runnerRegistryService.listByTenant(tenantId));
  }

  @DeleteMapping("/actions/runners/{runnerId}")
  public ResponseEntity<Void> delete(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final UUID runnerId) {

    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    this.runnerRegistryService.delete(runnerId, tenantId, tenantId);
    return ResponseEntity.noContent().build();
  }
}
