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
package dev.gitnode.os.actions.controllers;

import dev.gitnode.os.actions.dtos.request.RunnerGroupRequest;
import dev.gitnode.os.actions.dtos.response.RunnerGroupResponse;
import dev.gitnode.os.actions.services.RunnerGroupService;
import dev.gitnode.os.shared.auth.services.JwtUtils;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orgs/{orgId}/actions/runner-groups")
@RequiredArgsConstructor
@NullMarked
public class RunnerGroupController {

  private final RunnerGroupService groupService;
  private final JwtUtils jwtUtils;

  record PageResponse<T>(List<T> content, long totalElements, int totalPages, int page, int size) {}

  @GetMapping
  public ResponseEntity<PageResponse<RunnerGroupResponse>> list(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final Long orgId,
      @PageableDefault(size = 20) final Pageable pageable) {

    this.jwtUtils.extractUserId(authHeader);
    final var page = this.groupService.listByOrg(orgId, pageable);
    return ResponseEntity.ok(
        new PageResponse<>(
            page.getContent(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.getSize()));
  }

  @PostMapping
  public ResponseEntity<RunnerGroupResponse> create(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final Long orgId,
      @Valid @RequestBody final RunnerGroupRequest req) {

    this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.status(HttpStatus.CREATED).body(this.groupService.create(orgId, req));
  }

  @DeleteMapping("/{groupId}")
  public ResponseEntity<Void> delete(
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @PathVariable final Long orgId,
      @PathVariable final Long groupId) {

    this.jwtUtils.extractUserId(authHeader);
    this.groupService.delete(groupId, orgId);
    return ResponseEntity.noContent().build();
  }
}
