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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.jspecify.annotations.Nullable;

@Getter
@Setter
@Entity
@Table(name = "workflow_step")
public class WorkflowStep {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "job_id", nullable = false)
  private UUID jobId;

  @Column(name = "step_number", nullable = false)
  private int stepNumber;

  @Column(name = "name", length = 256)
  @Nullable
  private String name;

  @Column(name = "uses", length = 256)
  @Nullable
  private String uses;

  @ColumnDefault("'pending'")
  @Column(name = "status", nullable = false, length = 16)
  private String status = "pending";

  @Column(name = "conclusion", length = 16)
  @Nullable
  private String conclusion;

  @Column(name = "started_at")
  @Nullable
  private Instant startedAt;

  @Column(name = "completed_at")
  @Nullable
  private Instant completedAt;
}
