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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BootstrapAdminInitializer unit tests")
class BootstrapAdminInitializerTest {

  private static final String PASSWORD = "bootstrap-secret";

  @Mock private TenantRepository tenantRepository;

  @Test
  @DisplayName("creates tenant when username does not exist")
  void run_createsTenant_whenUsernameMissing() {

    final var initializer =
        new BootstrapAdminInitializer(this.tenantRepository, true, "Admin", PASSWORD);
    when(this.tenantRepository.existsByUsername("admin")).thenReturn(false);
    when(this.tenantRepository.save(any(Tenant.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    initializer.run(null);

    final var captor = ArgumentCaptor.forClass(Tenant.class);
    verify(this.tenantRepository).save(captor.capture());

    final var saved = captor.getValue();
    assertThat(saved.getUsername()).isEqualTo("admin");
    assertThat(saved.getEmail()).isEqualTo("admin@originhub.local");
    assertThat(saved.isEnabled()).isTrue();
    assertThat(saved.getSalt()).hasSize(16);
    assertThat(saved.getHash()).isEqualTo(DigestUtils.sha256Hex(PASSWORD + saved.getSalt()));
  }

  @Test
  @DisplayName("skips creation when username already exists")
  void run_skips_whenUsernameExists() {

    final var initializer =
        new BootstrapAdminInitializer(this.tenantRepository, true, "admin", PASSWORD);
    when(this.tenantRepository.existsByUsername("admin")).thenReturn(true);

    initializer.run(null);

    verify(this.tenantRepository, never()).save(any());
  }

  @Test
  @DisplayName("skips creation when password is blank")
  void run_skips_whenPasswordBlank() {

    final var initializer = new BootstrapAdminInitializer(this.tenantRepository, true, "admin", "");

    initializer.run(null);

    verify(this.tenantRepository, never()).existsByUsername(anyString());
    verify(this.tenantRepository, never()).save(any());
  }

  @Test
  @DisplayName("skips creation when bootstrap is disabled")
  void run_skips_whenDisabled() {

    final var initializer =
        new BootstrapAdminInitializer(this.tenantRepository, false, "admin", PASSWORD);

    initializer.run(null);

    verify(this.tenantRepository, never()).existsByUsername(anyString());
    verify(this.tenantRepository, never()).save(any());
  }
}
