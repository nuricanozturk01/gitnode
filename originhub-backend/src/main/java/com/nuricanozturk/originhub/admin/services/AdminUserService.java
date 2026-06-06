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
package com.nuricanozturk.originhub.admin.services;

import com.nuricanozturk.originhub.admin.dtos.AdminUserDetail;
import com.nuricanozturk.originhub.admin.dtos.AdminUserSummary;
import com.nuricanozturk.originhub.shared.audit.annotations.Audited;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.BadRequestException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NullMarked
public class AdminUserService {

  private final TenantRepository tenantRepository;
  private final PlatformAdminService platformAdminService;

  @Transactional(readOnly = true)
  public Page<AdminUserSummary> listUsers(final Pageable pageable, final @Nullable String query) {

    final var normalizedQuery = query == null ? "" : query.trim();

    if (normalizedQuery.isBlank()) {
      return this.tenantRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toSummary);
    }

    return this.tenantRepository
        .findAllByUsernameContainingIgnoreCaseOrderByCreatedAtDesc(normalizedQuery, pageable)
        .map(this::toSummary);
  }

  @Transactional(readOnly = true)
  public AdminUserDetail getUser(final UUID id) {

    return this.toDetail(this.requireTenant(id));
  }

  @Audited(
      action = "USER_ENABLED_CHANGED",
      entityType = "TENANT",
      entityIdSpEL = "#id",
      detailsSpEL = "'enabled=' + #enabled")
  @Transactional
  public AdminUserDetail setEnabled(final UUID id, final boolean enabled) {

    final var tenant = this.requireTenant(id);

    if (!enabled && this.platformAdminService.isBootstrapAdminUsername(tenant.getUsername())) {
      throw new BadRequestException("cannotDisableBootstrapAdmin");
    }

    tenant.setEnabled(enabled);
    return this.toDetail(this.tenantRepository.save(tenant));
  }

  private Tenant requireTenant(final UUID id) {

    return this.tenantRepository
        .findById(id)
        .orElseThrow(() -> new ItemNotFoundException("userNotExist"));
  }

  private AdminUserSummary toSummary(final Tenant tenant) {

    return new AdminUserSummary(
        tenant.getId(),
        tenant.getUsername(),
        tenant.getEmail(),
        tenant.isEnabled(),
        tenant.getCreatedAt());
  }

  private AdminUserDetail toDetail(final Tenant tenant) {

    return new AdminUserDetail(
        tenant.getId(),
        tenant.getUsername(),
        tenant.getEmail(),
        tenant.isEnabled(),
        tenant.getDisplayName(),
        tenant.getCreatedAt(),
        tenant.getUpdatedAt(),
        this.platformAdminService.isPlatformAdmin(tenant.getUsername()),
        this.platformAdminService.isBootstrapAdminUsername(tenant.getUsername()));
  }
}
