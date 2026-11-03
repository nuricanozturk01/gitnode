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

import dev.gitnode.os.discussion.entities.DiscussionVote;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

@NullMarked
public interface DiscussionVoteRepository
    extends JpaRepository<DiscussionVote, DiscussionVote.Key> {

  boolean existsByDiscussionIdAndTenantId(UUID discussionId, UUID tenantId);

  long countByDiscussionId(UUID discussionId);

  @Transactional
  @Modifying
  void deleteByDiscussionIdAndTenantId(UUID discussionId, UUID tenantId);

  @Query(
      """
      SELECT v.discussionId AS id, count(v) AS count FROM DiscussionVote v
      WHERE v.discussionId IN :ids GROUP BY v.discussionId
      """)
  List<IdCount> countByDiscussionIds(Collection<UUID> ids);
}
