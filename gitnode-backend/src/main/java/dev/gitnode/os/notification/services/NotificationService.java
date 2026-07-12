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
package dev.gitnode.os.notification.services;

import dev.gitnode.os.notification.dtos.NotificationInfo;
import dev.gitnode.os.notification.entities.Notification;
import dev.gitnode.os.notification.entities.NotificationType;
import dev.gitnode.os.notification.repositories.NotificationRepository;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.repo.dtos.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@NullMarked
public class NotificationService {

  private static final int MAX_PAGE_SIZE = 50;

  private final NotificationRepository repository;
  private final NotificationSseRegistry sseRegistry;
  private final NotificationPreferenceService preferenceService;

  @Transactional
  public void send(
      final UUID recipientId,
      final @Nullable UUID actorId,
      final NotificationType type,
      final String title,
      final @Nullable String body,
      final @Nullable String link,
      final @Nullable UUID entityId) {

    if (actorId != null && actorId.equals(recipientId)) {
      return;
    }
    if (!this.preferenceService.isEnabled(recipientId, type)) {
      return;
    }

    final var notification = new Notification();
    notification.setRecipientId(recipientId);
    notification.setActorId(actorId);
    notification.setType(type);
    notification.setTitle(title);
    notification.setBody(body);
    notification.setLink(link);
    notification.setEntityId(entityId);
    notification.setRead(false);

    final var saved = this.repository.save(notification);
    final var dto = this.toDto(saved);
    this.sseRegistry.push(recipientId, dto);
  }

  public PageResponse<NotificationInfo> list(
      final UUID recipientId, final int page, final int size) {
    final var pageable = PageRequest.of(page, Math.clamp(size, 1, MAX_PAGE_SIZE));
    return PageResponse.from(
        this.repository
            .findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable)
            .map(this::toDto));
  }

  public long unreadCount(final UUID recipientId) {
    return this.repository.countByRecipientIdAndReadFalse(recipientId);
  }

  @Transactional
  public void markRead(final UUID notificationId, final UUID recipientId) {
    final var notification =
        this.repository
            .findById(notificationId)
            .orElseThrow(() -> new ItemNotFoundException("Notification not found"));

    if (!notification.getRecipientId().equals(recipientId)) {
      throw new ItemNotFoundException("Notification not found");
    }

    if (!notification.isRead()) {
      notification.setRead(true);
      this.repository.save(notification);
    }
  }

  @Transactional
  public void markAllRead(final UUID recipientId) {
    this.repository.markAllReadByRecipientId(recipientId);
  }

  @Transactional
  public void delete(final UUID notificationId, final UUID recipientId) {
    if (!this.repository.existsByIdAndRecipientId(notificationId, recipientId)) {
      throw new ItemNotFoundException("Notification not found");
    }
    this.repository.deleteById(notificationId);
  }

  @Transactional
  public void deleteAll(final UUID recipientId) {
    this.repository.deleteAllByRecipientId(recipientId);
  }

  private NotificationInfo toDto(final Notification n) {
    return new NotificationInfo(
        n.getId(),
        n.getType(),
        n.getTitle(),
        n.getBody(),
        n.getLink(),
        n.isRead(),
        n.getActorId(),
        n.getEntityId(),
        n.getCreatedAt());
  }
}
