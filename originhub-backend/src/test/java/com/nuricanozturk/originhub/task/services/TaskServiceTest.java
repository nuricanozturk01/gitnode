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
package com.nuricanozturk.originhub.task.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.issue.api.IssueQueryService;
import com.nuricanozturk.originhub.pr.api.PrQueryPort;
import com.nuricanozturk.originhub.shared.branch.services.BranchProtocolService;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.task.events.TaskCreatedEvent;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import com.nuricanozturk.originhub.task.dtos.TaskDetail;
import com.nuricanozturk.originhub.task.dtos.TaskForm;
import com.nuricanozturk.originhub.task.entities.BoardColumn;
import com.nuricanozturk.originhub.task.entities.Project;
import com.nuricanozturk.originhub.task.entities.Task;
import com.nuricanozturk.originhub.task.mappers.TaskMapper;
import com.nuricanozturk.originhub.task.repositories.BoardColumnRepository;
import com.nuricanozturk.originhub.task.repositories.ProjectRepository;
import com.nuricanozturk.originhub.task.repositories.SubtaskRepository;
import com.nuricanozturk.originhub.task.repositories.TaskRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaskService unit tests")
class TaskServiceTest {

  @Mock private TaskRepository taskRepository;
  @Mock private SubtaskRepository subtaskRepository;
  @Mock private BoardColumnRepository boardColumnRepository;
  @Mock private ProjectRepository projectRepository;
  @Mock private RepoRepository repoRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private PrQueryPort prQueryPort;
  @Mock private IssueQueryService issueQueryService;
  @Mock private ProjectService projectService;
  @Mock private TaskMapper taskMapper;
  @Mock private BranchProtocolService branchProtocolService;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private TaskService taskService;

  @Test
  @DisplayName("create throws ItemNotFoundException when board column missing")
  void create_throws_whenColumnMissing() {
    Project project = project();
    UUID columnId = UUID.randomUUID();
    when(projectService.findProject("alice", "APP")).thenReturn(project);
    when(boardColumnRepository.findById(columnId)).thenReturn(Optional.empty());
    TaskForm form = taskForm(columnId, "TASK", null);

    assertThatThrownBy(() -> taskService.create("alice", "APP", form))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("Board column not found");
  }

  @Test
  @DisplayName("create assigns task code from project sequence and publishes event")
  void create_savesTaskWithCode_whenValid() {
    Project project = project();
    project.setCodePrefix("APP");
    project.setTaskSeq(5);
    UUID columnId = UUID.randomUUID();
    BoardColumn column = new BoardColumn();
    column.setId(columnId);
    Project updated = project();
    updated.setTaskSeq(6);
    when(projectService.findProject("alice", "APP")).thenReturn(project);
    when(boardColumnRepository.findById(columnId)).thenReturn(Optional.of(column));
    when(projectRepository.findById(project.getId())).thenReturn(Optional.of(updated));
    Task saved = new Task();
    saved.setId(UUID.randomUUID());
    saved.setCode("APP-6");
    when(taskRepository.save(any(Task.class))).thenReturn(saved);
    TaskDetail detail = TaskDetail.builder().code("APP-6").title("Implement").build();
    when(taskMapper.toDetail(any(), any(), any(), any())).thenReturn(detail);
    TaskForm form = taskForm(columnId, "TASK", null);
    form.setTitle("Implement");

    TaskDetail result = taskService.create("alice", "APP", form);

    assertThat(result.code()).isEqualTo("APP-6");
    verify(eventPublisher).publishEvent(any(TaskCreatedEvent.class));
  }

  @Test
  @DisplayName("create throws ErrorOccurredException for invalid task type")
  void create_throws_whenInvalidType() {
    Project project = project();
    UUID columnId = UUID.randomUUID();
    BoardColumn column = new BoardColumn();
    column.setId(columnId);
    Project updated = project();
    updated.setTaskSeq(1);
    when(projectService.findProject("alice", "APP")).thenReturn(project);
    when(boardColumnRepository.findById(columnId)).thenReturn(Optional.of(column));
    when(projectRepository.findById(project.getId())).thenReturn(Optional.of(updated));
    TaskForm form = taskForm(columnId, "INVALID", null);

    assertThatThrownBy(() -> taskService.create("alice", "APP", form))
        .isInstanceOf(ErrorOccurredException.class)
        .hasMessageContaining("Invalid type");
  }

  @Test
  @DisplayName("create throws ItemNotFoundException when assignee missing")
  void create_throws_whenAssigneeMissing() {
    Project project = project();
    UUID columnId = UUID.randomUUID();
    UUID assigneeId = UUID.randomUUID();
    BoardColumn column = new BoardColumn();
    column.setId(columnId);
    Project updated = project();
    updated.setTaskSeq(1);
    when(projectService.findProject("alice", "APP")).thenReturn(project);
    when(boardColumnRepository.findById(columnId)).thenReturn(Optional.of(column));
    when(projectRepository.findById(project.getId())).thenReturn(Optional.of(updated));
    when(tenantRepository.findById(assigneeId)).thenReturn(Optional.empty());
    TaskForm form = taskForm(columnId, "TASK", assigneeId);

    assertThatThrownBy(() -> taskService.create("alice", "APP", form))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("Assignee not found");
  }

  private static TaskForm taskForm(UUID columnId, String type, UUID assigneeId) {
    TaskForm form = new TaskForm();
    form.setTitle("Task");
    form.setBoardColumnId(columnId);
    form.setType(type);
    form.setPosition(0);
    form.setAssigneeId(assigneeId);
    return form;
  }

  private static Project project() {
    Project p = new Project();
    p.setId(UUID.randomUUID());
    Tenant owner = new Tenant();
    owner.setUsername("alice");
    p.setOwner(owner);
    p.setCodePrefix("APP");
    p.setTaskSeq(0);
    return p;
  }
}
