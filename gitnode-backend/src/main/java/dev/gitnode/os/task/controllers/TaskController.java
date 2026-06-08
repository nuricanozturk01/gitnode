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
package dev.gitnode.os.task.controllers;

import dev.gitnode.os.shared.branch.dtos.BranchInfo;
import dev.gitnode.os.shared.repo.dtos.PageResponse;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.task.dtos.CreateBranchFromTaskForm;
import dev.gitnode.os.task.dtos.SubtaskForm;
import dev.gitnode.os.task.dtos.SubtaskInfo;
import dev.gitnode.os.task.dtos.SubtaskUpdateForm;
import dev.gitnode.os.task.dtos.TaskDetail;
import dev.gitnode.os.task.dtos.TaskForm;
import dev.gitnode.os.task.dtos.TaskInfo;
import dev.gitnode.os.task.dtos.TaskUpdateForm;
import dev.gitnode.os.task.services.TaskService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{owner}/{projectCode}/tasks")
@RequiredArgsConstructor
@NullMarked
public class TaskController {

  private final TaskService taskService;

  @PostMapping
  public ResponseEntity<TaskDetail> create(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @Valid @RequestBody final TaskForm form) {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.taskService.create(owner, projectCode, form));
  }

  @GetMapping
  public ResponseEntity<PageResponse<TaskInfo>> getAll(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "500") final int size,
      @AuthenticationPrincipal final @Nullable Tenant viewer) {

    return ResponseEntity.ok(
        PageResponse.from(this.taskService.getAll(owner, projectCode, viewer, page, size)));
  }

  @GetMapping("/{taskCode}")
  public ResponseEntity<TaskDetail> get(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @PathVariable final String taskCode,
      @AuthenticationPrincipal final @Nullable Tenant viewer) {

    return ResponseEntity.ok(this.taskService.get(owner, projectCode, taskCode, viewer));
  }

  @PatchMapping("/{taskCode}")
  public ResponseEntity<TaskDetail> update(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @PathVariable final String taskCode,
      @Valid @RequestBody final TaskUpdateForm form) {

    return ResponseEntity.ok(this.taskService.update(owner, projectCode, taskCode, form));
  }

  @DeleteMapping("/{taskCode}")
  public ResponseEntity<Void> delete(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @PathVariable final String taskCode) {

    this.taskService.delete(owner, projectCode, taskCode);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{taskCode}/branch")
  public ResponseEntity<BranchInfo> createBranch(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @PathVariable final String taskCode,
      @Valid @RequestBody final CreateBranchFromTaskForm form)
      throws IOException {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.taskService.createBranch(owner, projectCode, taskCode, form));
  }

  @PostMapping("/{taskCode}/subtasks/{subtaskId}/branch")
  public ResponseEntity<BranchInfo> createBranchForSubtask(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @PathVariable final String taskCode,
      @PathVariable final UUID subtaskId,
      @Valid @RequestBody final CreateBranchFromTaskForm form)
      throws IOException {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            this.taskService.createBranchForSubtask(owner, projectCode, taskCode, subtaskId, form));
  }

  @PostMapping("/{taskCode}/subtasks")
  public ResponseEntity<SubtaskInfo> createSubtask(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @PathVariable final String taskCode,
      @Valid @RequestBody final SubtaskForm form) {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.taskService.createSubtask(owner, projectCode, taskCode, form));
  }

  @PatchMapping("/{taskCode}/subtasks/{subtaskId}")
  public ResponseEntity<SubtaskInfo> updateSubtask(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @PathVariable final String taskCode,
      @PathVariable final UUID subtaskId,
      @Valid @RequestBody final SubtaskUpdateForm form) {

    return ResponseEntity.ok(
        this.taskService.updateSubtask(owner, projectCode, taskCode, subtaskId, form));
  }

  @DeleteMapping("/{taskCode}/subtasks/{subtaskId}")
  public ResponseEntity<Void> deleteSubtask(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @PathVariable final String taskCode,
      @PathVariable final UUID subtaskId) {

    this.taskService.deleteSubtask(owner, projectCode, taskCode, subtaskId);
    return ResponseEntity.noContent().build();
  }
}
