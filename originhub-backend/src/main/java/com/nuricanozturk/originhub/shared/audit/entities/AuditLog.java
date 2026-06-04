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
package com.nuricanozturk.originhub.shared.audit.entities;

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
@Table(name = "audit_logs")
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Nullable
  @Column(name = "actor_username", length = 100)
  private String actorUsername;

  @Column(name = "action", nullable = false, length = 100)
  private String action;

  @Nullable
  @Column(name = "entity_type", length = 100)
  private String entityType;

  @Nullable
  @Column(name = "entity_id", length = 255)
  private String entityId;

  @Nullable
  @Column(name = "details", columnDefinition = "TEXT")
  private String details;

  @Nullable
  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @CreationTimestamp
  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;
}
