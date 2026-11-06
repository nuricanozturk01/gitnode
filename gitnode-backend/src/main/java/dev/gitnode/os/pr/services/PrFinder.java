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
package dev.gitnode.os.pr.services;

import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.pr.entities.PullRequest;
import dev.gitnode.os.pr.repositories.PrRepository;
import dev.gitnode.os.repo.api.RepoCommandPort;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@NullMarked
class PrFinder {

  private final RepoLookupPort repoLookup;
  private final RepoCommandPort repoCommands;
  private final PrRepository prRepository;
  private final TenantQueryPort tenants;

  RepoRef findRepo(final String owner, final String repoName) {
    return this.repoLookup
        .find(owner, repoName)
        .orElseThrow(() -> new ItemNotFoundException("Repository not found"));
  }

  RepoRef findRepoForUpdate(final String owner, final String repoName) {
    final var repo = this.findRepo(owner, repoName);
    if (!this.repoCommands.lockForUpdate(repo.id())) {
      throw new ItemNotFoundException("Repository not found");
    }
    return repo;
  }

  PullRequest findPr(final UUID repoId, final int number) {
    return this.prRepository
        .findByRepoIdAndNumber(repoId, number)
        .orElseThrow(() -> new ItemNotFoundException("Pull request not found: #" + number));
  }

  PullRequest findPrForUpdate(final UUID repoId, final int number) {
    return this.prRepository
        .findByRepoIdAndNumberForUpdate(repoId, number)
        .orElseThrow(() -> new ItemNotFoundException("Pull request not found: #" + number));
  }

  TenantRef findTenant(final UUID id) {
    return this.tenants.findById(id).orElseThrow(() -> new ItemNotFoundException("User not found"));
  }

  java.util.Optional<RepoRef> findRepoOrNull(final String owner, final String repoName) {
    return this.repoLookup.find(owner, repoName);
  }

  java.util.Optional<PullRequest> findPrOrNull(final UUID repoId, final int number) {
    return this.prRepository.findByRepoIdAndNumber(repoId, number);
  }

  /** The account id of a user name (case-insensitive), or empty. */
  java.util.Optional<UUID> findTenantIdIgnoreCase(final String username) {
    return this.tenants.findByUsernameIgnoreCase(username).map(TenantRef::id);
  }

  java.util.Map<UUID, TenantRef> findTenants(final java.util.Collection<UUID> ids) {
    return this.tenants.findAllByIds(ids);
  }
}
