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
package dev.gitnode.os.repo.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Read-only repository figures for the platform administration. */
@NullMarked
public interface RepoAdminPort {

  /** A repository as listed in the admin panel. */
  record AdminRepoRow(
      UUID id,
      String ownerUsername,
      String name,
      boolean isPrivate,
      boolean isArchived,
      String defaultBranch,
      @Nullable String description,
      Instant createdAt,
      Instant updatedAt) {}

  /** A repository created in a statistics window. */
  record RepoStat(String ownerUsername, String name, Instant createdAt) {}

  /** Lists repositories; blank filters match everything. */
  Page<AdminRepoRow> list(Pageable pageable, String query, String owner);

  List<RepoStat> createdSince(Instant since);

  long count();

  long countCreatedAfter(Instant since);

  long repoStorageBytes(String owner, String repoName);

  long totalStorageBytes();
}
