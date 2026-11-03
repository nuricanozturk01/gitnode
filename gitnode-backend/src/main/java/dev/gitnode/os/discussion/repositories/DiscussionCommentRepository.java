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

import dev.gitnode.os.discussion.entities.DiscussionComment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

@NullMarked
public interface DiscussionCommentRepository extends JpaRepository<DiscussionComment, UUID> {

  @Query(
      """
      SELECT c FROM DiscussionComment c
      WHERE c.discussionId = :discussionId AND c.parentId IS NULL
      ORDER BY c.createdAt
      """)
  Page<DiscussionComment> findTopLevel(UUID discussionId, Pageable pageable);

  @Query("SELECT c FROM DiscussionComment c WHERE c.parentId = :parentId ORDER BY c.createdAt")
  Page<DiscussionComment> findReplies(UUID parentId, Pageable pageable);

  Optional<DiscussionComment> findByIdAndDiscussionId(UUID id, UUID discussionId);

  @Query(
      """
      SELECT c.discussionId AS id, count(c) AS count FROM DiscussionComment c
      WHERE c.discussionId IN :ids GROUP BY c.discussionId
      """)
  List<IdCount> countByDiscussionIds(Collection<UUID> ids);

  @Query(
      """
      SELECT c.parentId AS id, count(c) AS count FROM DiscussionComment c
      WHERE c.parentId IN :ids GROUP BY c.parentId
      """)
  List<IdCount> countRepliesByParentIds(Collection<UUID> ids);
}
