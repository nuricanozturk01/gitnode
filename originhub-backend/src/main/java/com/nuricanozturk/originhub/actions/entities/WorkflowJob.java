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
package com.nuricanozturk.originhub.actions.entities;

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
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Getter
@Setter
@Entity
@Table(name = "workflow_job")
public class WorkflowJob {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "run_id", nullable = false)
  private UUID runId;

  @Column(name = "job_key", length = 256)
  @Nullable
  private String jobKey;

  @Column(name = "name", nullable = false, length = 256)
  private String name;

  @Column(name = "runner_id")
  @Nullable
  private UUID runnerId;

  @Column(name = "runner_labels", nullable = false, columnDefinition = "text[]")
  private List<String> runnerLabels;

  @Enumerated(EnumType.STRING)
  @ColumnDefault("'queued'")
  @Column(name = "status", nullable = false, length = 16)
  private WorkflowJobStatus status = WorkflowJobStatus.QUEUED;

  @Column(name = "conclusion", length = 16)
  @Nullable
  private String conclusion;

  @Column(name = "needs", columnDefinition = "text[]")
  @Nullable
  private List<String> needs;

  @Column(name = "matrix_values", columnDefinition = "jsonb")
  @Nullable
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, String> matrixValues;

  @Column(name = "started_at")
  @Nullable
  private Instant startedAt;

  @Column(name = "completed_at")
  @Nullable
  private Instant completedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
