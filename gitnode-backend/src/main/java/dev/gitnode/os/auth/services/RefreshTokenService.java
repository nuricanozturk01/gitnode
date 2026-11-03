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

import dev.gitnode.os.auth.entities.RefreshToken;
import dev.gitnode.os.auth.repositories.RefreshTokenRepository;
import dev.gitnode.os.identity.entities.Tenant;
import dev.gitnode.os.identity.services.JwtUtils;
import dev.gitnode.os.shared.errorhandling.exceptions.TokenExpiredException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues refresh tokens that are single-use. A used token presented again after the grace window
 * means it was stolen or replayed, so the whole login family is revoked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class RefreshTokenService {

  /** Tabs sharing one login may race to refresh; a reuse this soon is rejected without revoking. */
  static final Duration RACE_GRACE = Duration.ofSeconds(15);

  private final RefreshTokenRepository tokens;
  private final JwtUtils jwtUtils;

  /** Starts a new login family and returns its first refresh token. */
  @Transactional
  public String issue(final Tenant tenant) {
    return this.issue(tenant, UUID.randomUUID());
  }

  /** Validates and consumes {@code presented}; returns the user id and the successor token. */
  @Transactional(noRollbackFor = TokenExpiredException.class)
  public Rotation rotate(final String presented, final TenantLookup lookup) {
    final var claims = this.jwtUtils.extractRefreshClaims(presented);
    final var now = Instant.now();
    final var stored = this.tokens.findById(claims.tokenId()).orElseThrow(this::invalid);
    if (!stored.getUserId().equals(claims.userId())
        || !stored.getFamilyId().equals(claims.familyId())
        || stored.getRevokedAt() != null
        || !stored.getExpiresAt().isAfter(now)) {
      throw this.invalid();
    }
    if (this.tokens.claim(stored.getId(), now) == 0) {
      this.handleReuse(stored, now);
      throw this.invalid();
    }
    final var tenant = lookup.find(claims.userId());
    return new Rotation(tenant, this.issue(tenant, claims.familyId()));
  }

  /** Revokes the login family of {@code presented}; unknown or invalid tokens are ignored. */
  @Transactional
  public void revoke(final String presented) {
    try {
      this.tokens.revokeFamily(
          this.jwtUtils.extractRefreshClaims(presented).familyId(), Instant.now());
    } catch (final TokenExpiredException ignored) {
      // nothing to revoke
    }
  }

  @Transactional
  public void revokeAll(final UUID userId) {
    this.tokens.revokeAllForUser(userId, Instant.now());
  }

  @Scheduled(cron = "0 17 3 * * *")
  @Transactional
  public void purgeExpired() {
    final var removed = this.tokens.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(1)));
    log.debug("Purged {} expired refresh tokens", removed);
  }

  private void handleReuse(final RefreshToken stored, final Instant now) {
    final var usedAt = stored.getUsedAt();
    if (usedAt != null && Duration.between(usedAt, now).compareTo(RACE_GRACE) <= 0) {
      return;
    }
    final var revoked = this.tokens.revokeFamily(stored.getFamilyId(), now);
    log.warn(
        "Refresh token reuse detected for user {}; revoked {} tokens", stored.getUserId(), revoked);
  }

  private String issue(final Tenant tenant, final UUID familyId) {
    final var id = UUID.randomUUID();
    final var token = new RefreshToken();
    token.setId(id);
    token.setFamilyId(familyId);
    token.setUserId(tenant.getId());
    token.setExpiresAt(Instant.now().plusSeconds(JwtUtils.REFRESH_EXPIRATION_SECONDS));
    this.tokens.save(token);
    return this.jwtUtils.generateRefreshToken(tenant, id, familyId);
  }

  private TokenExpiredException invalid() {
    return new TokenExpiredException("invalidToken");
  }

  /** Loads the user for a rotation and rejects disabled or missing accounts. */
  @FunctionalInterface
  public interface TenantLookup {
    Tenant find(UUID userId);
  }

  public record Rotation(Tenant tenant, String refreshToken) {}
}
