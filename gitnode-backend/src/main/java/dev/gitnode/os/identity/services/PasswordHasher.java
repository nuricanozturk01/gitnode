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

package dev.gitnode.os.identity.services;

import dev.gitnode.os.identity.entities.Tenant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Hashes and verifies account passwords with bcrypt. Accounts created before this existed still
 * carry a salted SHA-256 hash; those verify as before and are upgraded on the next successful login
 * (see {@link #needsUpgrade(Tenant)}).
 */
@Component
@NullMarked
public class PasswordHasher {

  private static final int COST = 10;
  private static final String BCRYPT_PREFIX = "$2";

  private static final long VERIFIED_TTL_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
  private static final int VERIFIED_MAX_ENTRIES = 2_000;

  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(COST);

  /**
   * Recent successful bcrypt checks. A git client sends its password with every request of one
   * clone, so re-running bcrypt each time would cost a lot of CPU. The key is a digest of the
   * stored hash and the password, so changing the password makes old entries unreachable, and only
   * successes are remembered.
   */
  private final java.util.concurrent.ConcurrentHashMap<String, Long> verified =
      new java.util.concurrent.ConcurrentHashMap<>();

  /** A hash nobody knows the password of, used to spend the same time on unknown accounts. */
  private final String decoyHash = this.encoder.encode("decoy-" + java.util.UUID.randomUUID());

  /**
   * Does the work of a password check without an account, so that an unknown user name cannot be
   * told apart from a wrong password by response time.
   */
  public void spendTime(final String rawPassword) {
    this.encoder.matches(this.preHash(rawPassword), this.decoyHash);
  }

  /** Upgrades a legacy hash after a successful check; returns true when the tenant changed. */
  public boolean upgradeIfNeeded(final Tenant tenant, final String rawPassword) {
    if (!this.needsUpgrade(tenant)) {
      return false;
    }
    this.setPassword(tenant, rawPassword);
    return true;
  }

  public String hash(final String rawPassword) {
    return this.encoder.encode(this.preHash(rawPassword));
  }

  /** Stores a new password on the tenant and drops the legacy salt. */
  public void setPassword(final Tenant tenant, final String rawPassword) {
    tenant.setHash(this.hash(rawPassword));
    tenant.setSalt(null);
  }

  public boolean matches(final String rawPassword, final Tenant tenant) {
    final var stored = tenant.getHash();
    if (stored == null) {
      return false;
    }
    if (this.isBcrypt(stored)) {
      return this.matchesBcrypt(rawPassword, stored);
    }
    final var salt = tenant.getSalt();
    if (salt == null) {
      return false;
    }
    final var legacy = DigestUtils.sha256Hex(rawPassword + salt);
    return MessageDigest.isEqual(
        legacy.getBytes(StandardCharsets.UTF_8), stored.getBytes(StandardCharsets.UTF_8));
  }

  private boolean matchesBcrypt(final String rawPassword, final String stored) {
    final var key = DigestUtils.sha256Hex(stored + '\0' + rawPassword);
    final var now = System.nanoTime();
    final var seen = this.verified.get(key);
    if (seen != null && now - seen < VERIFIED_TTL_NANOS) {
      return true;
    }
    final var ok = this.encoder.matches(this.preHash(rawPassword), stored);
    if (ok) {
      if (this.verified.size() >= VERIFIED_MAX_ENTRIES) {
        this.verified.entrySet().removeIf(entry -> now - entry.getValue() >= VERIFIED_TTL_NANOS);
        if (this.verified.size() >= VERIFIED_MAX_ENTRIES) {
          this.verified.clear();
        }
      }
      this.verified.put(key, now);
    }
    return ok;
  }

  public boolean needsUpgrade(final Tenant tenant) {
    return tenant.getHash() != null && !this.isBcrypt(tenant.getHash());
  }

  public boolean hasPassword(final Tenant tenant) {
    return tenant.getHash() != null;
  }

  private boolean isBcrypt(final String stored) {
    return stored.startsWith(BCRYPT_PREFIX);
  }

  /** bcrypt only reads 72 bytes; hashing first keeps long passphrases fully significant. */
  private String preHash(final String rawPassword) {
    return Base64.getEncoder().encodeToString(DigestUtils.sha256(rawPassword));
  }
}
