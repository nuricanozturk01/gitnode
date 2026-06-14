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
package dev.gitnode.os.ai.entities;

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
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@Entity
@Table(name = "ai_codebase_analyses")
public class AiCodebaseAnalysis {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "repo_id", nullable = false)
  private UUID repoId;

  @Column(name = "branch", nullable = false)
  private String branch;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ReviewStatus status = ReviewStatus.PENDING;

  @Column(name = "arch_score")
  private Short archScore;

  @Column(name = "quality_score")
  private Short qualityScore;

  @Column(name = "perf_score")
  private Short perfScore;

  @Column(name = "memory_score")
  private Short memoryScore;

  @Column(name = "scalability_score")
  private Short scalabilityScore;

  @Column(name = "security_score")
  private Short securityScore;

  @Column(name = "overall_score")
  private Short overallScore;

  @Column(name = "summary", columnDefinition = "TEXT")
  private String summary;

  @Column(name = "recommendations", columnDefinition = "TEXT")
  private String recommendations;

  @Column(name = "dimension_details", columnDefinition = "TEXT")
  private String dimensionDetails;

  @Column(name = "raw_result", columnDefinition = "TEXT")
  private String rawResult;

  @Column(name = "triggered_by")
  private UUID triggeredBy;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;
}
