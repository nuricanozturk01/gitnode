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
package com.nuricanozturk.originhub.auth.services;

import com.nuricanozturk.originhub.auth.dtos.LoginForm;
import com.nuricanozturk.originhub.auth.dtos.RecoverPasswordForm;
import com.nuricanozturk.originhub.auth.dtos.RecoveryCodeRequestForm;
import com.nuricanozturk.originhub.auth.dtos.RegistrationForm;
import com.nuricanozturk.originhub.shared.auth.dtos.LoginInfo;
import com.nuricanozturk.originhub.shared.auth.services.JwtUtils;
import com.nuricanozturk.originhub.shared.auth.services.TenantAuthGuard;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.AccessNotAllowedException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.BadRequestException;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@NullMarked
public class AuthService {

  private static final int RECOVERY_CODE_LENGTH = 150;
  private static final int SALT_LENGTH = 16;

  private final TenantRepository tenantRepository;
  private final JwtUtils jwtUtils;

  @Transactional
  public boolean getPasswordRecoveryCode(final RecoveryCodeRequestForm form) {

    final var tenantOptional =
        this.tenantRepository.findByUsernameOrEmail(
            form.getUsernameOrEmail().toLowerCase(Locale.getDefault()));

    if (tenantOptional.isEmpty()) {
      return false;
    }

    final var tenant = tenantOptional.get();

    this.checkPasswordRecoveryCode(tenant);

    // sent email

    return true;
  }

  @Transactional
  public LoginInfo register(final RegistrationForm form) {

    final var formUsername = form.getUsername().toLowerCase(Locale.getDefault());
    final var email = form.getEmail().toLowerCase(Locale.getDefault());

    this.checkUsernameAndEmailInReserved(formUsername, email);

    final var tenant = this.createTenant(formUsername, email, Optional.of(form.getPassword()));

    return this.createLoginInfo(tenant);
  }

  @Transactional
  public LoginInfo login(final LoginForm form) {

    final var tenant =
        this.tenantRepository
            .findByUsernameOrEmail(form.getUsernameOrEmail().toLowerCase(Locale.getDefault()))
            .orElseThrow(() -> new AccessNotAllowedException("userNotExist"));

    this.checkPassword(tenant, form);
    TenantAuthGuard.requireEnabled(tenant);

    return this.createLoginInfo(tenant);
  }

  @Transactional
  public void recoverPassword(final RecoverPasswordForm form) {

    final var tenant =
        this.tenantRepository
            .findByPasswordRecoveryCode(form.getRecoveryCode())
            .orElseThrow(() -> new AccessNotAllowedException("accessDenied"));

    final var salt = RandomStringUtils.secure().nextAlphanumeric(16);

    tenant.setHash(DigestUtils.sha256Hex(form.getPassword() + salt));
    tenant.setSalt(salt);
    tenant.setPasswordRecoveryCode(null);

    this.tenantRepository.save(tenant);
  }

  public LoginInfo getTenantById(final UUID tenantId) {

    final var tenant =
        this.tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new AccessNotAllowedException("userNotExist"));

    TenantAuthGuard.requireEnabled(tenant);

    return this.createLoginInfo(tenant);
  }

  private void checkPasswordRecoveryCode(final Tenant tenant) {

    final var recoveryCode = tenant.getPasswordRecoveryCode();

    if (recoveryCode != null) {
      return;
    }

    final var verificationCode = RandomStringUtils.secure().nextAlphanumeric(RECOVERY_CODE_LENGTH);

    this.tenantRepository.updatePasswordRecoveryCode(tenant.getId(), verificationCode);
  }

  private LoginInfo createLoginInfo(final Tenant tenant) {

    final var accessToken = this.jwtUtils.generateToken(tenant);
    final var refreshToken = this.jwtUtils.generateRefreshToken(tenant);

    return LoginInfo.builder()
        .token(accessToken)
        .refreshToken(refreshToken)
        .email(tenant.getEmail())
        .username(tenant.getUsername())
        .expiresIn(JwtUtils.ACCESS_EXPIRATION_SECONDS)
        .refreshExpiresIn(JwtUtils.REFRESH_EXPIRATION_SECONDS)
        .build();
  }

  private void checkUsernameAndEmailInReserved(final String username, final String email) {

    if (this.tenantRepository.existsByUsername(username)) {
      throw new BadRequestException("usernameInUse");
    }

    if (this.tenantRepository.existsByEmail(email)) {
      throw new BadRequestException("emailInUse");
    }
  }

  private void checkPassword(final Tenant tenant, final LoginForm form) {

    final var hash = DigestUtils.sha256Hex(form.getPassword() + tenant.getSalt());

    if (!hash.equals(tenant.getHash())) {
      throw new AccessNotAllowedException("wrongPassword");
    }
  }

  private Tenant createTenant(
      final String formUsername, final String email, final Optional<String> password) {

    final var salt = RandomStringUtils.secure().nextAlphanumeric(SALT_LENGTH);

    final var tenant = new Tenant();
    tenant.setUsername(formUsername);
    tenant.setEmail(email);
    password.ifPresent(p -> tenant.setHash(DigestUtils.sha256Hex(p + salt)));
    tenant.setSalt(salt);
    tenant.setEnabled(true);
    tenant.setCreatedAt(Instant.now());

    return this.tenantRepository.save(tenant);
  }
}
