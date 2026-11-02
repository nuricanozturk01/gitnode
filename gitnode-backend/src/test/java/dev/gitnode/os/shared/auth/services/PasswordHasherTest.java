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

package dev.gitnode.os.shared.auth.services;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gitnode.os.shared.tenant.entities.Tenant;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PasswordHasher")
class PasswordHasherTest {

  private final PasswordHasher hasher = new PasswordHasher();

  @Test
  @DisplayName("new passwords are bcrypt, salted per hash and verify")
  void bcryptRoundTrip() {
    Tenant tenant = new Tenant();
    hasher.setPassword(tenant, "Correct horse 1");
    Tenant other = new Tenant();
    hasher.setPassword(other, "Correct horse 1");

    assertThat(tenant.getHash()).startsWith("$2").isNotEqualTo(other.getHash());
    assertThat(tenant.getSalt()).isNull();
    assertThat(hasher.matches("Correct horse 1", tenant)).isTrue();
    assertThat(hasher.matches("Correct horse 2", tenant)).isFalse();
    assertThat(hasher.needsUpgrade(tenant)).isFalse();
  }

  @Test
  @DisplayName("passwords longer than bcrypt's 72 bytes stay fully significant")
  void longPasswords() {
    String base = "a".repeat(80);
    Tenant tenant = new Tenant();
    hasher.setPassword(tenant, base + "X");

    assertThat(hasher.matches(base + "X", tenant)).isTrue();
    assertThat(hasher.matches(base + "Y", tenant)).isFalse();
  }

  @Test
  @DisplayName("legacy salted SHA-256 hashes still verify and are flagged for upgrade")
  void legacyHashes() {
    Tenant tenant = new Tenant();
    tenant.setSalt("fixedsalt1234567");
    tenant.setHash(DigestUtils.sha256Hex("Legacy pass 1" + "fixedsalt1234567"));

    assertThat(hasher.matches("Legacy pass 1", tenant)).isTrue();
    assertThat(hasher.matches("Legacy pass 2", tenant)).isFalse();
    assertThat(hasher.needsUpgrade(tenant)).isTrue();
  }

  @Test
  @DisplayName("accounts without a password never match")
  void noPassword() {
    Tenant tenant = new Tenant();

    assertThat(hasher.hasPassword(tenant)).isFalse();
    assertThat(hasher.matches("anything", tenant)).isFalse();
    assertThat(hasher.needsUpgrade(tenant)).isFalse();
  }

  @Test
  @DisplayName("a repeated successful check is served from memory and a changed password is not")
  void verificationCache() {
    Tenant tenant = new Tenant();
    hasher.setPassword(tenant, "Correct horse 1");

    assertThat(hasher.matches("Correct horse 1", tenant)).isTrue();
    long start = System.nanoTime();
    for (int i = 0; i < 200; i++) {
      assertThat(hasher.matches("Correct horse 1", tenant)).isTrue();
    }
    assertThat(System.nanoTime() - start)
        .isLessThan(java.util.concurrent.TimeUnit.SECONDS.toNanos(1));

    assertThat(hasher.matches("Wrong horse 1", tenant)).isFalse();
    hasher.setPassword(tenant, "New password 2");
    assertThat(hasher.matches("Correct horse 1", tenant)).isFalse();
    assertThat(hasher.matches("New password 2", tenant)).isTrue();
  }

  @Test
  @DisplayName("upgradeIfNeeded moves a legacy hash to bcrypt once and leaves bcrypt hashes alone")
  void upgradeIfNeeded() {
    Tenant tenant = new Tenant();
    tenant.setSalt("fixedsalt1234567");
    tenant.setHash(DigestUtils.sha256Hex("Legacy pass 1" + "fixedsalt1234567"));

    assertThat(hasher.upgradeIfNeeded(tenant, "Legacy pass 1")).isTrue();
    assertThat(tenant.getHash()).startsWith("$2");
    assertThat(tenant.getSalt()).isNull();
    assertThat(hasher.matches("Legacy pass 1", tenant)).isTrue();
    assertThat(hasher.upgradeIfNeeded(tenant, "Legacy pass 1")).isFalse();
  }

  @Test
  @DisplayName("spendTime takes about as long as a real check, so unknown users are not detectable")
  void spendTimeIsComparableToACheck() {
    Tenant tenant = new Tenant();
    hasher.setPassword(tenant, "Correct horse 1");
    hasher.spendTime("warm up");
    hasher.matches("warm up 2", tenant);

    long start = System.nanoTime();
    hasher.spendTime("another guess");
    long decoy = System.nanoTime() - start;
    start = System.nanoTime();
    hasher.matches("yet another guess", tenant);
    long real = System.nanoTime() - start;

    assertThat(decoy).isGreaterThan(real / 4);
  }
}
