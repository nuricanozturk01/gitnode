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
package dev.gitnode.os.notification.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "notification")
@Getter
@Setter
@NullMarked
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "recipient_id", nullable = false, updatable = false)
  private UUID recipientId;

  @Column(name = "actor_id", updatable = false)
  private @Nullable UUID actorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 60, updatable = false)
  private NotificationType type;

  @Column(name = "title", nullable = false, length = 255, updatable = false)
  private String title;

  @Column(name = "body", updatable = false)
  private @Nullable String body;

  @Column(name = "link", length = 500, updatable = false)
  private @Nullable String link;

  @Column(name = "is_read", nullable = false)
  private boolean read;

  @Column(name = "entity_id", updatable = false)
  private @Nullable UUID entityId;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
