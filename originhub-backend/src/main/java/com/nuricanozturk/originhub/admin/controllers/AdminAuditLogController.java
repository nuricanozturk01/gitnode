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
package com.nuricanozturk.originhub.admin.controllers;

import com.nuricanozturk.originhub.admin.dtos.AdminAuditLogFilters;
import com.nuricanozturk.originhub.admin.services.AdminAuditLogService;
import com.nuricanozturk.originhub.admin.services.PlatformAdminService;
import com.nuricanozturk.originhub.shared.audit.entities.AuditLog;
import com.nuricanozturk.originhub.shared.repo.dtos.PageResponse;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@NullMarked
@PreAuthorize("@platformAdminService.isCurrentUserPlatformAdmin()")
public class AdminAuditLogController {

  private static final int DEFAULT_PAGE_SIZE = 20;

  private final AdminAuditLogService adminAuditLogService;
  private final PlatformAdminService platformAdminService;

  @GetMapping
  public ResponseEntity<PageResponse<AuditLog>> search(
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size,
      @RequestParam(required = false) final String q,
      @RequestParam(required = false) final String actor,
      @RequestParam(required = false) final String action,
      @RequestParam(required = false) final String entityType,
      @RequestParam(required = false) final String entityId,
      @RequestParam(required = false) final String ipAddress,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          final Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          final Instant to) {

    this.platformAdminService.requirePlatformAdmin();
    final var pageable = pageable(page, size);
    return ResponseEntity.ok(
        PageResponse.from(
            this.adminAuditLogService.search(
                pageable, q, actor, action, entityType, entityId, ipAddress, from, to)));
  }

  @GetMapping("/filters")
  public ResponseEntity<AdminAuditLogFilters> filters() {

    this.platformAdminService.requirePlatformAdmin();
    return ResponseEntity.ok(this.adminAuditLogService.listFilterOptions());
  }

  @GetMapping("/by-actor")
  public ResponseEntity<PageResponse<AuditLog>> getByActor(
      @RequestParam final String username,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size) {

    this.platformAdminService.requirePlatformAdmin();
    final var pageable = pageable(page, size);
    return ResponseEntity.ok(
        PageResponse.from(
            this.adminAuditLogService.search(
                pageable, null, username, null, null, null, null, null, null)));
  }

  @GetMapping("/recent")
  public ResponseEntity<PageResponse<AuditLog>> getRecent(
      @RequestParam(defaultValue = "24") final int lastHours,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size,
      @RequestParam(required = false) final String actor) {

    this.platformAdminService.requirePlatformAdmin();
    final var pageable = pageable(page, size);
    return ResponseEntity.ok(
        PageResponse.from(this.adminAuditLogService.searchRecent(pageable, lastHours, actor)));
  }

  private static PageRequest pageable(final int page, final int size) {

    return PageRequest.of(
        page,
        AdminAuditLogService.capPageSize(size == 0 ? DEFAULT_PAGE_SIZE : size),
        Sort.by(Sort.Direction.DESC, "occurredAt"));
  }
}
