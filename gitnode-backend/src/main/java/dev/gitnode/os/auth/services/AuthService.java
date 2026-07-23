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
package dev.gitnode.os.auth.services;

import dev.gitnode.os.auth.dtos.LoginForm;
import dev.gitnode.os.auth.dtos.RecoverPasswordForm;
import dev.gitnode.os.auth.dtos.RecoveryCodeRequestForm;
import dev.gitnode.os.auth.dtos.RegistrationForm;
import dev.gitnode.os.events.auth.TenantRegisteredEvent;
import dev.gitnode.os.shared.audit.services.AuditLogService;
import dev.gitnode.os.shared.auth.dtos.LoginInfo;
import dev.gitnode.os.shared.auth.services.JwtUtils;
import dev.gitnode.os.shared.auth.services.TenantAuthGuard;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@NullMarked
public class AuthService {

  private static final int RECOVERY_CODE_LENGTH = 150;
  private static final int SALT_LENGTH = 16;
  private static final String AUTH_LOGIN_SUCCESS = "AUTH_LOGIN_SUCCESS";
  private static final String AUTH_LOGIN_FAILED = "AUTH_LOGIN_FAILED";
  private static final String ENTITY_TYPE_AUTH = "auth";

  private final TenantRepository tenantRepository;
  private final JwtUtils jwtUtils;
  private final AuditLogService auditLogService;
  private final ApplicationEventPublisher eventPublisher;

  @Value("${gitnode.audit.enabled:true}")
  private boolean auditEnabled;

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

    this.eventPublisher.publishEvent(new TenantRegisteredEvent(tenant.getUsername(), form.getPassword(), tenant.getSalt()));

    return this.createLoginInfo(tenant);
  }

  @Transactional
  public LoginInfo login(final LoginForm form) {

    final var usernameOrEmail = form.getUsernameOrEmail().toLowerCase(Locale.getDefault());

    try {
      final var tenant =
          this.tenantRepository
              .findByUsernameOrEmail(usernameOrEmail)
              .orElseThrow(() -> new AccessNotAllowedException("userNotExist"));

      this.checkPassword(tenant, form);
      TenantAuthGuard.requireEnabled(tenant);

      final var loginInfo = this.createLoginInfo(tenant);
      this.auditLogin(AUTH_LOGIN_SUCCESS, tenant.getUsername(), null);
      return loginInfo;

    } catch (final AccessNotAllowedException ex) {
      this.auditLogin(AUTH_LOGIN_FAILED, usernameOrEmail, ex.getMessage());
      throw ex;
    }
  }

  private void auditLogin(final String action, final String actor, final @Nullable String reason) {

    if (!this.auditEnabled) {
      return;
    }
    final var ip = this.resolveIpAddress();
    final var details = reason != null ? "reason=" + reason : null;
    this.auditLogService.log(actor, action, ENTITY_TYPE_AUTH, null, details, ip);
  }

  private @Nullable String resolveIpAddress() {

    final var attrs = RequestContextHolder.getRequestAttributes();
    if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
      return null;
    }
    final HttpServletRequest request = servletAttrs.getRequest();
    final var forwarded = request.getHeader("X-Forwarded-For");
    return (forwarded != null && !forwarded.isBlank())
        ? forwarded.split(",")[0].trim()
        : request.getRemoteAddr();
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
