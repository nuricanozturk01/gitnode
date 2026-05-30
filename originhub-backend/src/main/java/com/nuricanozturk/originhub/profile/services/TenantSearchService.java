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
package com.nuricanozturk.originhub.profile.services;

import com.nuricanozturk.originhub.profile.dtos.TenantSearchResult;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class TenantSearchService {

  private final @NonNull TenantRepository tenantRepository;

  public @NonNull List<TenantSearchResult> search(final @NonNull String prefix) {

    final var tenants =
        this.tenantRepository.findTop10ByUsernameIgnoreCaseStartingWithOrderByUsernameAsc(prefix);

    return tenants.stream().map(this::toResult).toList();
  }

  private @NonNull TenantSearchResult toResult(final @NonNull Tenant t) {

    final var displayName = t.getDisplayName() != null ? t.getDisplayName() : t.getUsername();

    return new TenantSearchResult(t.getUsername(), displayName, t.getAvatarUrl());
  }
}
