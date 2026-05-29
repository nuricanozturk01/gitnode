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
package com.nuricanozturk.originhub.snippet.entities;

import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
@Table(name = "snippets")
public class Snippet {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  @JoinColumn(name = "owner_id", nullable = false)
  private Tenant owner;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "description", length = Integer.MAX_VALUE)
  private String description;

  @Enumerated(EnumType.STRING)
  @ColumnDefault("'PUBLIC'")
  @Column(name = "visibility", nullable = false, length = 10)
  private Visibility visibility = Visibility.PUBLIC;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "forked_from_id")
  private Snippet forkedFrom;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "snippet_repos",
      joinColumns = @JoinColumn(name = "snippet_id"),
      inverseJoinColumns = @JoinColumn(name = "repo_id"))
  private Set<Repo> repos = new LinkedHashSet<>();

  @ColumnDefault("0")
  @Column(name = "file_count", nullable = false)
  private int fileCount = 0;

  @ColumnDefault("0")
  @Column(name = "comment_count", nullable = false)
  private int commentCount = 0;

  @ColumnDefault("0")
  @Column(name = "fork_count", nullable = false)
  private int forkCount = 0;

  @OneToMany(mappedBy = "snippet", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("position ASC")
  private List<SnippetFile> files = new ArrayList<>();

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;
}
