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
package com.nuricanozturk.originhub.admin.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Getter
@Entity
@Immutable
@NullMarked
@Table(name = "event_publication")
public class EventPublicationRecord {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "listener_id", nullable = false)
  private String listenerId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "serialized_event", nullable = false, columnDefinition = "TEXT")
  private String serializedEvent;

  @Column(name = "publication_date", nullable = false)
  private Instant publicationDate;

  @Nullable
  @Column(name = "completion_date")
  private Instant completionDate;

  @Nullable
  @Column(name = "status")
  private String status;

  @Nullable
  @Column(name = "completion_attempts")
  private Integer completionAttempts;

  @Nullable
  @Column(name = "last_resubmission_date")
  private Instant lastResubmissionDate;
}
