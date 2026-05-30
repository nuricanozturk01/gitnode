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
package com.nuricanozturk.originhub.pr.repositories;

import com.nuricanozturk.originhub.pr.entities.PullRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PrRepository extends JpaRepository<PullRequest, UUID> {

  @Query(
      """
    SELECT pr FROM PullRequest pr
    JOIN FETCH pr.author
    LEFT JOIN FETCH pr.mergedBy
    WHERE pr.repo.id = :repoId AND pr.status = :status
    ORDER BY pr.createdAt DESC
    """)
  @NonNull List<PullRequest> findAllByRepoIdAndStatusOrderByCreatedAtDesc(
      @NonNull UUID repoId, @NonNull String status);

  @Query(
      """
    SELECT pr FROM PullRequest pr
    JOIN FETCH pr.author
    LEFT JOIN FETCH pr.mergedBy
    WHERE pr.repo.id = :repoId AND pr.number = :number
    """)
  Optional<PullRequest> findByRepoIdAndNumber(@NonNull UUID repoId, int number);

  boolean existsByRepoIdAndSourceBranchAndTargetBranchAndStatus(
      @NonNull UUID repoId,
      @NonNull String sourceBranch,
      @NonNull String targetBranch,
      @NonNull String status);

  @Query("SELECT COALESCE(MAX(p.number), 0) FROM PullRequest p WHERE p.repo.id = :repoId")
  int findMaxNumberByRepoId(@NonNull UUID repoId);
}
