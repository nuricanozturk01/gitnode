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
package com.nuricanozturk.originhub.migration.entities;

import com.nuricanozturk.originhub.migration.dtos.MigrationItem;
import com.nuricanozturk.originhub.migration.dtos.MigrationService;
import com.nuricanozturk.originhub.migration.dtos.MigrationStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.NullMarked;

@Entity
@Table(name = "migration_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@NullMarked
public class MigrationJob {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Enumerated(EnumType.STRING)
  private MigrationService service;

  @Enumerated(EnumType.STRING)
  private MigrationStatus status;

  @Column(name = "repo_url")
  private String repoUrl;

  @Column(name = "repo_owner")
  private String owner;

  @Column(name = "repo_name")
  private String repoName;

  @ElementCollection
  @Enumerated(EnumType.STRING)
  @CollectionTable(
      name = "migration_jobs_migration_items",
      joinColumns = @JoinColumn(name = "migration_job_id"))
  @Column(name = "migration_items")
  private List<MigrationItem> migrationItems;

  @Column(name = "error_msg")
  private String errorMessage;

  @Column(name = "requester_id")
  private UUID requesterId;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  @Column(name = "completed_at")
  private Instant completedAt;
}
