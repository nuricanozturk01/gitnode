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
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

  @NonNull
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
  List<Task> findAllByProjectIdOrderByPositionAsc(@NonNull UUID projectId);

  @NonNull Optional<Task> findByProjectIdAndCode(@NonNull UUID projectId, @NonNull String code);

  @NonNull Optional<Task> findByBranchRepoIdAndBranchName(
      @NonNull UUID branchRepoId, @NonNull String branchName);
}
