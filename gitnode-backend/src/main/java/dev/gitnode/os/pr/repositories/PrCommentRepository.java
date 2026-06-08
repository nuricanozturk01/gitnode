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
package dev.gitnode.os.pr.repositories;

import dev.gitnode.os.pr.entities.PullRequestComment;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
@NullMarked
public interface PrCommentRepository extends JpaRepository<PullRequestComment, UUID> {

  @Query(
      value =
          """
          select prc from PullRequestComment prc join fetch prc.author
            where prc.pr.id = :prId order by prc.createdAt asc
        """,
      countQuery = "select count(prc) from PullRequestComment prc where prc.pr.id = :prId")
  Page<PullRequestComment> findAllByPrIdOrderByCreatedAtAsc(UUID prId, Pageable pageable);

  long countByPrId(UUID prId);
}
