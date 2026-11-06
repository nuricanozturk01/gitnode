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

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Persistence port for accounts, for the modules that manage them. */
@NullMarked
public interface TenantAccountStore {

  /** A new, unsaved account. */
  TenantAccount newAccount();

  TenantAccount save(TenantAccount account);

  void delete(TenantAccount account);

  Optional<TenantAccount> findById(UUID id);

  Optional<TenantAccount> findByUsername(String username);

  Optional<TenantAccount> findByUsernameIgnoreCase(String username);

  Optional<TenantAccount> findByUsernameOrEmail(String emailOrUsername);

  Optional<TenantAccount> findByPasswordRecoveryCode(String recoveryCode);

  List<TenantAccount> findAllByEmailIn(Collection<String> emails);

  List<TenantAccount> findTop10ByUsernamePrefix(String prefix);

  Page<TenantAccount> findAllNewestFirst(Pageable pageable);

  Page<TenantAccount> searchByUsernameNewestFirst(String username, Pageable pageable);

  boolean existsByUsername(String username);

  boolean existsByEmail(String email);

  void updatePasswordRecoveryCode(UUID id, String recoveryCode, Instant expiresAt);

  long count();

  long countEnabled();

  long countCreatedAfter(Instant since);

  List<TenantAccount> findAllCreatedAfter(Instant since);
}
