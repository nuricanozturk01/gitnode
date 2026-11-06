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
package dev.gitnode.os.repo.services;

import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

/** Resolves repository owner ids to account facts (repositories only store the owner id). */
@Component
@RequiredArgsConstructor
@NullMarked
public class RepoOwnerResolver {

  private final TenantQueryPort tenants;

  public TenantRef owner(final UUID ownerId) {
    return this.tenants
        .findById(ownerId)
        .orElseThrow(() -> new ItemNotFoundException("Repository owner not found"));
  }

  public String username(final UUID ownerId) {
    return this.owner(ownerId).username();
  }

  /** Owners keyed by id; one query for all of them. */
  public Map<UUID, TenantRef> owners(final Collection<UUID> ownerIds) {
    return ownerIds.isEmpty() ? Map.of() : this.tenants.findAllByIds(ownerIds);
  }
}
