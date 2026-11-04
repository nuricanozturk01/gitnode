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
package dev.gitnode.os.actions.repositories;

import dev.gitnode.os.actions.entities.WorkflowJob;
import dev.gitnode.os.actions.entities.WorkflowJobStatus;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@NullMarked
public interface WorkflowJobRepository extends JpaRepository<WorkflowJob, UUID> {

  List<WorkflowJob> findAllByRunIdOrderByCreatedAtAsc(UUID runId);

  /**
   * Queued jobs some ONLINE runner can actually take: the repository exists, the runner belongs to
   * the repository's owner and has every label the job asks for. Selecting only those keeps jobs
   * nobody can run (other owners without a runner, deleted repositories) from filling the batch and
   * starving everything behind them.
   */
  @Query(
      value =
          "SELECT j.* FROM workflow_job j WHERE j.status = 'QUEUED' AND EXISTS ("
              + " SELECT 1 FROM workflow_run wr"
              + " JOIN repositories rp ON rp.id = wr.repo_id"
              + " JOIN runner r ON r.tenant_id = rp.owner_id AND r.status = 'ONLINE'"
              + " WHERE wr.id = j.run_id AND j.runner_labels <@ r.labels)"
              + " ORDER BY j.created_at ASC LIMIT :limit FOR UPDATE OF j SKIP LOCKED",
      nativeQuery = true)
  List<WorkflowJob> findDispatchableForClaim(@Param("limit") int limit);

  /** The newest queued jobs, for explaining why they are not being picked up. */
  @Query(
      value =
          "SELECT * FROM workflow_job WHERE status = 'QUEUED' ORDER BY created_at DESC LIMIT :limit",
      nativeQuery = true)
  List<WorkflowJob> findRecentQueued(@Param("limit") int limit);

  /** Cancels queued jobs whose run or repository no longer exists; nothing can ever run them. */
  @Modifying
  @Query(
      value =
          "UPDATE workflow_job SET status = 'CANCELLED', conclusion = 'cancelled',"
              + " completed_at = now() WHERE status = 'QUEUED' AND NOT EXISTS ("
              + " SELECT 1 FROM workflow_run wr JOIN repositories rp ON rp.id = wr.repo_id"
              + " WHERE wr.id = workflow_job.run_id)",
      nativeQuery = true)
  int cancelOrphanedQueuedJobs();

  List<WorkflowJob> findAllByRunnerIdAndStatus(UUID runnerId, WorkflowJobStatus status);

  long countByRunnerIdAndStatus(UUID runnerId, WorkflowJobStatus status);
}
