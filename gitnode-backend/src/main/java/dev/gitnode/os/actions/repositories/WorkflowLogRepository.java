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

import dev.gitnode.os.actions.entities.WorkflowLog;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@NullMarked
public interface WorkflowLogRepository extends JpaRepository<WorkflowLog, Long> {

  List<WorkflowLog> findAllByStepIdOrderByLineNumberAsc(UUID stepId);

  /**
   * ::error / ::warning / ::notice lines of a run, in log order. The runner writes them with a
   * {@code ##[error]} style prefix, which is also how the UI recognises them.
   */
  @Query(
      "SELECT new dev.gitnode.os.actions.repositories.RunLogRow("
          + " l.content, l.level, j.id, j.name, s.id, s.stepNumber, l.lineNumber)"
          + " FROM WorkflowLog l, WorkflowStep s, WorkflowJob j"
          + " WHERE l.stepId = s.id AND s.jobId = j.id AND j.runId = :runId"
          + " AND (l.content LIKE '##[error]%' OR l.content LIKE '##[warning]%'"
          + " OR l.content LIKE '##[notice]%')"
          + " ORDER BY j.createdAt, s.stepNumber, l.lineNumber")
  List<RunLogRow> findAnnotations(@Param("runId") UUID runId, Pageable pageable);

  /** The $GITHUB_STEP_SUMMARY lines of a run, in log order. */
  @Query(
      "SELECT new dev.gitnode.os.actions.repositories.RunLogRow("
          + " l.content, l.level, j.id, j.name, s.id, s.stepNumber, l.lineNumber)"
          + " FROM WorkflowLog l, WorkflowStep s, WorkflowJob j"
          + " WHERE l.stepId = s.id AND s.jobId = j.id AND j.runId = :runId"
          + " AND l.level = 'summary'"
          + " ORDER BY j.createdAt, s.stepNumber, l.lineNumber")
  List<RunLogRow> findSummaryLines(@Param("runId") UUID runId);
}
