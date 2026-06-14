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
package dev.gitnode.os.notification.controllers;

import dev.gitnode.os.notification.dtos.NotificationPreferenceDto;
import dev.gitnode.os.notification.entities.NotificationType;
import dev.gitnode.os.notification.services.NotificationPreferenceService;
import dev.gitnode.os.shared.auth.services.JwtUtils;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications/preferences")
@RequiredArgsConstructor
@NullMarked
public class NotificationPreferenceController {

  private final NotificationPreferenceService preferenceService;
  private final JwtUtils jwtUtils;

  @GetMapping
  public ResponseEntity<List<NotificationPreferenceDto>> getAll(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {
    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(this.preferenceService.getAll(tenantId));
  }

  @PutMapping("/{type}")
  public ResponseEntity<NotificationPreferenceDto> set(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final NotificationType type,
      @RequestBody final Map<String, Boolean> body) {
    final var tenantId = this.jwtUtils.extractUserId(authHeader);
    final var enabled = Boolean.TRUE.equals(body.get("enabled"));
    return ResponseEntity.ok(this.preferenceService.set(tenantId, type, enabled));
  }
}
