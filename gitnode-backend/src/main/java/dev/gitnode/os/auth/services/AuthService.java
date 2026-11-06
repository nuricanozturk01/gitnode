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
import dev.gitnode.os.identity.api.PasswordHashing;
import dev.gitnode.os.identity.api.TenantAccount;
import dev.gitnode.os.identity.api.TenantAccountStore;
import dev.gitnode.os.identity.api.TokenService;
import dev.gitnode.os.identity.dtos.LoginInfo;
import dev.gitnode.os.shared.audit.services.AuditLogService;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.TokenExpiredException;
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
  static final java.time.Duration RECOVERY_TTL = java.time.Duration.ofMinutes(30);
  private static final int SALT_LENGTH = 16;
  private static final String AUTH_LOGIN_SUCCESS = "AUTH_LOGIN_SUCCESS";
  private static final String AUTH_LOGIN_FAILED = "AUTH_LOGIN_FAILED";
  private static final String ENTITY_TYPE_AUTH = "auth";

  private final TenantAccountStore tenantRepository;
  private final TokenService jwtUtils;
  private final PasswordHashing passwordHasher;
  private final PasswordRecoveryNotifier recoveryNotifier;
  private final dev.gitnode.os.auth.services.RefreshTokenService refreshTokens;
  private final AuditLogService auditLogService;
  private final TwoFactorService twoFactor;

  @Value("${gitnode.audit.enabled:true}")
  private boolean auditEnabled;

  /**
   * Starts a password recovery. The outcome is identical for unknown, disabled and valid accounts
   * so the endpoint cannot be used to discover which accounts exist.
   */
  @Transactional
  public void requestPasswordRecovery(final RecoveryCodeRequestForm form) {

    this.tenantRepository
        .findByUsernameOrEmail(form.getUsernameOrEmail().toLowerCase(Locale.getDefault()))
        .filter(TenantAccount::isEnabled)
        .ifPresent(
            tenant -> {
              final var code = RandomStringUtils.secure().nextAlphanumeric(RECOVERY_CODE_LENGTH);
              this.tenantRepository.updatePasswordRecoveryCode(
                  tenant.getId(), DigestUtils.sha256Hex(code), Instant.now().plus(RECOVERY_TTL));
              this.recoveryNotifier.send(tenant, code);
            });
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

    final var usernameOrEmail = form.getUsernameOrEmail().toLowerCase(Locale.getDefault());

    try {
      final var tenant =
          this.tenantRepository
              .findByUsernameOrEmail(usernameOrEmail)
              .orElseThrow(
                  () -> {
                    // Same error and about the same time as a wrong password: no account probing.
                    this.passwordHasher.spendTime(form.getPassword());
                    return new AccessNotAllowedException("wrongPassword");
                  });

      this.checkPassword(tenant, form);
      PasswordHashing.requireEnabled(tenant);
      this.checkSecondFactor(tenant, form);

      final var loginInfo = this.createLoginInfo(tenant);
      this.auditLogin(AUTH_LOGIN_SUCCESS, tenant.getUsername(), null);
      return loginInfo;

    } catch (final AccessNotAllowedException ex) {
      this.auditLogin(AUTH_LOGIN_FAILED, usernameOrEmail, ex.getMessage());
      throw ex;
    }
  }

  private void checkSecondFactor(final TenantAccount tenant, final LoginForm form) {

    if (!tenant.isTotpEnabled()) {
      return;
    }
    if (form.getOtp() == null || form.getOtp().isBlank()) {
      throw new AccessNotAllowedException("twoFactorRequired");
    }
    if (!this.twoFactor.verifyLogin(tenant, form.getOtp())) {
      throw new AccessNotAllowedException("invalidTwoFactorCode");
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
            .findByPasswordRecoveryCode(DigestUtils.sha256Hex(form.getRecoveryCode()))
            .filter(
                found ->
                    found.getPasswordRecoveryExpiresAt() != null
                        && found.getPasswordRecoveryExpiresAt().isAfter(Instant.now()))
            .filter(TenantAccount::isEnabled)
            .orElseThrow(() -> new AccessNotAllowedException("accessDenied"));

    this.passwordHasher.setPassword(tenant, form.getPassword());
    tenant.setPasswordRecoveryCode(null);
    tenant.setPasswordRecoveryExpiresAt(null);

    this.tenantRepository.save(tenant);
    this.refreshTokens.revokeAll(tenant.getId());
  }

  /** Exchanges a refresh token for a new access token and a rotated refresh token. */
  @Transactional(noRollbackFor = TokenExpiredException.class)
  public LoginInfo refresh(final String refreshToken) {

    final var rotation = this.refreshTokens.rotate(refreshToken, this::findEnabledTenant);

    return this.createLoginInfo(rotation.tenant(), rotation.refreshToken());
  }

  @Transactional
  public void logout(final String refreshToken) {
    this.refreshTokens.revoke(refreshToken);
  }

  private TenantAccount findEnabledTenant(final UUID tenantId) {

    final var tenant =
        this.tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new AccessNotAllowedException("userNotExist"));

    PasswordHashing.requireEnabled(tenant);

    return tenant;
  }

  private LoginInfo createLoginInfo(final TenantAccount tenant) {
    return this.createLoginInfo(tenant, this.refreshTokens.issue(tenant));
  }

  private LoginInfo createLoginInfo(final TenantAccount tenant, final String refreshToken) {

    final var accessToken = this.jwtUtils.generateToken(tenant);

    return LoginInfo.builder()
        .token(accessToken)
        .refreshToken(refreshToken)
        .email(tenant.getEmail())
        .username(tenant.getUsername())
        .expiresIn(TokenService.ACCESS_EXPIRATION_SECONDS)
        .refreshExpiresIn(TokenService.REFRESH_EXPIRATION_SECONDS)
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

  private void checkPassword(final TenantAccount tenant, final LoginForm form) {

    if (!this.passwordHasher.matches(form.getPassword(), tenant)) {
      throw new AccessNotAllowedException("wrongPassword");
    }
    if (this.passwordHasher.upgradeIfNeeded(tenant, form.getPassword())) {
      this.tenantRepository.save(tenant);
    }
  }

  private TenantAccount createTenant(
      final String formUsername, final String email, final Optional<String> password) {

    final var tenant = this.tenantRepository.newAccount();
    tenant.setUsername(formUsername);
    tenant.setEmail(email);
    password.ifPresent(p -> this.passwordHasher.setPassword(tenant, p));
    tenant.setEnabled(true);
    tenant.setCreatedAt(Instant.now());

    return this.tenantRepository.save(tenant);
  }
}
