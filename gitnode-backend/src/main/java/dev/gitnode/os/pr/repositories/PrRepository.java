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

import dev.gitnode.os.pr.entities.PullRequest;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@NullMarked
public interface PrRepository extends JpaRepository<PullRequest, UUID> {

  @Query(
      """
    SELECT pr.id FROM PullRequest pr
    WHERE pr.autoMergeStrategy IS NOT NULL AND pr.status = 'OPEN'
    ORDER BY pr.updatedAt
    """)
  List<UUID> findAutoMergeCandidateIds();

  @Transactional
  @Modifying
  @Query(
      "UPDATE PullRequest pr SET pr.autoMergeStrategy = NULL, pr.autoMergeById = NULL WHERE pr.id = :id")
  int clearAutoMerge(UUID id);

  @Query(
      value =
          """
    SELECT pr FROM PullRequest pr
    WHERE pr.repoId = :repoId AND pr.status = :status
    ORDER BY pr.createdAt DESC
    """,
      countQuery =
          "SELECT count(pr) FROM PullRequest pr WHERE pr.repoId = :repoId AND pr.status = :status")
  Page<PullRequest> findAllByRepoIdAndStatusOrderByCreatedAtDesc(
      UUID repoId, String status, Pageable pageable);

  @Query(
      value =
          """
    SELECT pr FROM PullRequest pr
    WHERE pr.repoId = :repoId AND pr.status = :status
      AND (:hasLabel = false OR :labelId MEMBER OF pr.labelIds)
      AND (:hasMilestone = false OR pr.milestoneId = :milestoneId)
      AND (:hasAssignee = false OR :assigneeId MEMBER OF pr.assigneeIds)
    ORDER BY pr.createdAt DESC
    """,
      countQuery =
          """
    SELECT count(pr) FROM PullRequest pr
    WHERE pr.repoId = :repoId AND pr.status = :status
      AND (:hasLabel = false OR :labelId MEMBER OF pr.labelIds)
      AND (:hasMilestone = false OR pr.milestoneId = :milestoneId)
      AND (:hasAssignee = false OR :assigneeId MEMBER OF pr.assigneeIds)
    """)
  Page<PullRequest> search(
      UUID repoId,
      String status,
      boolean hasLabel,
      UUID labelId,
      boolean hasMilestone,
      UUID milestoneId,
      boolean hasAssignee,
      UUID assigneeId,
      Pageable pageable);

  @Query(
      """
    SELECT pr FROM PullRequest pr
    WHERE pr.repoId = :repoId AND pr.number = :number
    """)
  Optional<PullRequest> findByRepoIdAndNumber(UUID repoId, int number);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT pr FROM PullRequest pr WHERE pr.repoId = :repoId AND pr.number = :number")
  Optional<PullRequest> findByRepoIdAndNumberForUpdate(UUID repoId, int number);

  @Query(
      "SELECT DISTINCT pr.targetBranch FROM PullRequest pr WHERE pr.repoId = :repoId "
          + "AND pr.sourceBranch = :sourceBranch AND pr.status = 'OPEN'")
  List<String> findOpenTargetBranches(UUID repoId, String sourceBranch);

  boolean existsByRepoIdAndSourceBranchAndTargetBranchAndStatus(
      UUID repoId, String sourceBranch, String targetBranch, String status);

  @Query("SELECT COALESCE(MAX(p.number), 0) FROM PullRequest p WHERE p.repoId = :repoId")
  int findMaxNumberByRepoId(UUID repoId);

  long countByRepoIdAndStatus(UUID repoId, String status);
}
