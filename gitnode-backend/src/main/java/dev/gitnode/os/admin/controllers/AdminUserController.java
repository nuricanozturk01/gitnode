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
package dev.gitnode.os.admin.controllers;

import dev.gitnode.os.admin.dtos.AdminUserDetail;
import dev.gitnode.os.admin.dtos.AdminUserSummary;
import dev.gitnode.os.admin.dtos.UserEnabledForm;
import dev.gitnode.os.admin.services.AdminUserService;
import dev.gitnode.os.shared.repo.dtos.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@NullMarked
@PreAuthorize("@platformAdminService.isCurrentUserPlatformAdmin()")
public class AdminUserController {

  private static final int MAX_PAGE_SIZE = 500;

  private final AdminUserService adminUserService;

  @GetMapping
  public ResponseEntity<PageResponse<AdminUserSummary>> list(
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "10") final int size,
      @RequestParam(required = false) final String q) {

    final var pageable =
        PageRequest.of(
            page, Math.min(size, MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "createdAt"));
    return ResponseEntity.ok(PageResponse.from(this.adminUserService.listUsers(pageable, q)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<AdminUserDetail> get(@PathVariable final UUID id) {

    return ResponseEntity.ok(this.adminUserService.getUser(id));
  }

  @PutMapping("/{id}/enabled")
  public ResponseEntity<AdminUserSummary> setEnabled(
      @PathVariable final UUID id, @RequestBody @Valid final UserEnabledForm form) {

    final var detail = this.adminUserService.setEnabled(id, form.enabled());
    return ResponseEntity.ok(
        new AdminUserSummary(
            detail.id(), detail.username(), detail.email(), detail.enabled(), detail.createdAt()));
  }
}
