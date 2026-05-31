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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
@NullMarked
public interface TaskMapper {

  @BeanMapping(builder = @Builder())
  @Mapping(target = "boardColumnId", source = "task.boardColumn.id")
  @Mapping(target = "hasLinkedPr", expression = "java(task.getLinkedPrId() != null)")
  @Mapping(target = "hasLinkedIssue", expression = "java(task.getLinkedIssueId() != null)")
  @Mapping(target = "subtaskCount", source = "subtaskCount")
  @Mapping(target = "completedSubtaskCount", source = "completedSubtaskCount")
  TaskInfo toInfo(Task task, int subtaskCount, int completedSubtaskCount);

  @BeanMapping(builder = @Builder())
  @Mapping(target = "id", source = "task.id")
  @Mapping(target = "code", source = "task.code")
  @Mapping(target = "title", source = "task.title")
  @Mapping(target = "description", source = "task.description")
  @Mapping(target = "status", source = "task.status")
  @Mapping(target = "type", source = "task.type")
  @Mapping(target = "position", source = "task.position")
  @Mapping(target = "boardColumnId", source = "task.boardColumn.id")
  @Mapping(target = "assignee", source = "task.assignee")
  @Mapping(target = "branchName", source = "task.branchName")
  @Mapping(
      target = "branchRepoId",
      expression = "java(task.getBranchRepo() != null ? task.getBranchRepo().getId() : null)")
  @Mapping(target = "linkedPr", source = "linkedPr")
  @Mapping(target = "linkedIssue", source = "linkedIssue")
  @Mapping(target = "subtasks", source = "subtasks")
  @Mapping(target = "createdAt", source = "task.createdAt")
  @Mapping(target = "updatedAt", source = "task.updatedAt")
  TaskDetail toDetail(
      Task task,
      @Nullable LinkedPrInfo linkedPr,
      @Nullable LinkedIssueInfo linkedIssue,
      List<SubtaskInfo> subtasks);

  @Mapping(target = "name", source = "displayName")
  @Nullable AuthorInfo toAuthorInfo(@Nullable Tenant tenant);
}
