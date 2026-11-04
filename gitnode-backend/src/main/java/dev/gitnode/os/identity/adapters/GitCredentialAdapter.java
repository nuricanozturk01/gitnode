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

import dev.gitnode.os.identity.api.GitCredentialPort;
import dev.gitnode.os.identity.repositories.TenantRepository;
import dev.gitnode.os.identity.services.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NullMarked
public class GitCredentialAdapter implements GitCredentialPort {

  private final TenantRepository tenantRepository;
  private final PasswordHasher passwordHasher;

  @Override
  @Transactional
  public void verifyPassword(final String username, final String password) {

    final var tenant =
        this.tenantRepository
            .findByUsername(username)
            .orElseThrow(
                () -> {
                  this.passwordHasher.spendTime(password);
                  return new IllegalArgumentException("User not found");
                });

    if (!this.passwordHasher.hasPassword(tenant)) {
      this.passwordHasher.spendTime(password);
      throw new IllegalArgumentException("User password not configured");
    }

    if (!tenant.isEnabled()) {
      this.passwordHasher.spendTime(password);
      throw new IllegalArgumentException("Invalid password");
    }

    if (!this.passwordHasher.matches(password, tenant)) {
      throw new IllegalArgumentException("Invalid password");
    }
    if (tenant.isTotpEnabled()) {
      throw new IllegalArgumentException(
          "Two-factor authentication is enabled; use SSH to access repositories");
    }
    if (this.passwordHasher.upgradeIfNeeded(tenant, password)) {
      this.tenantRepository.save(tenant);
    }
  }
}
