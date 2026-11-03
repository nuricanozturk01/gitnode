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

import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.identity.entities.Tenant;
import dev.gitnode.os.identity.repositories.TenantRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@NullMarked
public class TenantQueryAdapter implements TenantQueryPort {

  private final TenantRepository tenants;

  @Override
  public Optional<TenantRef> findById(final UUID id) {
    return this.tenants.findById(id).map(TenantQueryAdapter::toRef);
  }

  @Override
  public Optional<TenantRef> findByUsername(final String username) {
    return this.tenants.findByUsername(username).map(TenantQueryAdapter::toRef);
  }

  @Override
  public Optional<TenantRef> findByUsernameIgnoreCase(final String username) {
    return this.tenants.findByUsernameIgnoreCase(username).map(TenantQueryAdapter::toRef);
  }

  @Override
  public Optional<TenantRef> findByUsernameOrEmail(final String usernameOrEmail) {
    return this.tenants.findByUsernameOrEmail(usernameOrEmail).map(TenantQueryAdapter::toRef);
  }

  @Override
  public Map<String, TenantRef> findAllByEmails(final Collection<String> emails) {
    if (emails.isEmpty()) {
      return Map.of();
    }
    return this.tenants.findAllByEmailIn(emails).stream()
        .map(TenantQueryAdapter::toRef)
        .collect(Collectors.toMap(TenantRef::email, Function.identity()));
  }

  @Override
  public Map<UUID, TenantRef> findAllByIds(final Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    return this.tenants.findAllById(ids).stream()
        .map(TenantQueryAdapter::toRef)
        .collect(Collectors.toMap(TenantRef::id, Function.identity()));
  }

  private static TenantRef toRef(final Tenant tenant) {
    return new TenantRef(
        tenant.getId(),
        tenant.getUsername(),
        tenant.getEmail(),
        tenant.getDisplayName(),
        tenant.getAvatarUrl(),
        tenant.isEnabled());
  }
}
