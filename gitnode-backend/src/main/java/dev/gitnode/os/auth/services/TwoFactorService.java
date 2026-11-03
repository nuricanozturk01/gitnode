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

import dev.gitnode.os.shared.auth.services.PasswordHasher;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
public class TwoFactorService {

  private static final int RECOVERY_CODES = 10;
  private static final int RECOVERY_CODE_LENGTH = 10;

  public record Status(boolean enabled, int recoveryCodesLeft) {}

  public record Setup(String secret, String otpAuthUri) {}

  private final TenantRepository tenants;
  private final TotpService totp;
  private final PasswordHasher passwordHasher;
  private final RefreshTokenService refreshTokens;

  public Status status(final UUID userId) {
    final var tenant = this.find(userId);
    return new Status(tenant.isTotpEnabled(), this.recoveryHashes(tenant).length);
  }

  /** Generates a fresh, not yet active secret. Fails when 2FA is already on. */
  @Transactional
  public Setup setup(final UUID userId) {
    final var tenant = this.find(userId);
    if (tenant.isTotpEnabled()) {
      throw new BadRequestException("twoFactorAlreadyEnabled");
    }
    final var secret = this.totp.newSecret();
    tenant.setTotpSecret(this.totp.encrypt(secret));
    tenant.setTotpLastStep(null);
    this.tenants.save(tenant);
    return new Setup(secret, this.totp.otpAuthUri(tenant.getEmail(), secret));
  }

  /** Activates 2FA after the user proves the authenticator works; returns the recovery codes. */
  @Transactional
  public List<String> enable(final UUID userId, final String code) {
    final var tenant = this.find(userId);
    if (tenant.isTotpEnabled() || tenant.getTotpSecret() == null) {
      throw new BadRequestException("twoFactorSetupRequired");
    }
    final var step = this.totp.verify(this.totp.decrypt(tenant.getTotpSecret()), code, null);
    if (step.isEmpty()) {
      throw new BadRequestException("invalidTwoFactorCode");
    }
    final var plain = new ArrayList<String>();
    final var hashes = new ArrayList<String>();
    for (var i = 0; i < RECOVERY_CODES; i++) {
      final var recovery =
          RandomStringUtils.secure().nextAlphanumeric(RECOVERY_CODE_LENGTH).toLowerCase();
      plain.add(recovery);
      hashes.add(DigestUtils.sha256Hex(recovery));
    }
    tenant.setTotpEnabled(true);
    tenant.setTotpLastStep(step.getAsLong());
    tenant.setTotpRecoveryCodes(String.join("\n", hashes));
    this.tenants.save(tenant);
    this.refreshTokens.revokeAll(tenant.getId());
    return plain;
  }

  @Transactional
  public void disable(final UUID userId, final String password, final String code) {
    final var tenant = this.find(userId);
    if (!tenant.isTotpEnabled()) {
      return;
    }
    if (!this.passwordHasher.hasPassword(tenant)
        || !this.passwordHasher.matches(password, tenant)) {
      throw new AccessNotAllowedException("wrongPassword");
    }
    if (!this.consumeCode(tenant, code)) {
      throw new AccessNotAllowedException("invalidTwoFactorCode");
    }
    tenant.setTotpEnabled(false);
    tenant.setTotpSecret(null);
    tenant.setTotpRecoveryCodes(null);
    tenant.setTotpLastStep(null);
    this.tenants.save(tenant);
  }

  /** Verifies a login code (TOTP or recovery code) and records it as used. */
  @Transactional
  public boolean verifyLogin(final Tenant tenant, final String code) {
    final var ok = this.consumeCode(tenant, code);
    if (ok) {
      this.tenants.save(tenant);
    }
    return ok;
  }

  private boolean consumeCode(final Tenant tenant, final String rawCode) {
    final var code = rawCode.trim().replace(" ", "").toLowerCase();
    if (tenant.getTotpSecret() == null) {
      return false;
    }
    final var step =
        this.totp.verify(this.totp.decrypt(tenant.getTotpSecret()), code, tenant.getTotpLastStep());
    if (step.isPresent()) {
      tenant.setTotpLastStep(step.getAsLong());
      this.tenants.save(tenant);
      return true;
    }
    final var hash = DigestUtils.sha256Hex(code);
    final var hashes = this.recoveryHashes(tenant);
    if (Arrays.asList(hashes).contains(hash)) {
      tenant.setTotpRecoveryCodes(
          String.join("\n", Arrays.stream(hashes).filter(h -> !h.equals(hash)).toList()));
      this.tenants.save(tenant);
      return true;
    }
    return false;
  }

  private String[] recoveryHashes(final Tenant tenant) {
    final var stored = tenant.getTotpRecoveryCodes();
    return stored == null || stored.isBlank() ? new String[0] : stored.split("\n");
  }

  private Tenant find(final UUID userId) {
    return this.tenants
        .findById(userId)
        .orElseThrow(() -> new AccessNotAllowedException("userNotExist"));
  }
}
