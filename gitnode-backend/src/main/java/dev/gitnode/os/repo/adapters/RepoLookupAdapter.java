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
package dev.gitnode.os.repo.adapters;

import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.entities.Repo;
import dev.gitnode.os.repo.repositories.RepoRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@NullMarked
public class RepoLookupAdapter implements RepoLookupPort {

  private final RepoRepository repos;

  @Override
  public Optional<RepoRef> find(final String owner, final String repoName) {
    return this.repos.findByOwnerUsernameAndName(owner, repoName).map(RepoLookupAdapter::toRef);
  }

  @Override
  public Optional<RepoRef> findById(final UUID repoId) {
    return this.repos.findByIdWithOwner(repoId).map(RepoLookupAdapter::toRef);
  }

  @Override
  public Optional<HeadBranchPolicy> headBranchPolicy(final UUID repoId) {
    return this.repos
        .findById(repoId)
        .map(
            repo ->
                new HeadBranchPolicy(
                    repo.isDeleteHeadBranchOnPrMerge(), repo.isDeleteHeadBranchOnPrClose()));
  }

  @Override
  public java.util.Map<UUID, RepoRef> findAllByIds(final java.util.Collection<UUID> repoIds) {
    if (repoIds.isEmpty()) {
      return java.util.Map.of();
    }
    return this.repos.findAllById(repoIds).stream()
        .map(RepoLookupAdapter::toRef)
        .collect(java.util.stream.Collectors.toMap(RepoRef::id, ref -> ref));
  }

  @Override
  public java.util.Map<UUID, UUID> findOwnerIds(final java.util.Collection<UUID> repoIds) {
    if (repoIds.isEmpty()) {
      return java.util.Map.of();
    }
    return this.repos.findOwnerIdsByRepoIds(new java.util.HashSet<>(repoIds)).stream()
        .collect(java.util.stream.Collectors.toMap(row -> (UUID) row[0], row -> (UUID) row[1]));
  }

  private static RepoRef toRef(final Repo repo) {
    return new RepoRef(
        repo.getId(),
        repo.getOwner().getId(),
        repo.getOwner().getUsername(),
        repo.getName(),
        repo.isPrivate(),
        repo.getDefaultBranch(),
        repo.isAiPrReviewEnabled(),
        repo.getDescription());
  }
}
