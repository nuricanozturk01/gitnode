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
package com.nuricanozturk.originhub.task;

import static org.mockito.Mockito.verify;

import com.nuricanozturk.originhub.pr.api.PrQueryPort;
import com.nuricanozturk.originhub.shared.branch.services.BranchProtocolService;
import com.nuricanozturk.originhub.shared.collaborator.services.CollaboratorAccessPort;
import com.nuricanozturk.originhub.shared.pr.events.PullRequestCreatedEvent;
import com.nuricanozturk.originhub.shared.pr.events.PullRequestStatusChangedEvent;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import com.nuricanozturk.originhub.task.repositories.BoardColumnRepository;
import com.nuricanozturk.originhub.task.repositories.BoardRepository;
import com.nuricanozturk.originhub.task.repositories.ProjectRepository;
import com.nuricanozturk.originhub.task.repositories.SubtaskRepository;
import com.nuricanozturk.originhub.task.repositories.TaskRepository;
import com.nuricanozturk.originhub.task.services.TaskService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ApplicationModuleTest(mode = BootstrapMode.STANDALONE, extraIncludes = "shared")
@TestPropertySource(
    properties = {
      "originhub.jwt.secret=test-jwt-secret-key-for-module-tests-only",
      "spring.cache.type=none",
      "OAUTH2_GOOGLE_CLIENT_ID=test",
      "OAUTH2_GOOGLE_CLIENT_SECRET=test",
      "OAUTH2_GITHUB_CLIENT_ID=test",
      "OAUTH2_GITHUB_CLIENT_SECRET=test",
      "OAUTH2_GITLAB_CLIENT_ID=test",
      "OAUTH2_GITLAB_CLIENT_SECRET=test"
    })
@DisplayName("task module — PR event listeners (module test)")
class PrStatusTaskListenerModuleTest {

  @MockitoBean TaskRepository taskRepository;
  @MockitoBean TaskService taskService;
  @MockitoBean SubtaskRepository subtaskRepository;
  @MockitoBean BoardColumnRepository boardColumnRepository;
  @MockitoBean ProjectRepository projectRepository;
  @MockitoBean BoardRepository boardRepository;
  @MockitoBean RepoRepository repoRepository;
  @MockitoBean TenantRepository tenantRepository;

  @MockitoBean(name = "prQueryPort")
  PrQueryPort prQueryPort;

  @MockitoBean CollaboratorAccessPort collaboratorAccessPort;
  @MockitoBean BranchProtocolService branchProtocolService;

  @Test
  @DisplayName("PullRequestCreatedEvent → TaskService.linkPullRequest")
  void prCreated_linksTask(Scenario scenario) {
    final var prId = UUID.randomUUID();
    final var repoId = UUID.randomUUID();

    scenario
        .publish(new PullRequestCreatedEvent(prId, repoId, "feature/abc"))
        .andWaitForStateChange(
            () -> {
              verify(this.taskService).linkPullRequest(repoId, "feature/abc", prId);
              return true;
            },
            result -> result);
  }

  @Test
  @DisplayName("PullRequestStatusChangedEvent → TaskService.updateTaskStatusForPr")
  void prStatusChanged_updatesTaskStatus(Scenario scenario) {
    final var prId = UUID.randomUUID();
    final var repoId = UUID.randomUUID();

    scenario
        .publish(new PullRequestStatusChangedEvent(prId, repoId, "feature/abc", "main", "MERGED"))
        .andWaitForStateChange(
            () -> {
              verify(this.taskService).updateTaskStatusForPr(repoId, "feature/abc", "MERGED");
              return true;
            },
            result -> result);
  }
}
