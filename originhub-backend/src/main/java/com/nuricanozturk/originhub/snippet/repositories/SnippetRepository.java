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
package com.nuricanozturk.originhub.snippet.repositories;

import com.nuricanozturk.originhub.snippet.entities.Snippet;
import com.nuricanozturk.originhub.snippet.entities.Visibility;
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
public interface SnippetRepository extends JpaRepository<Snippet, UUID> {

  @Query("SELECT s FROM Snippet s JOIN FETCH s.owner WHERE s.id = :id")
  Optional<Snippet> findByIdWithOwner(@Param("id") UUID id);

  Page<Snippet> findAllByVisibility(Visibility visibility, Pageable pageable);

  @Query(
      "SELECT s FROM Snippet s WHERE s.visibility = 'PUBLIC' AND ("
          + "LOWER(s.title) LIKE LOWER(CONCAT('%', :q, '%')) OR "
          + "LOWER(s.description) LIKE LOWER(CONCAT('%', :q, '%')))")
  Page<Snippet> searchPublic(@Param("q") String q, Pageable pageable);

  @Query("SELECT s FROM Snippet s WHERE s.visibility = 'PUBLIC' AND s.owner.username = :username")
  Page<Snippet> findPublicByOwnerUsername(@Param("username") String username, Pageable pageable);

  List<Snippet> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

  Page<Snippet> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);

  @Query(
      value =
          """
          SELECT DISTINCT s FROM Snippet s JOIN FETCH s.owner JOIN s.repos r WHERE
            r.id = :repoId AND s.visibility = 'PUBLIC'
        """,
      countQuery =
          "SELECT COUNT(DISTINCT s) FROM Snippet s JOIN s.repos r WHERE r.id = :repoId AND s.visibility = 'PUBLIC'")
  Page<Snippet> findPublicByRepoId(@Param("repoId") UUID repoId, Pageable pageable);

  @Query(
      value =
          "SELECT DISTINCT s FROM Snippet s JOIN FETCH s.owner JOIN s.repos r WHERE r.id = :repoId",
      countQuery = "SELECT COUNT(DISTINCT s) FROM Snippet s JOIN s.repos r WHERE r.id = :repoId")
  Page<Snippet> findAllByRepoId(@Param("repoId") UUID repoId, Pageable pageable);

  @Modifying
  @Query("UPDATE Snippet s SET s.forkCount = s.forkCount + 1 WHERE s.id = :id")
  void incrementForkCount(@Param("id") UUID id);

  @Modifying
  @Query("UPDATE Snippet s SET s.forkCount = s.forkCount - 1 WHERE s.id = :id AND s.forkCount > 0")
  void decrementForkCount(@Param("id") UUID id);

  @Modifying
  @Query("UPDATE Snippet s SET s.commentCount = s.commentCount + 1 WHERE s.id = :id")
  void incrementCommentCount(@Param("id") UUID id);

  @Modifying
  @Query(
      "UPDATE Snippet s SET s.commentCount = s.commentCount - 1 WHERE s.id = :id AND s.commentCount > 0")
  void decrementCommentCount(@Param("id") UUID id);
}
