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

import com.nuricanozturk.originhub.shared.lock.DistributedLockService;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BootstrapAdminInitializer unit tests")
class BootstrapAdminInitializerTest {

  private static final String PASSWORD = "bootstrap-secret";

  @Mock private TenantRepository tenantRepository;
  @Mock private DistributedLockService lockService;
  @Mock private TransactionTemplate transactionTemplate;

  @BeforeEach
  void setUp() {
    when(lockService.generateOwner()).thenReturn("test-owner");
    when(lockService.tryLock(any(), any(), any())).thenReturn(true);
    when(transactionTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              final var callback =
                  (org.springframework.transaction.support.TransactionCallback<?>)
                      invocation.getArgument(0);
              return callback.doInTransaction(null);
            });
  }

  private BootstrapAdminInitializer initializer(
      final boolean enabled, final String username, final String password) {
    return new BootstrapAdminInitializer(
        tenantRepository, lockService, transactionTemplate, enabled, username, password);
  }

  @Test
  @DisplayName("creates tenant when username does not exist")
  void run_createsTenant_whenUsernameMissing() {

    when(this.tenantRepository.existsByUsername("admin")).thenReturn(false);
    when(this.tenantRepository.save(any(Tenant.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    initializer(true, "Admin", PASSWORD).run(null);

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

    when(this.tenantRepository.existsByUsername("admin")).thenReturn(true);

    initializer(true, "admin", PASSWORD).run(null);

    verify(this.tenantRepository, never()).save(any());
  }

  @Test
  @DisplayName("skips creation when password is blank")
  void run_skips_whenPasswordBlank() {

    initializer(true, "admin", "").run(null);

    verify(this.tenantRepository, never()).existsByUsername(anyString());
    verify(this.tenantRepository, never()).save(any());
  }

  @Test
  @DisplayName("skips creation when bootstrap is disabled")
  void run_skips_whenDisabled() {

    initializer(false, "admin", PASSWORD).run(null);

    verify(this.tenantRepository, never()).existsByUsername(anyString());
    verify(this.tenantRepository, never()).save(any());
  }

  @Test
  @DisplayName("skips creation when distributed lock not acquired")
  void run_skips_whenLockNotAcquired() {

    when(lockService.tryLock(any(), any(), any())).thenReturn(false);

    initializer(true, "admin", PASSWORD).run(null);

    verify(this.tenantRepository, never()).existsByUsername(anyString());
    verify(this.tenantRepository, never()).save(any());
  }
}
