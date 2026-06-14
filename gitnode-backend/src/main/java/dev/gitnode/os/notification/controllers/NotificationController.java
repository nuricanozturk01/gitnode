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

import dev.gitnode.os.notification.dtos.NotificationDto;
import dev.gitnode.os.notification.services.NotificationService;
import dev.gitnode.os.notification.services.NotificationSseRegistry;
import dev.gitnode.os.shared.auth.services.JwtUtils;
import dev.gitnode.os.shared.repo.dtos.PageResponse;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@NullMarked
public class NotificationController {

  private final NotificationService notificationService;
  private final NotificationSseRegistry sseRegistry;
  private final JwtUtils jwtUtils;

  @GetMapping
  public ResponseEntity<PageResponse<NotificationDto>> list(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size) {
    final var recipientId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(this.notificationService.list(recipientId, page, size));
  }

  @GetMapping("/unread-count")
  public ResponseEntity<Map<String, Long>> unreadCount(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {
    final var recipientId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.ok(Map.of("count", this.notificationService.unreadCount(recipientId)));
  }

  @PutMapping("/{id}/read")
  public ResponseEntity<Void> markRead(
      @PathVariable final UUID id,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {
    final var recipientId = this.jwtUtils.extractUserId(authHeader);
    this.notificationService.markRead(id, recipientId);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/read-all")
  public ResponseEntity<Void> markAllRead(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {
    final var recipientId = this.jwtUtils.extractUserId(authHeader);
    this.notificationService.markAllRead(recipientId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable final UUID id,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {
    final var recipientId = this.jwtUtils.extractUserId(authHeader);
    this.notificationService.delete(id, recipientId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping
  public ResponseEntity<Void> deleteAll(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {
    final var recipientId = this.jwtUtils.extractUserId(authHeader);
    this.notificationService.deleteAll(recipientId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          final @Nullable String authHeader,
      @RequestParam(value = "token", required = false) final @Nullable String tokenParam) {
    final var raw = authHeader != null ? authHeader : tokenParam;
    if (raw == null || raw.isBlank()) {
      throw new dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException(
          "Missing authentication token");
    }
    final var recipientId = this.jwtUtils.extractUserId(raw);
    return this.sseRegistry.subscribe(recipientId);
  }
}
