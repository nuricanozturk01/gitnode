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
package dev.gitnode.os.task.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.task.entities.Project;
import dev.gitnode.os.task.entities.Task;
import dev.gitnode.os.task.repositories.TaskRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskQueryAdapterTest {
  @Mock private TaskRepository taskRepository;
  @Mock private TenantQueryPort tenants;
  @InjectMocks private TaskQueryAdapter adapter;

  @Test
  void linkedTasksRespectProjectVisibility() {
    UUID issueId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    Project privateProject = project(ownerId, false);
    Project publicProject = project(ownerId, true);
    org.mockito.Mockito.lenient()
        .when(tenants.findAllByIds(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            java.util.Map.of(
                ownerId,
                new TenantQueryPort.TenantRef(
                    ownerId, "alice", "alice@example.org", null, null, true)));
    Task privateTask = task(privateProject, "PRIVATE-1");
    Task publicTask = task(publicProject, "PUBLIC-1");
    when(taskRepository.findByLinkedIssueId(issueId)).thenReturn(List.of(privateTask, publicTask));

    assertThat(adapter.findByLinkedIssueId(issueId, null))
        .extracting(data -> data.code())
        .containsExactly("PUBLIC-1");
    assertThat(adapter.findByLinkedIssueId(issueId, UUID.randomUUID()))
        .extracting(data -> data.code())
        .containsExactly("PUBLIC-1");
    assertThat(adapter.findByLinkedIssueId(issueId, ownerId))
        .extracting(data -> data.code())
        .containsExactly("PRIVATE-1", "PUBLIC-1");
    assertThat(adapter.findByLinkedIssueId(issueId, ownerId).getFirst().projectOwner())
        .isEqualTo("alice");
  }

  private static Project project(UUID ownerId, boolean isPublic) {
    Project project = new Project();
    project.setOwnerId(ownerId);
    project.setPublic(isPublic);
    project.setCodePrefix("CODE");
    project.setName("Project");
    return project;
  }

  private static Task task(Project project, String code) {
    Task task = new Task();
    task.setProject(project);
    task.setCode(code);
    task.setTitle("Task");
    task.setStatus("NOT_STARTED");
    return task;
  }
}
