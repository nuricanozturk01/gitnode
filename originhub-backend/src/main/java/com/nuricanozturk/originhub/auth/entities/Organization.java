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
package com.nuricanozturk.originhub.auth.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

@Getter
@Setter
@Entity
@Table(name = "organization")
@NoArgsConstructor
public class Organization {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "slug", nullable = false, unique = true, length = 100)
  private String slug;

  @Column(name = "email_domains", nullable = false)
  private List<String> emailDomains = new ArrayList<>();

  @ColumnDefault("false")
  @Column(name = "sso_enabled", nullable = false)
  private boolean ssoEnabled;

  @Column(name = "idp_metadata_uri")
  @Nullable
  private String idpMetadataUri;

  @Column(name = "idp_metadata_xml", columnDefinition = "TEXT")
  @Nullable
  private String idpMetadataXml;

  @ColumnDefault("'email'")
  @Column(name = "email_attribute", nullable = false, length = 100)
  private String emailAttribute = "email";

  @Column(name = "username_attribute", length = 100)
  @Nullable
  private String usernameAttribute;

  @Column(name = "sp_entity_id")
  @Nullable
  private String spEntityId;

  @ColumnDefault("false")
  @Column(name = "ldap_enabled", nullable = false)
  private boolean ldapEnabled;

  @Column(name = "ldap_url")
  @Nullable
  private String ldapUrl;

  @Column(name = "ldap_base_dn", length = 512)
  @Nullable
  private String ldapBaseDn;

  @Column(name = "ldap_manager_dn", length = 512)
  @Nullable
  private String ldapManagerDn;

  @Column(name = "ldap_manager_password")
  @Nullable
  private String ldapManagerPassword;

  @ColumnDefault("'ou=people'")
  @Column(name = "ldap_user_search_base", nullable = false)
  private String ldapUserSearchBase = "ou=people";

  @ColumnDefault("'(uid={0})'")
  @Column(name = "ldap_user_search_filter", nullable = false)
  private String ldapUserSearchFilter = "(uid={0})";

  @ColumnDefault("'mail'")
  @Column(name = "ldap_email_attribute", nullable = false, length = 100)
  private String ldapEmailAttribute = "mail";

  @ColumnDefault("'cn'")
  @Column(name = "ldap_display_name_attribute", nullable = false, length = 100)
  private String ldapDisplayNameAttribute = "cn";

  @ColumnDefault("false")
  @Column(name = "ldap_use_start_tls", nullable = false)
  private boolean ldapUseStartTls;

  @Column(name = "ldap_group_search_base")
  @Nullable
  private String ldapGroupSearchBase = "ou=groups";

  @Column(name = "ldap_group_search_filter")
  @Nullable
  private String ldapGroupSearchFilter = "(memberUid={0})";

  @Column(name = "ldap_group_role_attribute", length = 100)
  @Nullable
  private String ldapGroupRoleAttribute = "cn";

  @Column(name = "ldap_admin_group_dns")
  @Nullable
  private String ldapAdminGroupDns;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
