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
package dev.gitnode.os.identity.api;

import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import org.jspecify.annotations.NullMarked;

/** Password hashing and verification for accounts. */
@NullMarked
public interface PasswordHashing {

  /** Spends the time of a password check so unknown users cannot be told apart by timing. */
  void spendTime(String rawPassword);

  /** Upgrades a legacy hash after a successful check; true when the account changed. */
  boolean upgradeIfNeeded(TenantAccount account, String rawPassword);

  String hash(String rawPassword);

  void setPassword(TenantAccount account, String rawPassword);

  boolean matches(String rawPassword, TenantAccount account);

  boolean needsUpgrade(TenantAccount account);

  boolean hasPassword(TenantAccount account);

  /** Rejects disabled accounts. */
  static void requireEnabled(final TenantAccount account) {
    if (!account.isEnabled()) {
      throw new AccessNotAllowedException("userDisabled");
    }
  }
}
