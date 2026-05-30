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
import com.nuricanozturk.originhub.pr.entities.PullRequest;
import com.nuricanozturk.originhub.pr.repositories.PrRepository;
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
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class TaskService {

  private final @NonNull TaskRepository taskRepository;
  private final @NonNull SubtaskRepository subtaskRepository;
  private final @NonNull BoardColumnRepository boardColumnRepository;
  private final @NonNull ProjectRepository projectRepository;
  private final @NonNull RepoRepository repoRepository;
  private final @NonNull TenantRepository tenantRepository;
  private final @NonNull PrRepository prRepository;
  private final @NonNull IssueQueryService issueQueryService;
  private final @NonNull ProjectService projectService;
  private final @NonNull TaskMapper taskMapper;
  private final @NonNull BranchProtocolService branchProtocolService;
  private final @NonNull ApplicationEventPublisher eventPublisher;

  @Transactional
  public @NonNull TaskDetail create(
      final @NonNull String ownerUsername,
      final @NonNull String projectCode,
      final @NonNull TaskForm form) {

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

  public @NonNull List<TaskInfo> getAll(
      final @NonNull String ownerUsername,
      final @NonNull String projectCode,
      final @Nullable Tenant viewer) {

    final var project = this.projectService.findProjectAsViewer(ownerUsername, projectCode, viewer);
    return this.taskRepository.findAllByProjectIdOrderByPositionAsc(project.getId()).stream()
        .map(
            task -> {
              final int total = this.subtaskRepository.countByTaskId(task.getId());
              final int completed =
                  this.subtaskRepository.countByTaskIdAndStatus(task.getId(), "COMPLETED");
              return this.taskMapper.toInfo(task, total, completed);
            })
        .toList();
  }

  public @NonNull TaskDetail get(
      final @NonNull String ownerUsername,
      final @NonNull String projectCode,
      final @NonNull String taskCode,
      final @Nullable Tenant viewer) {

    final var project = this.projectService.findProjectAsViewer(ownerUsername, projectCode, viewer);
    return this.toDetail(this.findTask(project.getId(), taskCode));
  }

  @Transactional
  public @NonNull TaskDetail update(
      final @NonNull String ownerUsername,
      final @NonNull String projectCode,
      final @NonNull String taskCode,
      final @NonNull TaskUpdateForm form) {

    final var project = this.projectService.findProject(ownerUsername, projectCode);
    final var task = this.findTask(project.getId(), taskCode);

    this.applyTaskUpdates(task, form);

    final var saved = this.taskRepository.save(task);
    this.eventPublisher.publishEvent(
        new TaskUpdatedEvent(saved.getId(), project.getId(), saved.getCode(), ownerUsername));
    return this.toDetail(saved);
  }

  private void applyTaskUpdates(final @NonNull Task task, final @NonNull TaskUpdateForm form) {
    this.applyScalarUpdates(task, form);
    this.applyRelationUpdates(task, form);
  }

  private void applyScalarUpdates(final @NonNull Task task, final @NonNull TaskUpdateForm form) {
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

  private void applyRelationUpdates(final @NonNull Task task, final @NonNull TaskUpdateForm form) {
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
  public void delete(
      final @NonNull String ownerUsername,
      final @NonNull String projectCode,
      final @NonNull String taskCode) {

    final var project = this.projectService.findProject(ownerUsername, projectCode);
    final var task = this.findTask(project.getId(), taskCode);
    this.eventPublisher.publishEvent(
        new TaskDeletedEvent(task.getId(), project.getId(), task.getCode(), ownerUsername));
    this.taskRepository.delete(task);
  }

  @Transactional
  public @NonNull BranchInfo createBranch(
      final @NonNull String ownerUsername,
      final @NonNull String projectCode,
      final @NonNull String taskCode,
      final @NonNull CreateBranchFromTaskForm form)
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
  public @NonNull SubtaskInfo createSubtask(
      final @NonNull String ownerUsername,
      final @NonNull String projectCode,
      final @NonNull String taskCode,
      final @NonNull SubtaskForm form) {

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
  public @NonNull SubtaskInfo updateSubtask(
      final @NonNull String ownerUsername,
      final @NonNull String projectCode,
      final @NonNull String taskCode,
      final @NonNull UUID subtaskId,
      final @NonNull SubtaskUpdateForm form) {

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
  public @NonNull BranchInfo createBranchForSubtask(
      final @NonNull String ownerUsername,
      final @NonNull String projectCode,
      final @NonNull String taskCode,
      final @NonNull UUID subtaskId,
      final @NonNull CreateBranchFromTaskForm form)
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
      final @NonNull String ownerUsername,
      final @NonNull String projectCode,
      final @NonNull String taskCode,
      final @NonNull UUID subtaskId) {

    final var project = this.projectService.findProject(ownerUsername, projectCode);
    final var task = this.findTask(project.getId(), taskCode);
    final var subtask = this.findSubtask(subtaskId, task.getId());
    this.subtaskRepository.delete(subtask);
  }

  @Transactional
  public void linkPullRequest(
      final @NonNull UUID repoId, final @NonNull String sourceBranch, final @NonNull UUID prId) {

    final var pr =
        this.prRepository
            .findById(prId)
            .orElseThrow(() -> new ItemNotFoundException("Pull request not found"));

    final var taskOpt = this.taskRepository.findByBranchRepoIdAndBranchName(repoId, sourceBranch);
    if (taskOpt.isPresent()) {
      final var task = taskOpt.get();
      task.setLinkedPr(pr);
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
              subtask.setLinkedPr(pr);
              if (subtask.getStatus().equals(TaskStatus.NOT_STARTED.name())) {
                subtask.setStatus(TaskStatus.IN_PROGRESS.name());
              }
              this.subtaskRepository.save(subtask);
            });
  }

  @Transactional
  public void updateTaskStatusForPr(
      final @NonNull UUID repoId,
      final @NonNull String sourceBranch,
      final @NonNull String prStatus) {

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

  private @NonNull TaskDetail toDetail(final @NonNull Task task) {
    final var subtasks =
        this.subtaskRepository.findAllByTaskIdOrderByPositionAsc(task.getId()).stream()
            .map(this::toSubtaskInfo)
            .toList();
    return this.taskMapper.toDetail(
        task,
        this.buildLinkedPrInfo(task.getLinkedPr()),
        this.buildLinkedIssueInfo(task.getLinkedIssueId()),
        subtasks);
  }

  private @NonNull SubtaskInfo toSubtaskInfo(final @NonNull Subtask subtask) {
    return SubtaskInfo.builder()
        .id(subtask.getId())
        .code(subtask.getCode())
        .title(subtask.getTitle())
        .description(subtask.getDescription())
        .status(subtask.getStatus())
        .position(subtask.getPosition())
        .branchName(subtask.getBranchName())
        .branchRepoId(subtask.getBranchRepo() != null ? subtask.getBranchRepo().getId() : null)
        .linkedPr(this.buildLinkedPrInfo(subtask.getLinkedPr()))
        .createdAt(subtask.getCreatedAt())
        .updatedAt(subtask.getUpdatedAt())
        .build();
  }

  private @Nullable LinkedPrInfo buildLinkedPrInfo(final @Nullable PullRequest pr) {
    if (pr == null) {
      return null;
    }
    return LinkedPrInfo.builder()
        .id(pr.getId())
        .number(pr.getNumber())
        .title(pr.getTitle())
        .status(pr.getStatus())
        .sourceBranch(pr.getSourceBranch())
        .targetBranch(pr.getTargetBranch())
        .build();
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

  private @NonNull BranchInfo createBranch(
      final @NonNull String branchName, final @NonNull CreateBranchFromTaskForm form)
      throws IOException {

    final var branchForm = new BranchForm();
    branchForm.setName(branchName);
    branchForm.setSourceBranch(form.getSourceBranch());

    return this.branchProtocolService.create(form.getRepoOwner(), form.getRepoName(), branchForm);
  }

  private @NonNull Task findTask(final @NonNull UUID projectId, final @NonNull String taskCode) {
    return this.taskRepository
        .findByProjectIdAndCode(projectId, taskCode)
        .orElseThrow(() -> new ItemNotFoundException("Task not found: " + taskCode));
  }

  private @NonNull Subtask findSubtask(final @NonNull UUID subtaskId, final @NonNull UUID taskId) {
    return this.subtaskRepository
        .findByIdAndTaskId(subtaskId, taskId)
        .orElseThrow(() -> new ItemNotFoundException("Subtask not found"));
  }

  private @NonNull String validateStatus(final @NonNull String status) {
    final var valid = Arrays.stream(TaskStatus.values()).map(Enum::name).toList();
    if (!valid.contains(status)) {
      throw new ErrorOccurredException("Invalid status: " + status + ". Valid: " + valid);
    }
    return status;
  }

  private @NonNull String validateType(final @NonNull String type) {
    final var valid = Arrays.stream(TaskType.values()).map(Enum::name).toList();
    if (!valid.contains(type)) {
      throw new ErrorOccurredException("Invalid type: " + type + ". Valid: " + valid);
    }
    return type;
  }

  @SuppressWarnings("java:S5850")
  private @NonNull String slugify(final @NonNull String input) {
    return input
        .toLowerCase()
        .replaceAll("[^a-z0-9-]", "-")
        .replaceAll("-+", "-")
        .replaceAll("(^-|-$)", "");
  }
}
