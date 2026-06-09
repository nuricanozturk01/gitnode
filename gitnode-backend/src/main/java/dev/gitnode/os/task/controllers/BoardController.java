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

import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.task.dtos.BoardColumnForm;
import dev.gitnode.os.task.dtos.BoardColumnInfo;
import dev.gitnode.os.task.dtos.BoardColumnUpdateForm;
import dev.gitnode.os.task.dtos.BoardForm;
import dev.gitnode.os.task.dtos.BoardInfo;
import dev.gitnode.os.task.dtos.BoardUpdateForm;
import dev.gitnode.os.task.services.BoardService;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{owner}/{projectCode}/boards")
@RequiredArgsConstructor
@NullMarked
public class BoardController {

  private final BoardService boardService;

  @PostMapping
  public ResponseEntity<BoardInfo> createBoard(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @Valid @RequestBody final BoardForm form) {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.boardService.createBoard(owner, projectCode, form));
  }

  @GetMapping
  public ResponseEntity<List<BoardInfo>> getAllBoards(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @AuthenticationPrincipal final @Nullable Tenant viewer) {

    return ResponseEntity.ok(this.boardService.getAllBoards(owner, projectCode, viewer));
  }

  @GetMapping("/{boardId}")
  public ResponseEntity<BoardInfo> getBoard(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @PathVariable final UUID boardId,
      @AuthenticationPrincipal final @Nullable Tenant viewer) {

    return ResponseEntity.ok(this.boardService.getBoard(owner, projectCode, boardId, viewer));
  }

  @PatchMapping("/{boardId}")
  public ResponseEntity<BoardInfo> updateBoard(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @PathVariable final UUID boardId,
      @Valid @RequestBody final BoardUpdateForm form) {

    return ResponseEntity.ok(this.boardService.updateBoard(owner, projectCode, boardId, form));
  }

  @DeleteMapping("/{boardId}")
  public ResponseEntity<Void> deleteBoard(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @PathVariable final UUID boardId) {

    this.boardService.deleteBoard(owner, projectCode, boardId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{boardId}/columns")
  public ResponseEntity<BoardColumnInfo> createColumn(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @PathVariable final UUID boardId,
      @Valid @RequestBody final BoardColumnForm form) {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.boardService.createColumn(owner, projectCode, boardId, form));
  }

  @PatchMapping("/{boardId}/columns/{columnId}")
  public ResponseEntity<BoardColumnInfo> updateColumn(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @PathVariable final UUID boardId,
      @PathVariable final UUID columnId,
      @Valid @RequestBody final BoardColumnUpdateForm form) {

    return ResponseEntity.ok(
        this.boardService.updateColumn(owner, projectCode, boardId, columnId, form));
  }

  @DeleteMapping("/{boardId}/columns/{columnId}")
  public ResponseEntity<Void> deleteColumn(
      @PathVariable final String owner,
      @PathVariable final String projectCode,
      @PathVariable final UUID boardId,
      @PathVariable final UUID columnId) {

    this.boardService.deleteColumn(owner, projectCode, boardId, columnId);
    return ResponseEntity.noContent().build();
  }
}
