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
package com.nuricanozturk.originhub.admin.services;

import com.nuricanozturk.originhub.admin.dtos.AdminAuditLogFilters;
import com.nuricanozturk.originhub.shared.audit.entities.AuditLog;
import com.nuricanozturk.originhub.shared.audit.repositories.AuditLogRepository;
import com.nuricanozturk.originhub.shared.audit.specifications.AuditLogSpecifications;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NullMarked
public class AdminAuditLogService {

  static final int MAX_PAGE_SIZE = 50;
  static final int MAX_LAST_HOURS = 720;
  static final int FILTER_OPTIONS_LIMIT = 200;

  private final AuditLogRepository auditLogRepository;

  @Transactional(readOnly = true)
  public Page<AuditLog> search(
      final Pageable pageable,
      final @Nullable String q,
      final @Nullable String actor,
      final @Nullable String action,
      final @Nullable String entityType,
      final @Nullable String entityId,
      final @Nullable Instant from,
      final @Nullable Instant to) {

    return this.auditLogRepository.findAll(
        AuditLogSpecifications.search(
            blankToNull(q),
            blankToNull(actor),
            blankToNull(action),
            blankToNull(entityType),
            blankToNull(entityId),
            from,
            to),
        pageable);
  }

  @Transactional(readOnly = true)
  public Page<AuditLog> searchRecent(
      final Pageable pageable, final int lastHours, final @Nullable String actor) {

    final var cappedHours = Math.min(lastHours, MAX_LAST_HOURS);
    final var since = Instant.now().minus(cappedHours, ChronoUnit.HOURS);
    return this.search(pageable, null, actor, null, null, null, since, null);
  }

  @Transactional(readOnly = true)
  public AdminAuditLogFilters listFilterOptions() {

    final var pageable = PageRequest.of(0, FILTER_OPTIONS_LIMIT);
    return new AdminAuditLogFilters(
        this.auditLogRepository.findDistinctActions(pageable),
        this.auditLogRepository.findDistinctEntityTypes(pageable));
  }

  public static int capPageSize(final int size) {

    return Math.min(size, MAX_PAGE_SIZE);
  }

  private static @Nullable String blankToNull(final @Nullable String value) {

    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
