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
package com.nuricanozturk.originhub.shared.repo.repositories;

import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@NullMarked
public interface RepoRepository extends JpaRepository<Repo, UUID> {

  Optional<Repo> findByOwnerIdAndName(UUID ownerId, String name);

  @Query("SELECT r FROM Repo r JOIN FETCH r.owner WHERE r.id = :id")
  Optional<Repo> findByIdWithOwner(@Param("id") UUID id);

  Optional<Repo> findByOwnerUsernameAndName(String repoOwner, String repoName);

  Page<Repo> findAllByOwnerUsername(String ownerUsername, Pageable pageable);

  Page<Repo> findAllByOwnerUsernameAndIsPrivateFalse(String ownerUsername, Pageable pageable);

  boolean existsByOwnerUsernameAndName(String ownerUsername, String name);

  @Modifying
  @Query("update Repo r set r.defaultBranch = :branchName where r.id = :repoId")
  void updateDefaultBranch(UUID repoId, String branchName);

  boolean existsByForkedFromIdAndOwnerId(UUID forkedFromId, UUID ownerId);

  @Modifying
  @Query("UPDATE Repo r SET r.forkCount = r.forkCount + 1 WHERE r.id = :id")
  void incrementForkCount(@Param("id") UUID id);

  @Modifying
  @Query("UPDATE Repo r SET r.forkCount = r.forkCount - 1 WHERE r.id = :id AND r.forkCount > 0")
  void decrementForkCount(@Param("id") UUID id);

  @Query(
      "SELECT r FROM Repo r WHERE r.owner.username = :ownerUsername "
          + "AND (r.isPrivate = false OR r.id IN :collaboratorRepoIds)")
  Page<Repo> findVisibleReposByOwner(
      @Param("ownerUsername") String ownerUsername,
      @Param("collaboratorRepoIds") Collection<UUID> collaboratorRepoIds,
      Pageable pageable);

  @Query("SELECT r.id, r.owner.id FROM Repo r WHERE r.id IN :ids")
  List<Object[]> findOwnerIdsByRepoIds(@Param("ids") Set<UUID> ids);

  long countByCreatedAtAfter(Instant since);

  @Query("SELECT r FROM Repo r JOIN FETCH r.owner WHERE r.createdAt >= :since")
  List<Repo> findAllByCreatedAtAfter(@Param("since") Instant since);

  @Query("SELECT r FROM Repo r JOIN FETCH r.owner")
  Page<Repo> findAllWithOwner(Pageable pageable);

  @Query(
      """
      SELECT r FROM Repo r JOIN FETCH r.owner o
      WHERE (:owner = '' OR LOWER(o.username) LIKE LOWER(CONCAT('%', :owner, '%')))
        AND (
          :query = ''
          OR LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%'))
          OR LOWER(o.username) LIKE LOWER(CONCAT('%', :query, '%'))
          OR LOWER(CONCAT(o.username, '/', r.name)) LIKE LOWER(CONCAT('%', :query, '%'))
        )
      """)
  Page<Repo> searchWithOwner(
      @Param("query") String query, @Param("owner") String owner, Pageable pageable);
}
