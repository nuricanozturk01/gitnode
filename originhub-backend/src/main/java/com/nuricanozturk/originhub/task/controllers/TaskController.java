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
package com.nuricanozturk.originhub.task.controllers;

import com.nuricanozturk.originhub.shared.branch.dtos.BranchInfo;
import com.nuricanozturk.originhub.task.dtos.CreateBranchFromTaskForm;
import com.nuricanozturk.originhub.task.dtos.SubtaskForm;
import com.nuricanozturk.originhub.task.dtos.SubtaskInfo;
import com.nuricanozturk.originhub.task.dtos.SubtaskUpdateForm;
import com.nuricanozturk.originhub.task.dtos.TaskDetail;
import com.nuricanozturk.originhub.task.dtos.TaskForm;
import com.nuricanozturk.originhub.task.dtos.TaskInfo;
import com.nuricanozturk.originhub.task.dtos.TaskUpdateForm;
import com.nuricanozturk.originhub.task.services.TaskService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{owner}/{projectCode}/tasks")
@RequiredArgsConstructor
public class TaskController {

  private final @NonNull TaskService taskService;

  @PostMapping
  public @NonNull ResponseEntity<TaskDetail> create(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String projectCode,
      @Valid @RequestBody final @NonNull TaskForm form) {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.taskService.create(owner, projectCode, form));
  }

  @GetMapping
  public @NonNull ResponseEntity<List<TaskInfo>> getAll(
      @PathVariable final @NonNull String owner, @PathVariable final @NonNull String projectCode) {

    return ResponseEntity.ok(this.taskService.getAll(owner, projectCode));
  }

  @GetMapping("/{taskCode}")
  public @NonNull ResponseEntity<TaskDetail> get(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String projectCode,
      @PathVariable final @NonNull String taskCode) {

    return ResponseEntity.ok(this.taskService.get(owner, projectCode, taskCode));
  }

  @PatchMapping("/{taskCode}")
  public @NonNull ResponseEntity<TaskDetail> update(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String projectCode,
      @PathVariable final @NonNull String taskCode,
      @Valid @RequestBody final @NonNull TaskUpdateForm form) {

    return ResponseEntity.ok(this.taskService.update(owner, projectCode, taskCode, form));
  }

  @DeleteMapping("/{taskCode}")
  public @NonNull ResponseEntity<Void> delete(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String projectCode,
      @PathVariable final @NonNull String taskCode) {

    this.taskService.delete(owner, projectCode, taskCode);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{taskCode}/branch")
  public @NonNull ResponseEntity<BranchInfo> createBranch(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String projectCode,
      @PathVariable final @NonNull String taskCode,
      @Valid @RequestBody final @NonNull CreateBranchFromTaskForm form)
      throws IOException {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.taskService.createBranch(owner, projectCode, taskCode, form));
  }

  @PostMapping("/{taskCode}/subtasks")
  public @NonNull ResponseEntity<SubtaskInfo> createSubtask(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String projectCode,
      @PathVariable final @NonNull String taskCode,
      @Valid @RequestBody final @NonNull SubtaskForm form) {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.taskService.createSubtask(owner, projectCode, taskCode, form));
  }

  @PatchMapping("/{taskCode}/subtasks/{subtaskId}")
  public @NonNull ResponseEntity<SubtaskInfo> updateSubtask(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String projectCode,
      @PathVariable final @NonNull String taskCode,
      @PathVariable final @NonNull UUID subtaskId,
      @Valid @RequestBody final @NonNull SubtaskUpdateForm form) {

    return ResponseEntity.ok(
        this.taskService.updateSubtask(owner, projectCode, taskCode, subtaskId, form));
  }

  @DeleteMapping("/{taskCode}/subtasks/{subtaskId}")
  public @NonNull ResponseEntity<Void> deleteSubtask(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String projectCode,
      @PathVariable final @NonNull String taskCode,
      @PathVariable final @NonNull UUID subtaskId) {

    this.taskService.deleteSubtask(owner, projectCode, taskCode, subtaskId);
    return ResponseEntity.noContent().build();
  }
}
