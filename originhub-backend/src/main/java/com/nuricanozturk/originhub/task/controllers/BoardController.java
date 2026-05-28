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

import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.task.dtos.BoardColumnForm;
import com.nuricanozturk.originhub.task.dtos.BoardColumnInfo;
import com.nuricanozturk.originhub.task.dtos.BoardColumnUpdateForm;
import com.nuricanozturk.originhub.task.dtos.BoardForm;
import com.nuricanozturk.originhub.task.dtos.BoardInfo;
import com.nuricanozturk.originhub.task.dtos.BoardUpdateForm;
import com.nuricanozturk.originhub.task.services.BoardService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
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
public class BoardController {

  private final @NonNull BoardService boardService;

  @PostMapping
  public @NonNull ResponseEntity<BoardInfo> createBoard(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String projectCode,
      @Valid @RequestBody final @NonNull BoardForm form) {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.boardService.createBoard(owner, projectCode, form));
  }

  @GetMapping
  public @NonNull ResponseEntity<List<BoardInfo>> getAllBoards(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String projectCode,
      @AuthenticationPrincipal final @Nullable Tenant viewer) {

    return ResponseEntity.ok(this.boardService.getAllBoards(owner, projectCode, viewer));
  }

  @GetMapping("/{boardId}")
  public @NonNull ResponseEntity<BoardInfo> getBoard(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String projectCode,
      @PathVariable final @NonNull UUID boardId,
      @AuthenticationPrincipal final @Nullable Tenant viewer) {

    return ResponseEntity.ok(this.boardService.getBoard(owner, projectCode, boardId, viewer));
  }

  @PatchMapping("/{boardId}")
  public @NonNull ResponseEntity<BoardInfo> updateBoard(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String projectCode,
      @PathVariable final @NonNull UUID boardId,
      @Valid @RequestBody final @NonNull BoardUpdateForm form) {

    return ResponseEntity.ok(this.boardService.updateBoard(owner, projectCode, boardId, form));
  }

  @DeleteMapping("/{boardId}")
  public @NonNull ResponseEntity<Void> deleteBoard(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String projectCode,
      @PathVariable final @NonNull UUID boardId) {

    this.boardService.deleteBoard(owner, projectCode, boardId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{boardId}/columns")
  public @NonNull ResponseEntity<BoardColumnInfo> createColumn(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String projectCode,
      @PathVariable final @NonNull UUID boardId,
      @Valid @RequestBody final @NonNull BoardColumnForm form) {

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.boardService.createColumn(owner, projectCode, boardId, form));
  }

  @PatchMapping("/{boardId}/columns/{columnId}")
  public @NonNull ResponseEntity<BoardColumnInfo> updateColumn(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String projectCode,
      @PathVariable final @NonNull UUID boardId,
      @PathVariable final @NonNull UUID columnId,
      @Valid @RequestBody final @NonNull BoardColumnUpdateForm form) {

    return ResponseEntity.ok(
        this.boardService.updateColumn(owner, projectCode, boardId, columnId, form));
  }

  @DeleteMapping("/{boardId}/columns/{columnId}")
  public @NonNull ResponseEntity<Void> deleteColumn(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String projectCode,
      @PathVariable final @NonNull UUID boardId,
      @PathVariable final @NonNull UUID columnId) {

    this.boardService.deleteColumn(owner, projectCode, boardId, columnId);
    return ResponseEntity.noContent().build();
  }
}
