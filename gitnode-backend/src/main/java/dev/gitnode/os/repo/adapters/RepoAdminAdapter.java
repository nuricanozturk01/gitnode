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

import dev.gitnode.os.repo.api.RepoAdminPort;
import dev.gitnode.os.repo.entities.Repo;
import dev.gitnode.os.repo.repositories.RepoRepository;
import dev.gitnode.os.repo.services.RepoOwnerResolver;
import dev.gitnode.os.repo.services.RepoStorageService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@NullMarked
public class RepoAdminAdapter implements RepoAdminPort {

  private final RepoRepository repos;
  private final RepoStorageService storage;
  private final RepoOwnerResolver owners;

  @Override
  public Page<AdminRepoRow> list(final Pageable pageable, final String query, final String owner) {
    if (owner.isBlank() && query.isBlank()) {
      return this.rows(this.repos.findAllWithOwner(pageable));
    }
    return this.rows(this.repos.searchWithOwner(query, owner, pageable));
  }

  private Page<AdminRepoRow> rows(final Page<Repo> page) {
    final var owners =
        this.owners.owners(page.getContent().stream().map(Repo::getOwnerId).distinct().toList());
    return page.map(
        repo -> {
          final var ownerRef = owners.get(repo.getOwnerId());
          return toRow(repo, ownerRef == null ? "" : ownerRef.username());
        });
  }

  @Override
  public List<RepoStat> createdSince(final Instant since) {
    return this.repos.findRepoStatsSince(since).stream()
        .map(r -> new RepoStat(r.ownerUsername(), r.name(), r.createdAt()))
        .toList();
  }

  @Override
  public long count() {
    return this.repos.count();
  }

  @Override
  public long countCreatedAfter(final Instant since) {
    return this.repos.countByCreatedAtAfter(since);
  }

  @Override
  public long repoStorageBytes(final String owner, final String repoName) {
    return this.storage.calculateRepoStorageBytes(owner, repoName);
  }

  @Override
  public long totalStorageBytes() {
    return this.storage.calculateTotalStorageBytes();
  }

  private static AdminRepoRow toRow(final Repo repo, final String ownerUsername) {
    return new AdminRepoRow(
        repo.getId(),
        ownerUsername,
        repo.getName(),
        repo.isPrivate(),
        repo.isArchived(),
        repo.getDefaultBranch(),
        repo.getDescription(),
        repo.getCreatedAt(),
        repo.getUpdatedAt());
  }
}
