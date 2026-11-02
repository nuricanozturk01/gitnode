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

package dev.gitnode.os.actions.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.gitnode.os.actions.entities.WorkflowRun;
import dev.gitnode.os.actions.entities.WorkflowRunStatus;
import dev.gitnode.os.actions.repositories.WorkflowRunRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RequiredChecksAdapter")
class RequiredChecksAdapterTest {

  private final WorkflowRunRepository runs = mock(WorkflowRunRepository.class);
  private final RequiredChecksAdapter adapter = new RequiredChecksAdapter(runs);
  private final UUID repoId = UUID.randomUUID();

  private void latest(final String workflow, final WorkflowRunStatus status) {
    final var run = new WorkflowRun();
    run.setStatus(status);
    when(runs.findFirstByRepoIdAndTriggerShaAndWorkflowNameOrderByCreatedAtDesc(
            eq(repoId), any(), eq(workflow)))
        .thenReturn(Optional.of(run));
  }

  @Test
  @DisplayName("only successful latest runs pass; missing and unfinished runs do not")
  void reportsEverythingExceptSuccess() {
    latest("ci", WorkflowRunStatus.SUCCESS);
    latest("lint", WorkflowRunStatus.FAILURE);
    latest("e2e", WorkflowRunStatus.IN_PROGRESS);
    when(runs.findFirstByRepoIdAndTriggerShaAndWorkflowNameOrderByCreatedAtDesc(
            eq(repoId), any(), eq("never-ran")))
        .thenReturn(Optional.empty());

    final var failing =
        adapter.notPassing(repoId, "a".repeat(40), List.of("ci", "lint", "e2e", "never-ran"));

    assertThat(failing).containsExactly("lint", "e2e", "never-ran");
  }

  @Test
  @DisplayName("no required workflows means nothing fails")
  void emptyRequirementPasses() {
    assertThat(adapter.notPassing(repoId, "a".repeat(40), List.of())).isEmpty();
  }
}
