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

import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.time.Instant;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@NullMarked
public class BootstrapAdminInitializer implements ApplicationRunner {

  private static final int SALT_LENGTH = 16;
  private static final String BOOTSTRAP_EMAIL_DOMAIN = "@originhub.local";

  private final TenantRepository tenantRepository;
  private final boolean enabled;
  private final String username;
  private final String password;

  public BootstrapAdminInitializer(
      final TenantRepository tenantRepository,
      @Value("${originhub.bootstrap.admin.enabled:true}") final boolean enabled,
      @Value("${originhub.bootstrap.admin.username:admin}") final String username,
      @Value("${originhub.bootstrap.admin.password:}") final String password) {

    this.tenantRepository = tenantRepository;
    this.enabled = enabled;
    this.username = username;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(final ApplicationArguments args) {

    if (!this.enabled) {
      return;
    }

    if (this.password.isBlank()) {
      log.warn("Bootstrap admin is enabled but password is blank, skipping user creation");
      return;
    }

    final var normalizedUsername = this.username.toLowerCase(Locale.getDefault());

    if (this.tenantRepository.existsByUsername(normalizedUsername)) {
      log.info("Bootstrap admin already exists, skipping");
      return;
    }

    final var salt = RandomStringUtils.secure().nextAlphanumeric(SALT_LENGTH);

    final var tenant = new Tenant();
    tenant.setUsername(normalizedUsername);
    tenant.setEmail(normalizedUsername + BOOTSTRAP_EMAIL_DOMAIN);
    tenant.setHash(DigestUtils.sha256Hex(this.password + salt));
    tenant.setSalt(salt);
    tenant.setEnabled(true);
    tenant.setCreatedAt(Instant.now());

    this.tenantRepository.save(tenant);

    log.info("Bootstrap admin user created: {}", normalizedUsername);
  }
}
