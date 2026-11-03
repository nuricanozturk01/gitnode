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

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Read-only account lookups for other modules; returns immutable views, never the entity. */
@NullMarked
public interface TenantQueryPort {

  /** The public facts about an account that other modules need to render or authorize. */
  record TenantRef(
      UUID id,
      String username,
      String email,
      @Nullable String displayName,
      @Nullable String avatarUrl,
      boolean enabled) {

    public String nameForDisplay() {
      return this.displayName != null ? this.displayName : this.username;
    }
  }

  Optional<TenantRef> findById(UUID id);

  Optional<TenantRef> findByUsername(String username);

  Optional<TenantRef> findByUsernameIgnoreCase(String username);

  /** Matches the user name or the primary e-mail, like the login form does. */
  Optional<TenantRef> findByUsernameOrEmail(String usernameOrEmail);

  /** Accounts whose primary e-mail is one of the given addresses, keyed by that address. */
  Map<String, TenantRef> findAllByEmails(Collection<String> emails);

  /** Accounts that do not exist are simply absent from the result. */
  Map<UUID, TenantRef> findAllByIds(Collection<UUID> ids);
}
