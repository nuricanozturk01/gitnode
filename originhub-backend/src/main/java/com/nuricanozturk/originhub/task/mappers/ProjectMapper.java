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

import com.nuricanozturk.originhub.task.dtos.BoardColumnInfo;
import com.nuricanozturk.originhub.task.dtos.BoardInfo;
import com.nuricanozturk.originhub.task.dtos.ProjectInfo;
import com.nuricanozturk.originhub.task.entities.Board;
import com.nuricanozturk.originhub.task.entities.BoardColumn;
import com.nuricanozturk.originhub.task.entities.Project;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
@NullMarked
public interface ProjectMapper {

  @BeanMapping(builder = @Builder())
  @Mapping(target = "taskCount", source = "taskCount")
  @Mapping(target = "isPublic", source = "project.public")
  ProjectInfo toInfo(Project project, long taskCount);

  @BeanMapping(builder = @Builder())
  BoardColumnInfo toBoardColumnInfo(BoardColumn column);

  @BeanMapping(builder = @Builder())
  @Mapping(target = "columns", source = "columns")
  BoardInfo toBoardInfo(Board board, List<BoardColumnInfo> columns);
}
