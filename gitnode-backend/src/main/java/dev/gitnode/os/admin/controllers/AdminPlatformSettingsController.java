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

import dev.gitnode.os.admin.dtos.AdminFeatureTogglesForm;
import dev.gitnode.os.admin.dtos.AdminPlatformSettingsResponse;
import dev.gitnode.os.admin.dtos.PlatformAdminsResponse;
import dev.gitnode.os.admin.dtos.StatsCacheTtlForm;
import dev.gitnode.os.admin.services.AdminPlatformSettingsService;
import dev.gitnode.os.admin.services.AdminStatsService;
import dev.gitnode.os.admin.services.PlatformAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@NullMarked
@PreAuthorize("@platformAdminService.isCurrentUserPlatformAdmin()")
public class AdminPlatformSettingsController {

  private final AdminPlatformSettingsService adminPlatformSettingsService;
  private final AdminStatsService adminStatsService;
  private final PlatformAdminService platformAdminService;

  @GetMapping
  public ResponseEntity<AdminPlatformSettingsResponse> getSettings() {

    return ResponseEntity.ok(this.adminPlatformSettingsService.getSettings());
  }

  @PutMapping("/stats-cache")
  public ResponseEntity<AdminPlatformSettingsResponse> updateStatsCacheTtl(
      @RequestBody @Valid final StatsCacheTtlForm form) {

    this.adminPlatformSettingsService.updateStatsCacheTtlSeconds(form.statsCacheTtlSeconds());
    this.adminStatsService.evictAllCaches();

    return ResponseEntity.ok(this.adminPlatformSettingsService.getSettings());
  }

  @PutMapping("/feature-toggles")
  public ResponseEntity<AdminPlatformSettingsResponse> updateFeatureToggles(
      @RequestBody @Valid final AdminFeatureTogglesForm form) {

    return ResponseEntity.ok(this.adminPlatformSettingsService.updateFeatureToggles(form));
  }

  @GetMapping("/platform-admins")
  public ResponseEntity<PlatformAdminsResponse> listPlatformAdmins() {

    return ResponseEntity.ok(
        new PlatformAdminsResponse(
            this.platformAdminService.listPlatformAdminUsernames(),
            this.platformAdminService.bootstrapAdminUsername(),
            this.platformAdminService.bootstrapAdminEnabled()));
  }
}
