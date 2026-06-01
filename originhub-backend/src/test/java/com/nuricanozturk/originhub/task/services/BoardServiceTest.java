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

import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.task.dtos.BoardColumnForm;
import com.nuricanozturk.originhub.task.dtos.BoardColumnInfo;
import com.nuricanozturk.originhub.task.dtos.BoardForm;
import com.nuricanozturk.originhub.task.dtos.BoardInfo;
import com.nuricanozturk.originhub.task.entities.Board;
import com.nuricanozturk.originhub.task.entities.BoardColumn;
import com.nuricanozturk.originhub.task.entities.Project;
import com.nuricanozturk.originhub.task.mappers.ProjectMapper;
import com.nuricanozturk.originhub.task.repositories.BoardColumnRepository;
import com.nuricanozturk.originhub.task.repositories.BoardRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BoardService unit tests")
class BoardServiceTest {

  @Mock private BoardRepository boardRepository;
  @Mock private BoardColumnRepository boardColumnRepository;
  @Mock private ProjectService projectService;
  @Mock private ProjectMapper projectMapper;

  @InjectMocks private BoardService boardService;

  @Test
  @DisplayName("createBoard saves board linked to project")
  void createBoard_savesBoard_whenProjectExists() {
    Project project = project();
    when(projectService.findProject("alice", "APP")).thenReturn(project);
    Board saved = new Board();
    saved.setId(UUID.randomUUID());
    saved.setName("Sprint");
    saved.setPosition(0);
    when(boardRepository.save(any(Board.class))).thenReturn(saved);
    when(boardColumnRepository.findAllByBoardIdOrderByPositionAsc(saved.getId()))
        .thenReturn(List.of());
    BoardInfo boardInfo = BoardInfo.builder().id(saved.getId()).name("Sprint").build();
    when(projectMapper.toBoardInfo(saved, List.of())).thenReturn(boardInfo);
    BoardForm form = new BoardForm("Sprint", 0);

    BoardInfo result = boardService.createBoard("alice", "APP", form);

    assertThat(result.name()).isEqualTo("Sprint");
  }

  @Test
  @DisplayName("getBoard throws ItemNotFoundException when board not in project")
  void getBoard_throws_whenBoardMissing() {
    Project project = project();
    UUID boardId = UUID.randomUUID();
    when(projectService.findProjectAsViewer("alice", "APP", null)).thenReturn(project);
    when(boardRepository.findByIdAndProjectId(boardId, project.getId()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> boardService.getBoard("alice", "APP", boardId, null))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("Board not found");
  }

  @Test
  @DisplayName("deleteBoard removes board from project")
  void deleteBoard_deletes_whenFound() {
    Project project = project();
    Board board = new Board();
    board.setId(UUID.randomUUID());
    when(projectService.findProject("alice", "APP")).thenReturn(project);
    when(boardRepository.findByIdAndProjectId(board.getId(), project.getId()))
        .thenReturn(Optional.of(board));

    boardService.deleteBoard("alice", "APP", board.getId());

    verify(boardRepository).delete(board);
  }

  @Test
  @DisplayName("createColumn saves column on board")
  void createColumn_savesColumn_whenBoardExists() {
    Project project = project();
    Board board = new Board();
    board.setId(UUID.randomUUID());
    when(projectService.findProject("alice", "APP")).thenReturn(project);
    when(boardRepository.findByIdAndProjectId(board.getId(), project.getId()))
        .thenReturn(Optional.of(board));
    BoardColumn saved = new BoardColumn();
    saved.setId(UUID.randomUUID());
    saved.setName("Todo");
    when(boardColumnRepository.save(any(BoardColumn.class))).thenReturn(saved);
    BoardColumnInfo columnInfo = BoardColumnInfo.builder().id(saved.getId()).name("Todo").build();
    when(projectMapper.toBoardColumnInfo(saved)).thenReturn(columnInfo);
    BoardColumnForm form = new BoardColumnForm("Todo", 0, null);

    BoardColumnInfo result = boardService.createColumn("alice", "APP", board.getId(), form);

    assertThat(result.name()).isEqualTo("Todo");
  }

  @Test
  @DisplayName("getAllBoards returns boards for visible project")
  void getAllBoards_returnsBoards_whenProjectVisible() {
    Project project = project();
    Board board = new Board();
    board.setId(UUID.randomUUID());
    board.setName("Main");
    board.setPosition(0);
    when(projectService.findProjectAsViewer("alice", "APP", null)).thenReturn(project);
    when(boardRepository.findAllByProjectIdOrderByPositionAsc(project.getId()))
        .thenReturn(List.of(board));
    when(boardColumnRepository.findAllByBoardIdOrderByPositionAsc(board.getId()))
        .thenReturn(List.of());
    when(projectMapper.toBoardInfo(board, List.of()))
        .thenReturn(BoardInfo.builder().id(board.getId()).name("Main").build());

    List<BoardInfo> result = boardService.getAllBoards("alice", "APP", null);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().name()).isEqualTo("Main");
  }

  private static Project project() {
    Project p = new Project();
    p.setId(UUID.randomUUID());
    Tenant owner = new Tenant();
    owner.setUsername("alice");
    p.setOwner(owner);
    p.setCodePrefix("APP");
    p.setPublic(true);
    return p;
  }
}
