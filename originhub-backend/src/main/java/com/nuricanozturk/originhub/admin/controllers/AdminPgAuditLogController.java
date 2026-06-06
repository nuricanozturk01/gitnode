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

import com.nuricanozturk.originhub.admin.dtos.PgAuditLogSearchResponse;
import com.nuricanozturk.originhub.admin.dtos.PgAuditLogStatus;
import com.nuricanozturk.originhub.admin.services.PgAuditLogReaderService;
import com.nuricanozturk.originhub.admin.services.PlatformAdminService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/pgaudit-logs")
@RequiredArgsConstructor
@NullMarked
@PreAuthorize("@platformAdminService.isCurrentUserPlatformAdmin()")
public class AdminPgAuditLogController {

  private final PgAuditLogReaderService pgAuditLogReaderService;
  private final PlatformAdminService platformAdminService;

  @GetMapping("/status")
  public ResponseEntity<PgAuditLogStatus> status() {

    this.platformAdminService.requirePlatformAdmin();
    return ResponseEntity.ok(this.pgAuditLogReaderService.status());
  }

  @GetMapping
  public ResponseEntity<PgAuditLogSearchResponse> search(
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size,
      @RequestParam(required = false) final String q,
      @RequestParam(required = false) final String user,
      @RequestParam(required = false) final String category,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          final Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          final Instant to) {

    this.platformAdminService.requirePlatformAdmin();
    return ResponseEntity.ok(
        this.pgAuditLogReaderService.search(page, size, q, user, category, from, to));
  }
}
