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
package com.nuricanozturk.originhub.auth.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record OrganizationInfo(
    UUID id,
    String name,
    String slug,
    List<String> emailDomains,
    boolean ssoEnabled,
    @Nullable String idpMetadataUri,
    boolean metadataCached,
    String emailAttribute,
    @Nullable String usernameAttribute,
    @Nullable String spEntityId,
    boolean ldapEnabled,
    boolean ldapConfigured,
    @Nullable String ldapUrl,
    @Nullable String ldapBaseDn,
    @Nullable String ldapManagerDn,
    boolean managerPasswordConfigured,
    String ldapUserSearchBase,
    String ldapUserSearchFilter,
    String ldapEmailAttribute,
    String ldapDisplayNameAttribute,
    boolean ldapUseStartTls,
    @Nullable String ldapGroupSearchBase,
    @Nullable String ldapGroupSearchFilter,
    @Nullable String ldapGroupRoleAttribute,
    @Nullable String ldapAdminGroupDns,
    Instant createdAt,
    Instant updatedAt) {}
