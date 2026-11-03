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
package dev.gitnode.os.collaborator.mappers;

import dev.gitnode.os.collaborator.dtos.CollaboratorInfo;
import dev.gitnode.os.collaborator.entities.RepoCollaborator;
import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Builds collaborator views; the entity stores accounts by id, names are looked up in batch. */
@Component
@RequiredArgsConstructor
@NullMarked
public class CollaboratorMapper {

  private final TenantQueryPort tenants;

  public CollaboratorInfo toInfo(final RepoCollaborator collaborator) {
    return this.toInfo(collaborator, this.accounts(List.of(collaborator)));
  }

  public Page<CollaboratorInfo> toInfoPage(final Page<RepoCollaborator> page) {
    final var accounts = this.accounts(page.getContent());
    return page.map(collaborator -> this.toInfo(collaborator, accounts));
  }

  private Map<UUID, TenantRef> accounts(final List<RepoCollaborator> collaborators) {
    final var ids = new HashSet<UUID>();
    for (final var collaborator : collaborators) {
      ids.add(collaborator.getTenantId());
      ids.add(collaborator.getInvitedById());
    }
    return this.tenants.findAllByIds(ids);
  }

  private CollaboratorInfo toInfo(
      final RepoCollaborator collaborator, final Map<UUID, TenantRef> accounts) {
    final @Nullable TenantRef tenant = accounts.get(collaborator.getTenantId());
    final @Nullable TenantRef inviter = accounts.get(collaborator.getInvitedById());
    return new CollaboratorInfo(
        collaborator.getId(),
        tenant == null ? "ghost" : tenant.username(),
        tenant == null ? null : tenant.displayName(),
        tenant == null ? "" : avatarUrl(tenant),
        collaborator.getPermissions(),
        collaborator.getStatus(),
        inviter == null ? "ghost" : inviter.username(),
        collaborator.getCreatedAt(),
        collaborator.getUpdatedAt());
  }

  private static String avatarUrl(final TenantRef tenant) {
    final @Nullable String stored = tenant.avatarUrl();
    if (stored != null && !stored.isBlank()) {
      return stored;
    }
    final var hash =
        DigestUtils.md5Hex(tenant.email().trim().toLowerCase(java.util.Locale.getDefault()));
    return "https://www.gravatar.com/avatar/" + hash + "?d=identicon";
  }
}
