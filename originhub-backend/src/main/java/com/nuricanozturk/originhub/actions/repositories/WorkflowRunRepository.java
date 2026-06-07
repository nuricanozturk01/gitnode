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
package com.nuricanozturk.originhub.actions.repositories;

import com.nuricanozturk.originhub.actions.entities.WorkflowRun;
import com.nuricanozturk.originhub.actions.entities.WorkflowRunStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@NullMarked
public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, UUID> {

  Page<WorkflowRun> findAllByRepoIdOrderByCreatedAtDesc(UUID repoId, Pageable pageable);

  Optional<WorkflowRun> findFirstByRepoIdAndWorkflowDefIdOrderByCreatedAtDesc(
      UUID repoId, UUID workflowDefId);

  @Query(
      "SELECT r FROM WorkflowRun r WHERE r.repoId = :repoId AND r.workflowDefId IN :defIds"
          + " AND r.createdAt = (SELECT MAX(r2.createdAt) FROM WorkflowRun r2"
          + " WHERE r2.workflowDefId = r.workflowDefId AND r2.repoId = :repoId)")
  List<WorkflowRun> findLatestRunPerDef(
      @Param("repoId") UUID repoId, @Param("defIds") List<UUID> defIds);

  List<WorkflowRun> findByRepoIdAndConcurrencyGroupAndStatusIn(
      UUID repoId, String group, List<WorkflowRunStatus> statuses);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT COALESCE(MAX(r.runNumber), 0) + 1 FROM WorkflowRun r WHERE r.repoId = :repoId")
  int nextRunNumber(@Param("repoId") UUID repoId);
}
