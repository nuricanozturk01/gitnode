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
package com.nuricanozturk.originhub.collaborator.entities;

import com.nuricanozturk.originhub.shared.collaborator.dtos.CollaboratorPermission;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

@Getter
@Setter
@Entity
@Table(name = "repo_collaborators")
public class RepoCollaborator {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  @JoinColumn(name = "repo_id", nullable = false)
  private Repo repo;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  @JoinColumn(name = "invited_by_id", nullable = false)
  private Tenant invitedBy;

  @ElementCollection(targetClass = CollaboratorPermission.class, fetch = FetchType.EAGER)
  @CollectionTable(
      name = "repo_collaborator_permissions",
      joinColumns = @JoinColumn(name = "collaborator_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "permission")
  private Set<CollaboratorPermission> permissions = EnumSet.noneOf(CollaboratorPermission.class);

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private CollaboratorStatus status = CollaboratorStatus.PENDING;

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;

  @Nullable
  @Column(name = "invite_token", unique = true, length = 36)
  private String inviteToken;

  @Nullable
  @Column(name = "token_expires_at")
  private Instant tokenExpiresAt;
}
