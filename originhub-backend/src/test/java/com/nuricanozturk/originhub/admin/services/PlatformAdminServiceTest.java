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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nuricanozturk.originhub.shared.errorhandling.exceptions.AccessNotAllowedException;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("PlatformAdminService unit tests")
class PlatformAdminServiceTest {

  @AfterEach
  void tearDown() {

    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("isPlatformAdmin returns true for configured username")
  void isPlatformAdmin_returnsTrue_forConfiguredUsername() {

    final var service = new PlatformAdminService("alice, bob", "admin", false);

    assertThat(service.isPlatformAdmin("alice")).isTrue();
    assertThat(service.isPlatformAdmin("BOB")).isTrue();
    assertThat(service.isPlatformAdmin("charlie")).isFalse();
  }

  @Test
  @DisplayName("requirePlatformAdmin throws when current user is not admin")
  void requirePlatformAdmin_throws_whenNotAdmin() {

    final var service = new PlatformAdminService("admin-user", "admin", false);
    final var tenant = new Tenant();
    tenant.setUsername("regular-user");

    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(tenant, null, List.of()));

    assertThatThrownBy(service::requirePlatformAdmin).isInstanceOf(AccessNotAllowedException.class);
  }

  @Test
  @DisplayName("isCurrentUserPlatformAdmin returns true for authenticated admin")
  void isCurrentUserPlatformAdmin_returnsTrue_forAuthenticatedAdmin() {

    final var service = new PlatformAdminService("admin-user", "admin", false);
    final var tenant = new Tenant();
    tenant.setUsername("admin-user");

    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(tenant, null, List.of()));

    assertThat(service.isCurrentUserPlatformAdmin()).isTrue();
  }

  @Test
  @DisplayName("isPlatformAdmin returns true for bootstrap admin username when enabled")
  void isPlatformAdmin_returnsTrue_forBootstrapAdminUsername() {

    final var service = new PlatformAdminService("", "bootstrap-admin", true);

    assertThat(service.isPlatformAdmin("bootstrap-admin")).isTrue();
    assertThat(service.isPlatformAdmin("BOOTSTRAP-ADMIN")).isTrue();
  }

  @Test
  @DisplayName("isBootstrapAdminUsername identifies configured bootstrap user")
  void isBootstrapAdminUsername_returnsTrue_forConfiguredBootstrapUser() {

    final var service = new PlatformAdminService("", "bootstrap-admin", true);

    assertThat(service.isBootstrapAdminUsername("bootstrap-admin")).isTrue();
    assertThat(service.isBootstrapAdminUsername("other-admin")).isFalse();
  }
}
