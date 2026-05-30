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
package com.nuricanozturk.originhub.pr.entities;

import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@Entity
@Table(name = "pull_requests")
public class PullRequest {
  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  @JoinColumn(name = "repo_id", nullable = false)
  private Repo repo;

  @Column(name = "number", nullable = false)
  private int number;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "description", length = Integer.MAX_VALUE)
  private String description;

  @ColumnDefault("'OPEN'")
  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "author_id", nullable = false)
  private Tenant author;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "merged_by_id")
  private Tenant mergedBy;

  @Column(name = "source_branch", nullable = false)
  private String sourceBranch;

  @Column(name = "source_sha", length = 40)
  private String sourceSha;

  @Column(name = "target_branch", nullable = false)
  private String targetBranch;

  @Column(name = "merge_sha", length = 40)
  private String mergeSha;

  @Column(name = "is_draft")
  private boolean isDraft;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "merged_at")
  private Instant mergedAt;

  @Column(name = "closed_at")
  private Instant closedAt;
}
