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
import org.jspecify.annotations.NonNull;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProjectMapper {

  @BeanMapping(builder = @Builder())
  @NonNull ProjectInfo toInfo(@NonNull Project project);

  default @NonNull BoardColumnInfo toBoardColumnInfo(final @NonNull BoardColumn column) {
    return BoardColumnInfo.builder()
        .id(column.getId())
        .name(column.getName())
        .position(column.getPosition())
        .color(column.getColor())
        .createdAt(column.getCreatedAt())
        .updatedAt(column.getUpdatedAt())
        .build();
  }

  default @NonNull BoardInfo toBoardInfo(
      final @NonNull Board board, final @NonNull List<BoardColumnInfo> columns) {
    return BoardInfo.builder()
        .id(board.getId())
        .name(board.getName())
        .position(board.getPosition())
        .columns(columns)
        .createdAt(board.getCreatedAt())
        .updatedAt(board.getUpdatedAt())
        .build();
  }
}
