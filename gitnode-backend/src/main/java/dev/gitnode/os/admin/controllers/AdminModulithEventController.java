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

import dev.gitnode.os.admin.dtos.ModulithEventDetail;
import dev.gitnode.os.admin.dtos.ModulithEventFilters;
import dev.gitnode.os.admin.dtos.ModulithEventLifecycleFilter;
import dev.gitnode.os.admin.dtos.ModulithEventSearchResponse;
import dev.gitnode.os.admin.services.AdminModulithEventService;
import dev.gitnode.os.admin.services.PlatformAdminService;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/modulith-events")
@RequiredArgsConstructor
@NullMarked
@PreAuthorize("@platformAdminService.isCurrentUserPlatformAdmin()")
public class AdminModulithEventController {

  private static final int DEFAULT_PAGE_SIZE = 20;

  private final AdminModulithEventService adminModulithEventService;
  private final PlatformAdminService platformAdminService;

  @GetMapping("/status")
  public ResponseEntity<ModulithEventAvailabilityResponse> status() {

    this.platformAdminService.requirePlatformAdmin();
    final boolean available = this.adminModulithEventService.isAvailable();
    return ResponseEntity.ok(
        new ModulithEventAvailabilityResponse(
            available,
            available
                ? "Spring Modulith event viewer is enabled."
                : AdminModulithEventService.DISABLED_MESSAGE));
  }

  @GetMapping
  public ResponseEntity<ModulithEventSearchResponse> search(
      @RequestParam(defaultValue = "0") final int page,
      @RequestParam(defaultValue = "20") final int size,
      @RequestParam(required = false) final String q,
      @RequestParam(required = false) final String eventType,
      @RequestParam(required = false) final String listenerId,
      @RequestParam(required = false) final String status,
      @RequestParam(defaultValue = "ALL") final ModulithEventLifecycleFilter lifecycle,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          final Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          final Instant to) {

    this.platformAdminService.requirePlatformAdmin();
    return ResponseEntity.ok(
        this.adminModulithEventService.search(
            pageable(page, size), q, eventType, listenerId, status, lifecycle, from, to));
  }

  @GetMapping("/filters")
  public ResponseEntity<ModulithEventFilters> filters() {

    this.platformAdminService.requirePlatformAdmin();
    return ResponseEntity.ok(this.adminModulithEventService.listFilterOptions());
  }

  @GetMapping("/{id}")
  public ResponseEntity<ModulithEventDetail> detail(@PathVariable final UUID id) {

    this.platformAdminService.requirePlatformAdmin();
    return ResponseEntity.ok(this.adminModulithEventService.getDetail(id));
  }

  private static PageRequest pageable(final int page, final int size) {

    return PageRequest.of(
        page,
        AdminModulithEventService.capPageSize(size == 0 ? DEFAULT_PAGE_SIZE : size),
        Sort.by(Sort.Direction.DESC, "publicationDate"));
  }

  public record ModulithEventAvailabilityResponse(boolean available, String message) {}
}
