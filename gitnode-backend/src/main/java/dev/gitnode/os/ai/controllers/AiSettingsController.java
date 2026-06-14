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

import dev.gitnode.os.ai.dtos.TestAiConnectionRequest;
import dev.gitnode.os.ai.dtos.TestAiConnectionResponse;
import dev.gitnode.os.ai.dtos.UpdateAiSettingsRequest;
import dev.gitnode.os.ai.dtos.UserAiSettingsDto;
import dev.gitnode.os.ai.services.UserAiSettingsService;
import dev.gitnode.os.shared.auth.services.JwtUtils;
import dev.gitnode.os.shared.ratelimit.RateLimit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/ai/settings")
@RequiredArgsConstructor
@NullMarked
public class AiSettingsController {

  private final UserAiSettingsService settingsService;
  private final JwtUtils jwtUtils;

  @GetMapping
  public ResponseEntity<UserAiSettingsDto> get(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {
    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(this.settingsService.getSettings(tenantId));
  }

  @PutMapping
  public ResponseEntity<UserAiSettingsDto> update(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @Valid @RequestBody final UpdateAiSettingsRequest request) {
    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(this.settingsService.updateSettings(tenantId, request));
  }

  @DeleteMapping("/api-key")
  public ResponseEntity<Void> deleteApiKey(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {
    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    this.settingsService.deleteApiKey(tenantId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/test")
  @RateLimit(limit = 5, windowSeconds = 60, key = "ai.test")
  public ResponseEntity<TestAiConnectionResponse> testConnection(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @RequestBody(required = false) final @Nullable TestAiConnectionRequest request) {
    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    final var message = this.settingsService.testConnection(tenantId, request);
    return ResponseEntity.ok(new TestAiConnectionResponse(message));
  }
}
