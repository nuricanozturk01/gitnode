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
package com.nuricanozturk.originhub.shared.audit.controllers;

import com.nuricanozturk.originhub.shared.audit.entities.AuditLog;
import com.nuricanozturk.originhub.shared.audit.repositories.AuditLogRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AuditLogController {

  private static final int DEFAULT_PAGE_SIZE = 50;

  private final AuditLogRepository auditLogRepository;

  @GetMapping
  public ResponseEntity<Page<AuditLog>> getAll(
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "50") final int size) {

    final var pageable =
        PageRequest.of(page, Math.min(size, DEFAULT_PAGE_SIZE), Sort.by("occurredAt").descending());
    return ResponseEntity.ok(this.auditLogRepository.findAll(pageable));
  }

  @GetMapping("/by-actor")
  public ResponseEntity<Page<AuditLog>> getByActor(
      @RequestParam final String username,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "50") final int size) {

    final var pageable =
        PageRequest.of(page, Math.min(size, DEFAULT_PAGE_SIZE), Sort.by("occurredAt").descending());
    return ResponseEntity.ok(
        this.auditLogRepository.findAllByActorUsernameOrderByOccurredAtDesc(username, pageable));
  }

  @GetMapping("/recent")
  public ResponseEntity<Page<AuditLog>> getRecent(
      @RequestParam(defaultValue = "24") final int lastHours,
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "50") final int size) {

    final var since = Instant.now().minus(Math.min(lastHours, 720), ChronoUnit.HOURS);
    final var pageable =
        PageRequest.of(page, Math.min(size, DEFAULT_PAGE_SIZE), Sort.by("occurredAt").descending());
    return ResponseEntity.ok(
        this.auditLogRepository.findAllByOccurredAtAfterOrderByOccurredAtDesc(since, pageable));
  }
}
