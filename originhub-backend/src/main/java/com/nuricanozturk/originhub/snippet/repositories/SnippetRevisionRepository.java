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

import com.nuricanozturk.originhub.snippet.entities.SnippetRevision;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@NullMarked
public interface SnippetRevisionRepository extends JpaRepository<SnippetRevision, UUID> {

  @Query(
      value =
          """
         SELECT r FROM SnippetRevision r JOIN FETCH r.author
           WHERE r.snippet.id = :snippetId ORDER BY r.createdAt DESC
        """,
      countQuery = "SELECT COUNT(r) FROM SnippetRevision r WHERE r.snippet.id = :snippetId")
  Page<SnippetRevision> findAllBySnippetIdOrderByCreatedAtDesc(
      @Param("snippetId") UUID snippetId, Pageable pageable);

  @Query("SELECT r FROM SnippetRevision r JOIN FETCH r.author WHERE r.id = :id")
  Optional<SnippetRevision> findByIdWithAuthor(@Param("id") UUID id);
}
