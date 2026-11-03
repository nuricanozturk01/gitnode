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
package dev.gitnode.os.discussion.repositories;

import dev.gitnode.os.discussion.entities.Discussion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

@NullMarked
public interface DiscussionRepository extends JpaRepository<Discussion, UUID> {

  @Query(
      """
      SELECT d FROM Discussion d
      WHERE d.repoId = :repoId
        AND (:hasCategory = false OR d.category = :category)
        AND (:unansweredOnly = false OR (d.category = 'Q_AND_A' AND d.answerCommentId IS NULL))
      """)
  Page<Discussion> search(
      UUID repoId, boolean hasCategory, String category, boolean unansweredOnly, Pageable pageable);

  @Query("SELECT d FROM Discussion d WHERE d.repoId = :repoId AND d.number = :number")
  Optional<Discussion> findByRepoIdAndNumber(UUID repoId, int number);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT d FROM Discussion d WHERE d.repoId = :repoId AND d.number = :number")
  Optional<Discussion> findByRepoIdAndNumberForUpdate(UUID repoId, int number);

  @Query("SELECT COALESCE(MAX(d.number), 0) FROM Discussion d WHERE d.repoId = :repoId")
  int findMaxNumberByRepoId(UUID repoId);
}
