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

import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Repository identity lookups for other modules; an immutable view, never the entity. */
@NullMarked
public interface RepoLookupPort {

  record RepoRef(
      UUID id,
      UUID ownerId,
      String ownerUsername,
      String name,
      boolean isPrivate,
      @Nullable String defaultBranch,
      boolean aiPrReviewEnabled,
      @Nullable String description) {

    public RepoRef(
        final UUID id,
        final UUID ownerId,
        final String ownerUsername,
        final String name,
        final boolean isPrivate,
        final @Nullable String defaultBranch,
        final boolean aiPrReviewEnabled) {
      this(id, ownerId, ownerUsername, name, isPrivate, defaultBranch, aiPrReviewEnabled, null);
    }

    public RepoRef(
        final UUID id,
        final UUID ownerId,
        final String ownerUsername,
        final String name,
        final boolean isPrivate,
        final @Nullable String defaultBranch) {
      this(id, ownerId, ownerUsername, name, isPrivate, defaultBranch, false, null);
    }

    public RepoRef(
        final UUID id,
        final UUID ownerId,
        final String ownerUsername,
        final String name,
        final boolean isPrivate) {
      this(id, ownerId, ownerUsername, name, isPrivate, null, false, null);
    }
  }

  Optional<RepoRef> find(String owner, String repoName);

  Optional<RepoRef> findById(UUID repoId);

  /** What to do with the source branch of a pull request once it is merged or closed. */
  record HeadBranchPolicy(boolean deleteOnMerge, boolean deleteOnClose) {}

  Optional<HeadBranchPolicy> headBranchPolicy(UUID repoId);

  /** Repositories that exist among the given ids, keyed by id. */
  java.util.Map<UUID, RepoRef> findAllByIds(java.util.Collection<UUID> repoIds);

  /** Owner account id per repository id; unknown repositories are simply absent. */
  java.util.Map<UUID, UUID> findOwnerIds(java.util.Collection<UUID> repoIds);
}
