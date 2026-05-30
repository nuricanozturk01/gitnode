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
package com.nuricanozturk.originhub.task.mappers;

import com.nuricanozturk.originhub.shared.commit.dtos.AuthorInfo;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.task.dtos.LinkedIssueInfo;
import com.nuricanozturk.originhub.task.dtos.LinkedPrInfo;
import com.nuricanozturk.originhub.task.dtos.SubtaskInfo;
import com.nuricanozturk.originhub.task.dtos.TaskDetail;
import com.nuricanozturk.originhub.task.dtos.TaskInfo;
import com.nuricanozturk.originhub.task.entities.Task;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TaskMapper {

  default @NonNull TaskInfo toInfo(
      final @NonNull Task task, final int subtaskCount, final int completedSubtaskCount) {
    return TaskInfo.builder()
        .id(task.getId())
        .code(task.getCode())
        .title(task.getTitle())
        .status(task.getStatus())
        .type(task.getType())
        .position(task.getPosition())
        .boardColumnId(task.getBoardColumn().getId())
        .assignee(this.toAuthorInfo(task.getAssignee()))
        .branchName(task.getBranchName())
        .hasLinkedPr(task.getLinkedPr() != null)
        .hasLinkedIssue(task.getLinkedIssueId() != null)
        .subtaskCount(subtaskCount)
        .completedSubtaskCount(completedSubtaskCount)
        .createdAt(task.getCreatedAt())
        .updatedAt(task.getUpdatedAt())
        .build();
  }

  default @NonNull TaskDetail toDetail(
      final @NonNull Task task,
      final @Nullable LinkedPrInfo linkedPr,
      final @Nullable LinkedIssueInfo linkedIssue,
      final @NonNull List<SubtaskInfo> subtasks) {
    return TaskDetail.builder()
        .id(task.getId())
        .code(task.getCode())
        .title(task.getTitle())
        .description(task.getDescription())
        .status(task.getStatus())
        .type(task.getType())
        .position(task.getPosition())
        .boardColumnId(task.getBoardColumn().getId())
        .assignee(this.toAuthorInfo(task.getAssignee()))
        .branchName(task.getBranchName())
        .branchRepoId(task.getBranchRepo() != null ? task.getBranchRepo().getId() : null)
        .linkedPr(linkedPr)
        .linkedIssue(linkedIssue)
        .subtasks(subtasks)
        .createdAt(task.getCreatedAt())
        .updatedAt(task.getUpdatedAt())
        .build();
  }

  default @Nullable AuthorInfo toAuthorInfo(final @Nullable Tenant tenant) {
    if (tenant == null) {
      return null;
    }
    return new AuthorInfo(
        tenant.getDisplayName(), tenant.getEmail(), tenant.getUsername(), tenant.getAvatarUrl());
  }
}
