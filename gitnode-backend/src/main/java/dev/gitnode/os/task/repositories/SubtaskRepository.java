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
package dev.gitnode.os.task.repositories;

import dev.gitnode.os.task.entities.Subtask;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
@NullMarked
public interface SubtaskRepository extends JpaRepository<Subtask, UUID> {

  @Query(
"""
      SELECT s FROM Subtask s
      LEFT JOIN FETCH s.branchRepo
      WHERE s.task.id = :taskId
      ORDER BY s.position ASC
""")
  List<Subtask> findAllByTaskIdOrderByPositionAsc(UUID taskId);

  Optional<Subtask> findByIdAndTaskId(UUID id, UUID taskId);

  int countByTaskId(UUID taskId);

  int countByTaskIdAndStatus(UUID taskId, String status);

  Optional<Subtask> findByBranchRepoIdAndBranchName(UUID branchRepoId, String branchName);
}
