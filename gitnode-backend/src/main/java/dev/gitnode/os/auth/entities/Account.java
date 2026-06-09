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
package dev.gitnode.os.auth.entities;

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
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.jspecify.annotations.Nullable;

@Getter
@Setter
@Entity
@Table(name = "sso_account")
@NoArgsConstructor
@ToString(of = {"id", "accountType", "subjectId"})
@EqualsAndHashCode(of = "id")
public class Account {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "account_type", nullable = false)
  private String accountType;

  @Column(name = "subject_id", nullable = false)
  private String subjectId;

  @Column(name = "username")
  private String username;

  @Column(name = "email")
  private String email;

  @Column(name = "avatar_url")
  private String avatarUrl;

  @Column(name = "ldap_groups", length = 2000)
  @Nullable
  private String ldapGroups;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;
}
