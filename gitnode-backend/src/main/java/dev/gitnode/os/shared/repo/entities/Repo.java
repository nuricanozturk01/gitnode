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
package dev.gitnode.os.shared.repo.entities;

import dev.gitnode.os.shared.tenant.entities.Tenant;
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
import java.util.Set;
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
@Table(name = "repositories")
public class Repo {
  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  @JoinColumn(name = "owner_id", nullable = false)
  private Tenant owner;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "description", length = Integer.MAX_VALUE)
  private String description;

  @ColumnDefault("true")
  @Column(name = "is_private", nullable = false)
  private boolean isPrivate = true;

  @ColumnDefault("false")
  @Column(name = "is_archived")
  private boolean isArchived;

  @ColumnDefault("'main'")
  @Column(name = "default_branch")
  private String defaultBranch;

  @ColumnDefault("false")
  @Column(name = "delete_head_branch_on_pr_merge", nullable = false)
  private boolean deleteHeadBranchOnPrMerge;

  @ColumnDefault("false")
  @Column(name = "delete_head_branch_on_pr_close", nullable = false)
  private boolean deleteHeadBranchOnPrClose;

  @Column(name = "topics")
  private Set<String> topics;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "forked_from_id")
  private Repo forkedFrom;

  @ColumnDefault("0")
  @Column(name = "fork_count", nullable = false)
  private int forkCount;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;
}
