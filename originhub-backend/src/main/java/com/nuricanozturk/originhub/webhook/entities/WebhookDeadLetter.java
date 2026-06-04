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
package com.nuricanozturk.originhub.webhook.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Getter
@Setter
@Entity
@NullMarked
@Table(name = "webhook_dead_letters")
public class WebhookDeadLetter {

  private static final int THREE = 3;

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "webhook_id", nullable = false)
  private UUID webhookId;

  @Column(name = "url", nullable = false, length = 500)
  private String url;

  @Nullable
  @Column(name = "event_type", length = 100)
  private String eventType;

  @Nullable
  @Column(name = "payload", columnDefinition = "TEXT")
  private String payload;

  @Nullable
  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount = THREE;

  @CreationTimestamp
  @Column(name = "failed_at", nullable = false)
  private Instant failedAt;
}
