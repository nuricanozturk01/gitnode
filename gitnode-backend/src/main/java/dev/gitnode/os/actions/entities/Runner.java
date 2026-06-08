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
package dev.gitnode.os.actions.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

@Getter
@Setter
@Entity
@Table(name = "runner")
public class Runner {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id")
  @Nullable
  private UUID tenantId;

  @Column(name = "name", nullable = false, length = 128)
  private String name;

  @Column(name = "token_hash", nullable = false, length = 64, unique = true)
  private String tokenHash;

  @Column(name = "labels", nullable = false, columnDefinition = "text[]")
  private List<String> labels;

  @Column(name = "os", length = 32)
  @Nullable
  private String os;

  @Column(name = "arch", length = 16)
  @Nullable
  private String arch;

  @Enumerated(EnumType.STRING)
  @ColumnDefault("'offline'")
  @Column(name = "status", nullable = false, length = 16)
  private RunnerStatus status = RunnerStatus.OFFLINE;

  @Enumerated(EnumType.STRING)
  @ColumnDefault("'shell'")
  @Column(name = "executor_type", nullable = false, length = 16)
  private ExecutorType executorType = ExecutorType.SHELL;

  @Column(name = "version", length = 32)
  @Nullable
  private String version;

  @Column(name = "last_heartbeat")
  @Nullable
  private Instant lastHeartbeat;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "created_by")
  @Nullable
  private UUID createdBy;
}
