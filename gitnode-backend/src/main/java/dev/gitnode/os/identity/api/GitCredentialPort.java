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

import org.jspecify.annotations.NullMarked;

/** Verifies the account password a Git client sends over HTTPS. */
@NullMarked
public interface GitCredentialPort {

  /**
   * Checks the password of the named account. Accounts with two-factor authentication enabled are
   * refused (the second factor cannot be supplied by a Git client; use SSH instead).
   *
   * @throws IllegalArgumentException when the user is unknown, has no password, the password is
   *     wrong or two-factor authentication is enabled
   */
  void verifyPassword(String username, String password);
}
