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
package com.nuricanozturk.originhub.task.repositories;

import com.nuricanozturk.originhub.task.entities.Task;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
@NullMarked
public interface TaskRepository extends JpaRepository<Task, UUID> {

  @Query(
      """
      SELECT t FROM Task t
      JOIN FETCH t.boardColumn
      LEFT JOIN FETCH t.assignee
      LEFT JOIN FETCH t.branchRepo
      LEFT JOIN FETCH t.linkedPr
      WHERE t.project.id = :projectId
      ORDER BY t.position ASC
      """)
  List<Task> findAllByProjectIdOrderByPositionAsc(UUID projectId);

  @Query(
"""
      SELECT t FROM Task t
      JOIN FETCH t.boardColumn
      LEFT JOIN FETCH t.assignee
      LEFT JOIN FETCH t.branchRepo
      LEFT JOIN FETCH t.linkedPr
      WHERE t.project.id = :projectId AND t.code = :code
""")
  Optional<Task> findByProjectIdAndCode(UUID projectId, String code);

  Optional<Task> findByBranchRepoIdAndBranchName(UUID branchRepoId, String branchName);

  @Query(
"""
      SELECT t FROM Task t
      JOIN FETCH t.project
      WHERE t.linkedIssueId = :issueId
""")
  List<Task> findByLinkedIssueId(UUID issueId);

  long countByProjectId(UUID projectId);

  @Modifying
  @Query("UPDATE Task t SET t.subtaskSeq = t.subtaskSeq + 1 WHERE t.id = :taskId")
  void incrementSubtaskSeq(UUID taskId);

  @Modifying
  @Query("UPDATE Task t SET t.linkedIssueId = null WHERE t.linkedIssueId = :issueId")
  void clearLinkedIssueId(UUID issueId);
}
