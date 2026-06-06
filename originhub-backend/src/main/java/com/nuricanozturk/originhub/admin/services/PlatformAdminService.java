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

import com.nuricanozturk.originhub.shared.errorhandling.exceptions.AccessNotAllowedException;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@NullMarked
public class PlatformAdminService {

  private final Set<String> adminUsernames;
  private final String bootstrapAdminUsername;
  private final boolean bootstrapAdminEnabled;

  public PlatformAdminService(
      @Value("${originhub.platform.admin-usernames:}") final String adminUsernamesCsv,
      @Value("${originhub.bootstrap.admin.username:admin}") final String bootstrapAdminUsername,
      @Value("${originhub.bootstrap.admin.enabled:true}") final boolean bootstrapAdminEnabled) {

    this.bootstrapAdminUsername = bootstrapAdminUsername;
    this.bootstrapAdminEnabled = bootstrapAdminEnabled;

    final var usernames =
        Arrays.stream(adminUsernamesCsv.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .map(s -> s.toLowerCase(Locale.ROOT))
            .collect(Collectors.toCollection(HashSet::new));

    if (bootstrapAdminEnabled && !bootstrapAdminUsername.isBlank()) {
      usernames.add(bootstrapAdminUsername.toLowerCase(Locale.ROOT));
    }

    this.adminUsernames = Set.copyOf(usernames);
  }

  public boolean isCurrentUserPlatformAdmin() {

    final var tenant = this.currentTenant();
    return tenant != null && this.isPlatformAdmin(tenant.getUsername());
  }

  public boolean isPlatformAdmin(final String username) {

    return this.adminUsernames.contains(username.toLowerCase(Locale.ROOT));
  }

  public void requirePlatformAdmin() {

    if (!this.isCurrentUserPlatformAdmin()) {
      throw new AccessNotAllowedException("platformAdminRequired");
    }
  }

  public boolean isBootstrapAdminUsername(final String username) {

    return this.bootstrapAdminEnabled
        && !this.bootstrapAdminUsername.isBlank()
        && this.bootstrapAdminUsername.equalsIgnoreCase(username);
  }

  public List<String> listPlatformAdminUsernames() {

    return this.adminUsernames.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
  }

  public String bootstrapAdminUsername() {

    return this.bootstrapAdminUsername;
  }

  public boolean bootstrapAdminEnabled() {

    return this.bootstrapAdminEnabled;
  }

  private @Nullable Tenant currentTenant() {

    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()) {
      return null;
    }

    final var principal = authentication.getPrincipal();

    if (principal instanceof Tenant tenant) {
      return tenant;
    }

    return null;
  }
}
