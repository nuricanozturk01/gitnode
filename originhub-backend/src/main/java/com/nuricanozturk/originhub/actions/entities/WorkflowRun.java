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
@Table(name = "workflow_run")
public class WorkflowRun {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "repo_id", nullable = false)
  private UUID repoId;

  @Column(name = "workflow_def_id")
  @Nullable
  private UUID workflowDefId;

  @Column(name = "workflow_name", nullable = false, length = 256)
  private String workflowName;

  @Column(name = "run_number", nullable = false)
  private int runNumber;

  @Column(name = "trigger_event", nullable = false, length = 32)
  private String triggerEvent;

  @Column(name = "trigger_ref", length = 256)
  @Nullable
  private String triggerRef;

  @Column(name = "trigger_sha", length = 64)
  @Nullable
  private String triggerSha;

  @Column(name = "trigger_actor")
  @Nullable
  private UUID triggerActor;

  @Enumerated(EnumType.STRING)
  @ColumnDefault("'queued'")
  @Column(name = "status", nullable = false, length = 16)
  private WorkflowRunStatus status = WorkflowRunStatus.QUEUED;

  @Column(name = "conclusion", length = 16)
  @Nullable
  private String conclusion;

  @Column(name = "concurrency_group", length = 256)
  @Nullable
  private String concurrencyGroup;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "inputs", columnDefinition = "jsonb")
  @Nullable
  private Map<String, String> inputs;

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
