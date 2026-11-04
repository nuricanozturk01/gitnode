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
package dev.gitnode.os.identity.adapters;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.identity.entities.Tenant;
import dev.gitnode.os.identity.repositories.TenantRepository;
import dev.gitnode.os.identity.services.PasswordHasher;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GitCredentialAdapterTest {

  private final TenantRepository tenantRepository = mock(TenantRepository.class);
  private final PasswordHasher passwordHasher = mock(PasswordHasher.class);
  private final Tenant tenant = mock(Tenant.class);
  private GitCredentialAdapter adapter;

  @BeforeEach
  void setUp() {
    this.adapter = new GitCredentialAdapter(this.tenantRepository, this.passwordHasher);
    when(this.tenantRepository.findByUsername("alice")).thenReturn(Optional.of(this.tenant));
    when(this.passwordHasher.hasPassword(this.tenant)).thenReturn(true);
    when(this.passwordHasher.matches("pw", this.tenant)).thenReturn(true);
  }

  @Test
  @DisplayName("verifyPassword accepts an enabled account with the right password")
  void verifyPassword_accepts_whenEnabled() {
    when(this.tenant.isEnabled()).thenReturn(true);

    assertThatCode(() -> this.adapter.verifyPassword("alice", "pw")).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("verifyPassword refuses a disabled account with the same error as a bad password")
  void verifyPassword_refuses_whenDisabled() {
    when(this.tenant.isEnabled()).thenReturn(false);

    assertThatThrownBy(() -> this.adapter.verifyPassword("alice", "pw"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid password");
    verify(this.passwordHasher).spendTime("pw");
    verify(this.passwordHasher, never()).matches(any(), any());
  }
}
