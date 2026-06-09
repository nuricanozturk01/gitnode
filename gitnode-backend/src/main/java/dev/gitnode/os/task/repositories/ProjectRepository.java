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
package dev.gitnode.os.task.repositories;

import dev.gitnode.os.task.entities.Project;
import java.util.List;
import java.util.Optional;
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
public interface ProjectRepository extends JpaRepository<Project, UUID> {

  List<Project> findAllByOwnerUsernameOrderByCreatedAtDesc(String ownerUsername);

  List<Project> findAllByOwnerUsernameAndIsPublicTrueOrderByCreatedAtDesc(String ownerUsername);

  Page<Project> findAllByOwnerUsernameOrderByCreatedAtDesc(String ownerUsername, Pageable pageable);

  Page<Project> findAllByOwnerUsernameAndIsPublicTrueOrderByCreatedAtDesc(
      String ownerUsername, Pageable pageable);

  Optional<Project> findByOwnerUsernameAndCodePrefix(String ownerUsername, String codePrefix);

  Optional<Project> findByOwnerUsernameAndCodePrefixAndIsPublicTrue(
      String ownerUsername, String codePrefix);

  boolean existsByOwnerIdAndName(UUID ownerId, String name);

  boolean existsByOwnerIdAndCodePrefix(UUID ownerId, String codePrefix);

  @Modifying
  @Query("UPDATE Project p SET p.taskSeq = p.taskSeq + 1 WHERE p.id = :projectId")
  void incrementTaskSeq(UUID projectId);

  @Query(
      "SELECT DISTINCT p FROM Project p JOIN p.repos r WHERE r.id = :repoId ORDER BY p.createdAt DESC")
  List<Project> findAllByRepoId(UUID repoId);

  @Query(
      value =
          "SELECT DISTINCT p FROM Project p JOIN p.repos r WHERE r.id = :repoId ORDER BY p.createdAt DESC",
      countQuery = "SELECT COUNT(DISTINCT p) FROM Project p JOIN p.repos r WHERE r.id = :repoId")
  Page<Project> findAllByRepoId(@Param("repoId") UUID repoId, Pageable pageable);

  @Query(
      value =
          "SELECT DISTINCT p FROM Project p JOIN p.repos r "
              + "WHERE r.id = :repoId AND p.isPublic = true ORDER BY p.createdAt DESC",
      countQuery =
          "SELECT COUNT(DISTINCT p) FROM Project p JOIN p.repos r "
              + "WHERE r.id = :repoId AND p.isPublic = true")
  Page<Project> findPublicByRepoId(@Param("repoId") UUID repoId, Pageable pageable);

  @Query(
      value =
          "SELECT DISTINCT p FROM Project p JOIN p.repos r "
              + "WHERE r.id = :repoId "
              + "AND (p.isPublic = true OR p.owner.username = :viewerUsername) "
              + "ORDER BY p.createdAt DESC",
      countQuery =
          "SELECT COUNT(DISTINCT p) FROM Project p JOIN p.repos r "
              + "WHERE r.id = :repoId "
              + "AND (p.isPublic = true OR p.owner.username = :viewerUsername)")
  Page<Project> findVisibleByRepoId(
      @Param("repoId") UUID repoId,
      @Param("viewerUsername") String viewerUsername,
      Pageable pageable);
}
