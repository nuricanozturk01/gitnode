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
package dev.gitnode.os.identity.adapters;

import dev.gitnode.os.identity.api.TenantAccount;
import dev.gitnode.os.identity.api.TenantAccountStore;
import dev.gitnode.os.identity.entities.Tenant;
import dev.gitnode.os.identity.repositories.TenantRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** Backs {@link TenantAccountStore} with the tenant repository. */
@Service
@RequiredArgsConstructor
@NullMarked
public class TenantAccountStoreAdapter implements TenantAccountStore {

  private final TenantRepository tenants;

  private static Tenant entity(final TenantAccount account) {
    if (account instanceof Tenant tenant) {
      return tenant;
    }
    throw new IllegalArgumentException("Unsupported account type: " + account.getClass());
  }

  private static List<TenantAccount> widen(final List<Tenant> list) {
    return List.copyOf(list);
  }

  @Override
  public TenantAccount newAccount() {
    return new Tenant();
  }

  @Override
  public TenantAccount save(final TenantAccount account) {
    return this.tenants.save(entity(account));
  }

  @Override
  public void delete(final TenantAccount account) {
    this.tenants.delete(entity(account));
  }

  @Override
  public Optional<TenantAccount> findById(final UUID id) {
    return this.tenants.findById(id).map(t -> t);
  }

  @Override
  public Optional<TenantAccount> findByUsername(final String username) {
    return this.tenants.findByUsername(username).map(t -> t);
  }

  @Override
  public Optional<TenantAccount> findByUsernameIgnoreCase(final String username) {
    return this.tenants.findByUsernameIgnoreCase(username).map(t -> t);
  }

  @Override
  public Optional<TenantAccount> findByUsernameOrEmail(final String emailOrUsername) {
    return this.tenants.findByUsernameOrEmail(emailOrUsername).map(t -> t);
  }

  @Override
  public Optional<TenantAccount> findByPasswordRecoveryCode(final String recoveryCode) {
    return this.tenants.findByPasswordRecoveryCode(recoveryCode).map(t -> t);
  }

  @Override
  public List<TenantAccount> findAllByEmailIn(final Collection<String> emails) {
    return widen(this.tenants.findAllByEmailIn(emails));
  }

  @Override
  public List<TenantAccount> findTop10ByUsernamePrefix(final String prefix) {
    return widen(this.tenants.findTop10ByUsernameIgnoreCaseStartingWithOrderByUsernameAsc(prefix));
  }

  @Override
  public Page<TenantAccount> findAllNewestFirst(final Pageable pageable) {
    return this.tenants.findAllByOrderByCreatedAtDesc(pageable).map(t -> t);
  }

  @Override
  public Page<TenantAccount> searchByUsernameNewestFirst(
      final String username, final Pageable pageable) {
    return this.tenants
        .findAllByUsernameContainingIgnoreCaseOrderByCreatedAtDesc(username, pageable)
        .map(t -> t);
  }

  @Override
  public boolean existsByUsername(final String username) {
    return this.tenants.existsByUsername(username);
  }

  @Override
  public boolean existsByEmail(final String email) {
    return this.tenants.existsByEmail(email);
  }

  @Override
  public void updatePasswordRecoveryCode(
      final UUID id, final String recoveryCode, final Instant expiresAt) {
    this.tenants.updatePasswordRecoveryCode(id, recoveryCode, expiresAt);
  }

  @Override
  public long count() {
    return this.tenants.count();
  }

  @Override
  public long countEnabled() {
    return this.tenants.countByEnabledTrue();
  }

  @Override
  public long countCreatedAfter(final Instant since) {
    return this.tenants.countByCreatedAtAfter(since);
  }

  @Override
  public List<TenantAccount> findAllCreatedAfter(final Instant since) {
    return widen(this.tenants.findAllByCreatedAtAfter(since));
  }
}
