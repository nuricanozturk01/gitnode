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
package dev.gitnode.os.repo.repositories;

import dev.gitnode.os.repo.entities.Repo;
import jakarta.persistence.LockModeType;
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
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@NullMarked
public interface RepoRepository extends JpaRepository<Repo, UUID> {

  Optional<Repo> findByOwnerIdAndName(UUID ownerId, String name);

  @Query(
      "SELECT r FROM Repo r JOIN Tenant o ON o.id = r.ownerId "
          + "WHERE o.username = :repoOwner AND r.name = :repoName")
  Optional<Repo> findByOwnerUsernameAndName(
      @Param("repoOwner") String repoOwner, @Param("repoName") String repoName);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM Repo r WHERE r.id = :id")
  Optional<Repo> findByIdForUpdate(UUID id);

  @Query(
      value =
          "SELECT r FROM Repo r JOIN Tenant o ON o.id = r.ownerId "
              + "WHERE o.username = :ownerUsername",
      countQuery =
          "SELECT COUNT(r) FROM Repo r JOIN Tenant o ON o.id = r.ownerId "
              + "WHERE o.username = :ownerUsername")
  Page<Repo> findAllByOwnerUsername(
      @Param("ownerUsername") String ownerUsername, Pageable pageable);

  @Query(
      value =
          "SELECT r FROM Repo r JOIN Tenant o ON o.id = r.ownerId "
              + "WHERE o.username = :ownerUsername AND r.isPrivate = false",
      countQuery =
          "SELECT COUNT(r) FROM Repo r JOIN Tenant o ON o.id = r.ownerId "
              + "WHERE o.username = :ownerUsername AND r.isPrivate = false")
  Page<Repo> findAllByOwnerUsernameAndIsPrivateFalse(
      @Param("ownerUsername") String ownerUsername, Pageable pageable);

  @Query(
      "SELECT COUNT(r) > 0 FROM Repo r JOIN Tenant o ON o.id = r.ownerId "
          + "WHERE o.username = :ownerUsername AND r.name = :name")
  boolean existsByOwnerUsernameAndName(
      @Param("ownerUsername") String ownerUsername, @Param("name") String name);

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
      value =
          "SELECT r FROM Repo r JOIN Tenant o ON o.id = r.ownerId "
              + "WHERE o.username = :ownerUsername "
              + "AND (r.isPrivate = false OR r.id IN :collaboratorRepoIds)",
      countQuery =
          "SELECT COUNT(r) FROM Repo r JOIN Tenant o ON o.id = r.ownerId "
              + "WHERE o.username = :ownerUsername "
              + "AND (r.isPrivate = false OR r.id IN :collaboratorRepoIds)")
  Page<Repo> findVisibleReposByOwner(
      @Param("ownerUsername") String ownerUsername,
      @Param("collaboratorRepoIds") Collection<UUID> collaboratorRepoIds,
      Pageable pageable);

  @Query("SELECT r.id, r.ownerId FROM Repo r WHERE r.id IN :ids")
  List<Object[]> findOwnerIdsByRepoIds(@Param("ids") Set<UUID> ids);

  long countByCreatedAtAfter(Instant since);

  @Query("SELECT r FROM Repo r WHERE r.createdAt >= :since")
  List<Repo> findAllByCreatedAtAfter(@Param("since") Instant since);

  @Query(
      "SELECT new dev.gitnode.os.repo.repositories.RepoStatRow("
          + "o.username, r.name, r.createdAt) "
          + "FROM Repo r JOIN Tenant o ON o.id = r.ownerId WHERE r.createdAt >= :since")
  List<RepoStatRow> findRepoStatsSince(@Param("since") Instant since);

  @Query("SELECT r FROM Repo r")
  Page<Repo> findAllWithOwner(Pageable pageable);

  @Query(
      value =
          """
      SELECT r FROM Repo r JOIN Tenant o ON o.id = r.ownerId
      WHERE (:owner = '' OR LOWER(o.username) LIKE LOWER(CONCAT('%', :owner, '%')))
        AND (
          :query = ''
          OR LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%'))
          OR LOWER(o.username) LIKE LOWER(CONCAT('%', :query, '%'))
          OR LOWER(CONCAT(o.username, '/', r.name)) LIKE LOWER(CONCAT('%', :query, '%'))
        )
      """,
      countQuery =
          """
      SELECT COUNT(r) FROM Repo r JOIN Tenant o ON o.id = r.ownerId
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
