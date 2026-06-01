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

import com.nuricanozturk.originhub.issue.api.IssueQueryService;
import com.nuricanozturk.originhub.pr.api.PrQueryPort;
import com.nuricanozturk.originhub.shared.branch.dtos.BranchForm;
import com.nuricanozturk.originhub.shared.branch.dtos.BranchInfo;
import com.nuricanozturk.originhub.shared.branch.services.BranchProtocolService;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.task.events.TaskCreatedEvent;
import com.nuricanozturk.originhub.shared.task.events.TaskDeletedEvent;
import com.nuricanozturk.originhub.shared.task.events.TaskUpdatedEvent;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import com.nuricanozturk.originhub.task.dtos.CreateBranchFromTaskForm;
import com.nuricanozturk.originhub.task.dtos.LinkedIssueInfo;
import com.nuricanozturk.originhub.task.dtos.LinkedPrInfo;
import com.nuricanozturk.originhub.task.dtos.SubtaskForm;
import com.nuricanozturk.originhub.task.dtos.SubtaskInfo;
import com.nuricanozturk.originhub.task.dtos.SubtaskUpdateForm;
import com.nuricanozturk.originhub.task.dtos.TaskDetail;
import com.nuricanozturk.originhub.task.dtos.TaskForm;
import com.nuricanozturk.originhub.task.dtos.TaskInfo;
import com.nuricanozturk.originhub.task.dtos.TaskUpdateForm;
import com.nuricanozturk.originhub.task.entities.Subtask;
import com.nuricanozturk.originhub.task.entities.Task;
import com.nuricanozturk.originhub.task.entities.TaskStatus;
import com.nuricanozturk.originhub.task.entities.TaskType;
import com.nuricanozturk.originhub.task.mappers.TaskMapper;
import com.nuricanozturk.originhub.task.repositories.BoardColumnRepository;
import com.nuricanozturk.originhub.task.repositories.ProjectRepository;
import com.nuricanozturk.originhub.task.repositories.SubtaskRepository;
import com.nuricanozturk.originhub.task.repositories.TaskRepository;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
@NullMarked
public class TaskService {

  private final TaskRepository taskRepository;
  private final SubtaskRepository subtaskRepository;
  private final BoardColumnRepository boardColumnRepository;
  private final ProjectRepository projectRepository;
  private final RepoRepository repoRepository;
  private final TenantRepository tenantRepository;
  private final PrQueryPort prQueryPort;
  private final IssueQueryService issueQueryService;
  private final ProjectService projectService;
  private final TaskMapper taskMapper;
  private final BranchProtocolService branchProtocolService;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public TaskDetail create(
      final String ownerUsername, final String projectCode, final TaskForm form) {

    final var project = this.projectService.findProject(ownerUsername, projectCode);

    final var column =
        this.boardColumnRepository
            .findById(form.getBoardColumnId())
            .orElseThrow(() -> new ItemNotFoundException("Board column not found"));

    this.projectRepository.incrementTaskSeq(project.getId());

    final var updatedProject =
        this.projectRepository
            .findById(project.getId())
            .orElseThrow(() -> new ItemNotFoundException("Project not found"));

    final var taskCode = updatedProject.getCodePrefix() + "-" + updatedProject.getTaskSeq();

    final var task = new Task();
    task.setProject(updatedProject);
    task.setBoardColumn(column);
    task.setCode(taskCode);
    task.setTitle(form.getTitle());
    task.setDescription(form.getDescription());
    task.setType(this.validateType(form.getType()));
    task.setStatus(TaskStatus.NOT_STARTED.name());
    task.setPosition(form.getPosition());

    if (form.getAssigneeId() != null) {
      final var assignee =
          this.tenantRepository
              .findById(form.getAssigneeId())
              .orElseThrow(() -> new ItemNotFoundException("Assignee not found"));
      task.setAssignee(assignee);
    }

    if (form.getLinkedIssueId() != null) {
      @SuppressWarnings("unused")
      final var ignored =
          this.issueQueryService
              .findById(form.getLinkedIssueId())
              .orElseThrow(() -> new ItemNotFoundException("Issue not found"));
      task.setLinkedIssueId(form.getLinkedIssueId());
    }

    final var saved = this.taskRepository.save(task);
    this.eventPublisher.publishEvent(
        new TaskCreatedEvent(saved.getId(), project.getId(), saved.getCode(), ownerUsername));
    return this.toDetail(saved);
  }

  public Page<TaskInfo> getAll(
      final String ownerUsername,
      final String projectCode,
      final @Nullable Tenant viewer,
      final int page,
      final int size) {

    final var project = this.projectService.findProjectAsViewer(ownerUsername, projectCode, viewer);
    final var pageable = PageRequest.of(page, size);

    return this.taskRepository
        .findAllByProjectIdOrderByPositionAsc(project.getId(), pageable)
        .map(
            task -> {
              final int total = this.subtaskRepository.countByTaskId(task.getId());
              final int completed =
                  this.subtaskRepository.countByTaskIdAndStatus(task.getId(), "COMPLETED");
              return this.taskMapper.toInfo(task, total, completed);
            });
  }

  public TaskDetail get(
      final String ownerUsername,
      final String projectCode,
      final String taskCode,
      final @Nullable Tenant viewer) {

    final var project = this.projectService.findProjectAsViewer(ownerUsername, projectCode, viewer);
    return this.toDetail(this.findTask(project.getId(), taskCode));
  }

  @Transactional
  public TaskDetail update(
      final String ownerUsername,
      final String projectCode,
      final String taskCode,
      final TaskUpdateForm form) {

    final var project = this.projectService.findProject(ownerUsername, projectCode);
    final var task = this.findTask(project.getId(), taskCode);

    this.applyTaskUpdates(task, form);

    final var saved = this.taskRepository.save(task);
    this.eventPublisher.publishEvent(
        new TaskUpdatedEvent(saved.getId(), project.getId(), saved.getCode(), ownerUsername));
    return this.toDetail(saved);
  }

  private void applyTaskUpdates(final Task task, final TaskUpdateForm form) {
    this.applyScalarUpdates(task, form);
    this.applyRelationUpdates(task, form);
  }

  private void applyScalarUpdates(final Task task, final TaskUpdateForm form) {
    if (form.getTitle() != null) {
      task.setTitle(form.getTitle());
    }
    if (form.getDescription() != null) {
      final var desc = form.getDescription();
      task.setDescription(desc.isBlank() ? null : desc);
    }
    if (form.getStatus() != null) {
      task.setStatus(this.validateStatus(form.getStatus()));
    }
    if (form.getType() != null) {
      task.setType(this.validateType(form.getType()));
    }
    if (form.getPosition() != null) {
      task.setPosition(form.getPosition());
    }
  }

  private void applyRelationUpdates(final Task task, final TaskUpdateForm form) {
    if (form.getBoardColumnId() != null) {
      final var column =
          this.boardColumnRepository
              .findById(form.getBoardColumnId())
              .orElseThrow(() -> new ItemNotFoundException("Board column not found"));
      task.setBoardColumn(column);
    }
    if (form.getAssigneeId() != null) {
      final var assignee =
          this.tenantRepository
              .findById(form.getAssigneeId())
              .orElseThrow(() -> new ItemNotFoundException("Assignee not found"));
      task.setAssignee(assignee);
    }
    if (form.isUnlinkIssue()) {
      task.setLinkedIssueId(null);
    } else if (form.getLinkedIssueId() != null) {
      @SuppressWarnings("unused")
      final var ignored =
          this.issueQueryService
              .findById(form.getLinkedIssueId())
              .orElseThrow(() -> new ItemNotFoundException("Issue not found"));
      task.setLinkedIssueId(form.getLinkedIssueId());
    }
  }

  @Transactional
  public void delete(final String ownerUsername, final String projectCode, final String taskCode) {

    final var project = this.projectService.findProject(ownerUsername, projectCode);
    final var task = this.findTask(project.getId(), taskCode);
    this.eventPublisher.publishEvent(
        new TaskDeletedEvent(task.getId(), project.getId(), task.getCode(), ownerUsername));
    this.taskRepository.delete(task);
  }

  @Transactional
  public BranchInfo createBranch(
      final String ownerUsername,
      final String projectCode,
      final String taskCode,
      final CreateBranchFromTaskForm form)
      throws IOException {

    final var project = this.projectService.findProject(ownerUsername, projectCode);
    final var task = this.findTask(project.getId(), taskCode);

    if (task.getBranchName() != null) {
      throw new ErrorOccurredException("Task already has a linked branch: " + task.getBranchName());
    }

    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(form.getRepoOwner(), form.getRepoName())
            .orElseThrow(() -> new ItemNotFoundException("Repository not found"));

    final var branchName =
        form.getBranchName() != null
            ? form.getBranchName()
            : this.slugify(task.getCode() + "-" + task.getTitle());

    final var created = this.createBranch(branchName, form);

    task.setBranchRepo(repo);
    task.setBranchName(branchName);
    task.setStatus(TaskStatus.IN_PROGRESS.name());
    this.taskRepository.save(task);

    return created;
  }

  @Transactional
  public SubtaskInfo createSubtask(
      final String ownerUsername,
      final String projectCode,
      final String taskCode,
      final SubtaskForm form) {

    final var project = this.projectService.findProject(ownerUsername, projectCode);
    final var task = this.findTask(project.getId(), taskCode);

    this.taskRepository.incrementSubtaskSeq(task.getId());
    final var taskWithSeq =
        this.taskRepository
            .findById(task.getId())
            .orElseThrow(() -> new ItemNotFoundException("Task not found"));

    final var subtask = new Subtask();
    subtask.setTask(taskWithSeq);
    subtask.setCode("SUB-" + taskWithSeq.getSubtaskSeq());
    subtask.setTitle(form.getTitle());
    subtask.setDescription(form.getDescription());
    subtask.setStatus(TaskStatus.NOT_STARTED.name());
    subtask.setPosition(form.getPosition());

    return this.toSubtaskInfo(this.subtaskRepository.save(subtask));
  }

  @Transactional
  public SubtaskInfo updateSubtask(
      final String ownerUsername,
      final String projectCode,
      final String taskCode,
      final UUID subtaskId,
      final SubtaskUpdateForm form) {

    final var project = this.projectService.findProject(ownerUsername, projectCode);
    final var task = this.findTask(project.getId(), taskCode);
    final var subtask = this.findSubtask(subtaskId, task.getId());

    if (form.getTitle() != null) {
      subtask.setTitle(form.getTitle());
    }

    if (form.getDescription() != null) {
      subtask.setDescription(form.getDescription());
    }

    if (form.getStatus() != null) {
      subtask.setStatus(this.validateStatus(form.getStatus()));
    }

    if (form.getPosition() != null) {
      subtask.setPosition(form.getPosition());
    }

    return this.toSubtaskInfo(this.subtaskRepository.save(subtask));
  }

  @Transactional
  public BranchInfo createBranchForSubtask(
      final String ownerUsername,
      final String projectCode,
      final String taskCode,
      final UUID subtaskId,
      final CreateBranchFromTaskForm form)
      throws IOException {

    final var project = this.projectService.findProject(ownerUsername, projectCode);
    final var task = this.findTask(project.getId(), taskCode);
    final var subtask = this.findSubtask(subtaskId, task.getId());

    if (subtask.getBranchName() != null) {
      throw new ErrorOccurredException(
          "Subtask already has a linked branch: " + subtask.getBranchName());
    }

    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(form.getRepoOwner(), form.getRepoName())
            .orElseThrow(() -> new ItemNotFoundException("Repository not found"));

    final var branchName =
        form.getBranchName() != null && !form.getBranchName().isBlank()
            ? form.getBranchName().trim()
            : this.slugify(task.getCode() + "." + subtask.getCode() + "-" + subtask.getTitle());

    final var created = this.createBranch(branchName, form);

    subtask.setBranchRepo(repo);
    subtask.setBranchName(branchName);
    subtask.setStatus(TaskStatus.IN_PROGRESS.name());
    this.subtaskRepository.save(subtask);

    return created;
  }

  @Transactional
  public void deleteSubtask(
      final String ownerUsername,
      final String projectCode,
      final String taskCode,
      final UUID subtaskId) {

    final var project = this.projectService.findProject(ownerUsername, projectCode);
    final var task = this.findTask(project.getId(), taskCode);
    final var subtask = this.findSubtask(subtaskId, task.getId());
    this.subtaskRepository.delete(subtask);
  }

  @Transactional
  public void linkPullRequest(final UUID repoId, final String sourceBranch, final UUID prId) {

    if (this.prQueryPort.findById(prId).isEmpty()) {
      throw new ItemNotFoundException("Pull request not found");
    }

    final var taskOpt = this.taskRepository.findByBranchRepoIdAndBranchName(repoId, sourceBranch);
    if (taskOpt.isPresent()) {
      final var task = taskOpt.get();
      task.setLinkedPrId(prId);
      if (task.getStatus().equals(TaskStatus.NOT_STARTED.name())) {
        task.setStatus(TaskStatus.IN_PROGRESS.name());
      }
      this.taskRepository.save(task);
      return;
    }

    this.subtaskRepository
        .findByBranchRepoIdAndBranchName(repoId, sourceBranch)
        .ifPresent(
            subtask -> {
              subtask.setLinkedPrId(prId);
              if (subtask.getStatus().equals(TaskStatus.NOT_STARTED.name())) {
                subtask.setStatus(TaskStatus.IN_PROGRESS.name());
              }
              this.subtaskRepository.save(subtask);
            });
  }

  @Transactional
  public void updateTaskStatusForPr(
      final UUID repoId, final String sourceBranch, final String prStatus) {

    if (!"MERGED".equals(prStatus)) {
      return;
    }

    final var taskOpt = this.taskRepository.findByBranchRepoIdAndBranchName(repoId, sourceBranch);
    if (taskOpt.isPresent()) {
      final var task = taskOpt.get();
      if (task.getProject().isSyncTaskStatusOnPrMerge()) {
        task.setStatus(TaskStatus.COMPLETED.name());
        this.taskRepository.save(task);
      }
      return;
    }

    this.subtaskRepository
        .findByBranchRepoIdAndBranchName(repoId, sourceBranch)
        .ifPresent(
            subtask -> {
              if (subtask.getTask().getProject().isSyncTaskStatusOnPrMerge()) {
                subtask.setStatus(TaskStatus.COMPLETED.name());
                this.subtaskRepository.save(subtask);
              }
            });
  }

  private TaskDetail toDetail(final Task task) {
    final var subtasks =
        this.subtaskRepository.findAllByTaskIdOrderByPositionAsc(task.getId()).stream()
            .map(this::toSubtaskInfo)
            .toList();
    return this.taskMapper.toDetail(
        task,
        this.buildLinkedPrInfo(task.getLinkedPrId()),
        this.buildLinkedIssueInfo(task.getLinkedIssueId()),
        subtasks);
  }

  private SubtaskInfo toSubtaskInfo(final Subtask subtask) {
    return SubtaskInfo.builder()
        .id(subtask.getId())
        .code(subtask.getCode())
        .title(subtask.getTitle())
        .description(subtask.getDescription())
        .status(subtask.getStatus())
        .position(subtask.getPosition())
        .branchName(subtask.getBranchName())
        .branchRepoId(subtask.getBranchRepo() != null ? subtask.getBranchRepo().getId() : null)
        .linkedPr(this.buildLinkedPrInfo(subtask.getLinkedPrId()))
        .createdAt(subtask.getCreatedAt())
        .updatedAt(subtask.getUpdatedAt())
        .build();
  }

  private @Nullable LinkedPrInfo buildLinkedPrInfo(final @Nullable UUID prId) {
    if (prId == null) {
      return null;
    }
    return this.prQueryPort
        .findById(prId)
        .map(
            pr ->
                LinkedPrInfo.builder()
                    .id(pr.id())
                    .number(pr.number())
                    .title(pr.title())
                    .status(pr.status())
                    .sourceBranch(pr.sourceBranch())
                    .targetBranch(pr.targetBranch())
                    .build())
        .orElse(null);
  }

  private @Nullable LinkedIssueInfo buildLinkedIssueInfo(final @Nullable UUID issueId) {
    if (issueId == null) {
      return null;
    }
    return this.issueQueryService
        .findById(issueId)
        .map(
            d ->
                LinkedIssueInfo.builder()
                    .id(d.id())
                    .number(d.number())
                    .title(d.title())
                    .status(d.status())
                    .build())
        .orElse(null);
  }

  private BranchInfo createBranch(final String branchName, final CreateBranchFromTaskForm form)
      throws IOException {

    final var branchForm = new BranchForm();
    branchForm.setName(branchName);
    branchForm.setSourceBranch(form.getSourceBranch());

    return this.branchProtocolService.create(form.getRepoOwner(), form.getRepoName(), branchForm);
  }

  private Task findTask(final UUID projectId, final String taskCode) {
    return this.taskRepository
        .findByProjectIdAndCode(projectId, taskCode)
        .orElseThrow(() -> new ItemNotFoundException("Task not found: " + taskCode));
  }

  private Subtask findSubtask(final UUID subtaskId, final UUID taskId) {
    return this.subtaskRepository
        .findByIdAndTaskId(subtaskId, taskId)
        .orElseThrow(() -> new ItemNotFoundException("Subtask not found"));
  }

  private String validateStatus(final String status) {
    final var valid = Arrays.stream(TaskStatus.values()).map(Enum::name).toList();
    if (!valid.contains(status)) {
      throw new ErrorOccurredException("Invalid status: " + status + ". Valid: " + valid);
    }
    return status;
  }

  private String validateType(final String type) {
    final var valid = Arrays.stream(TaskType.values()).map(Enum::name).toList();
    if (!valid.contains(type)) {
      throw new ErrorOccurredException("Invalid type: " + type + ". Valid: " + valid);
    }
    return type;
  }

  @SuppressWarnings("java:S5850")
  private String slugify(final String input) {
    return input
        .toLowerCase()
        .replaceAll("[^a-z0-9-]", "-")
        .replaceAll("-+", "-")
        .replaceAll("(^-|-$)", "");
  }
}
