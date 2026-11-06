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

import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.repo.dtos.RepoInfo;
import dev.gitnode.os.repo.entities.Repo;
import dev.gitnode.os.repo.mappers.RepoMapper;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Builds {@link RepoInfo} views, resolving repository owners in one batch per call. */
@Component
@RequiredArgsConstructor
@NullMarked
public class RepoInfoAssembler {

  private final RepoMapper mapper;
  private final RepoOwnerResolver ownerResolver;

  public RepoInfo toDto(final Repo repo) {
    return this.mapper.toDto(repo, this.ownersOf(List.of(repo)));
  }

  public Page<RepoInfo> toDtoPage(final Page<Repo> page) {
    final var owners = this.ownersOf(page.getContent());
    return page.map(repo -> this.mapper.toDto(repo, owners));
  }

  private Map<UUID, TenantRef> ownersOf(final Collection<Repo> repos) {
    final Set<UUID> ids = new HashSet<>();
    for (final var repo : repos) {
      ids.add(repo.getOwnerId());
      if (repo.getForkedFrom() != null) {
        ids.add(repo.getForkedFrom().getOwnerId());
      }
    }
    return this.ownerResolver.owners(ids);
  }
}
