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

import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.task.dtos.BoardColumnForm;
import com.nuricanozturk.originhub.task.dtos.BoardColumnInfo;
import com.nuricanozturk.originhub.task.dtos.BoardColumnUpdateForm;
import com.nuricanozturk.originhub.task.dtos.BoardForm;
import com.nuricanozturk.originhub.task.dtos.BoardInfo;
import com.nuricanozturk.originhub.task.dtos.BoardUpdateForm;
import com.nuricanozturk.originhub.task.entities.Board;
import com.nuricanozturk.originhub.task.entities.BoardColumn;
import com.nuricanozturk.originhub.task.mappers.ProjectMapper;
import com.nuricanozturk.originhub.task.repositories.BoardColumnRepository;
import com.nuricanozturk.originhub.task.repositories.BoardRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class BoardService {

  private final @NonNull BoardRepository boardRepository;
  private final @NonNull BoardColumnRepository boardColumnRepository;
  private final @NonNull ProjectService projectService;
  private final @NonNull ProjectMapper projectMapper;

  @Transactional
  public @NonNull BoardInfo createBoard(
      final @NonNull String ownerUsername,
      final @NonNull String projectCode,
      final @NonNull BoardForm form) {

    final var project = this.projectService.findProject(ownerUsername, projectCode);

    final var board = new Board();
    board.setProject(project);
    board.setName(form.getName());
    board.setPosition(form.getPosition());

    final var saved = this.boardRepository.save(board);
    return this.toBoardInfo(saved);
  }

  public @NonNull List<BoardInfo> getAllBoards(
      final @NonNull String ownerUsername, final @NonNull String projectCode) {

    final var project = this.projectService.findProject(ownerUsername, projectCode);
    return this.boardRepository.findAllByProjectIdOrderByPositionAsc(project.getId()).stream()
        .map(this::toBoardInfo)
        .toList();
  }

  public @NonNull BoardInfo getBoard(
      final @NonNull String ownerUsername,
      final @NonNull String projectCode,
      final @NonNull UUID boardId) {

    final var project = this.projectService.findProject(ownerUsername, projectCode);
    final var board = this.findBoard(boardId, project.getId());
    return this.toBoardInfo(board);
  }

  @Transactional
  public @NonNull BoardInfo updateBoard(
      final @NonNull String ownerUsername,
      final @NonNull String projectCode,
      final @NonNull UUID boardId,
      final @NonNull BoardUpdateForm form) {

    final var project = this.projectService.findProject(ownerUsername, projectCode);
    final var board = this.findBoard(boardId, project.getId());

    if (form.getName() != null) {
      board.setName(form.getName());
    }

    if (form.getPosition() != null) {
      board.setPosition(form.getPosition());
    }

    return this.toBoardInfo(this.boardRepository.save(board));
  }

  @Transactional
  public void deleteBoard(
      final @NonNull String ownerUsername,
      final @NonNull String projectCode,
      final @NonNull UUID boardId) {

    final var project = this.projectService.findProject(ownerUsername, projectCode);
    final var board = this.findBoard(boardId, project.getId());
    this.boardRepository.delete(board);
  }

  @Transactional
  public @NonNull BoardColumnInfo createColumn(
      final @NonNull String ownerUsername,
      final @NonNull String projectCode,
      final @NonNull UUID boardId,
      final @NonNull BoardColumnForm form) {

    final var project = this.projectService.findProject(ownerUsername, projectCode);
    final var board = this.findBoard(boardId, project.getId());

    final var column = new BoardColumn();
    column.setBoard(board);
    column.setName(form.getName());
    column.setPosition(form.getPosition());
    column.setColor(form.getColor());

    return this.projectMapper.toBoardColumnInfo(this.boardColumnRepository.save(column));
  }

  @Transactional
  public @NonNull BoardColumnInfo updateColumn(
      final @NonNull String ownerUsername,
      final @NonNull String projectCode,
      final @NonNull UUID boardId,
      final @NonNull UUID columnId,
      final @NonNull BoardColumnUpdateForm form) {

    final var project = this.projectService.findProject(ownerUsername, projectCode);
    this.findBoard(boardId, project.getId());
    final var column = this.findColumn(columnId, boardId);

    if (form.getName() != null) {
      column.setName(form.getName());
    }

    if (form.getPosition() != null) {
      column.setPosition(form.getPosition());
    }

    if (form.getColor() != null) {
      column.setColor(form.getColor().isBlank() ? null : form.getColor());
    }

    return this.projectMapper.toBoardColumnInfo(this.boardColumnRepository.save(column));
  }

  @Transactional
  public void deleteColumn(
      final @NonNull String ownerUsername,
      final @NonNull String projectCode,
      final @NonNull UUID boardId,
      final @NonNull UUID columnId) {

    final var project = this.projectService.findProject(ownerUsername, projectCode);
    this.findBoard(boardId, project.getId());
    final var column = this.findColumn(columnId, boardId);
    this.boardColumnRepository.delete(column);
  }

  private @NonNull BoardInfo toBoardInfo(final @NonNull Board board) {
    final var columns =
        this.boardColumnRepository.findAllByBoardIdOrderByPositionAsc(board.getId()).stream()
            .map(this.projectMapper::toBoardColumnInfo)
            .toList();
    return this.projectMapper.toBoardInfo(board, columns);
  }

  private @NonNull Board findBoard(final @NonNull UUID boardId, final @NonNull UUID projectId) {
    return this.boardRepository
        .findByIdAndProjectId(boardId, projectId)
        .orElseThrow(() -> new ItemNotFoundException("Board not found"));
  }

  private @NonNull BoardColumn findColumn(
      final @NonNull UUID columnId, final @NonNull UUID boardId) {
    return this.boardColumnRepository
        .findByIdAndBoardId(columnId, boardId)
        .orElseThrow(() -> new ItemNotFoundException("Column not found"));
  }
}
