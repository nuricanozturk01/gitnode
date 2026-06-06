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
package com.nuricanozturk.originhub.webhook.controllers;

import com.nuricanozturk.originhub.shared.auth.services.JwtUtils;
import com.nuricanozturk.originhub.shared.ratelimit.RateLimit;
import com.nuricanozturk.originhub.webhook.dtos.WebhookForm;
import com.nuricanozturk.originhub.webhook.dtos.WebhookInfo;
import com.nuricanozturk.originhub.webhook.dtos.WebhookUpdateForm;
import com.nuricanozturk.originhub.webhook.services.WebhookService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repos/{owner}/{repo}/settings/webhooks")
@RequiredArgsConstructor
@NullMarked
public class WebhookController {

  private final WebhookService webhookService;
  private final JwtUtils jwtUtils;

  @GetMapping
  public ResponseEntity<List<WebhookInfo>> list(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo) {
    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(this.webhookService.list(requesterId, owner, repo));
  }

  @PostMapping
  @RateLimit(limit = 10, windowSeconds = 3600, key = "webhook.create")
  public ResponseEntity<WebhookInfo> create(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @Valid @RequestBody final WebhookForm form) {
    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.webhookService.create(requesterId, owner, repo, form));
  }

  @PatchMapping("/{webhookId}")
  public ResponseEntity<WebhookInfo> update(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID webhookId,
      @RequestBody final WebhookUpdateForm form) {
    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(this.webhookService.update(requesterId, owner, repo, webhookId, form));
  }

  @DeleteMapping("/{webhookId}")
  public ResponseEntity<Void> delete(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID webhookId) {
    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    this.webhookService.delete(requesterId, owner, repo, webhookId);
    return ResponseEntity.noContent().build();
  }
}
