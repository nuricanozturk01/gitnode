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
package dev.gitnode.os.admin.services;

import dev.gitnode.os.admin.dtos.AdminRepoSummary;
import dev.gitnode.os.shared.repo.entities.Repo;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NullMarked
public class AdminRepoService {

  private final RepoRepository repoRepository;

  @Transactional(readOnly = true)
  public Page<AdminRepoSummary> listRepos(
      final Pageable pageable, final @Nullable String query, final @Nullable String owner) {

    final var normalizedQuery = query == null ? "" : query.trim();
    final var normalizedOwner = owner == null ? "" : owner.trim();

    if (normalizedOwner.isBlank() && normalizedQuery.isBlank()) {
      return this.repoRepository.findAllWithOwner(pageable).map(this::toSummary);
    }

    return this.repoRepository
        .searchWithOwner(normalizedQuery, normalizedOwner, pageable)
        .map(this::toSummary);
  }

  private AdminRepoSummary toSummary(final Repo repo) {

    final var ownerUsername = repo.getOwner().getUsername();
    return new AdminRepoSummary(
        repo.getId(),
        ownerUsername,
        repo.getName(),
        ownerUsername + "/" + repo.getName(),
        repo.isPrivate(),
        repo.isArchived(),
        repo.getDefaultBranch(),
        repo.getDescription(),
        repo.getCreatedAt(),
        repo.getUpdatedAt());
  }
}
