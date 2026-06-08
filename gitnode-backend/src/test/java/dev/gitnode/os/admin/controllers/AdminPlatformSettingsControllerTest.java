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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.admin.dtos.AdminFeatureTogglesForm;
import dev.gitnode.os.admin.dtos.AdminPlatformSettingsResponse;
import dev.gitnode.os.admin.dtos.StatsCacheTtlForm;
import dev.gitnode.os.admin.services.AdminPlatformSettingsService;
import dev.gitnode.os.admin.services.AdminStatsService;
import dev.gitnode.os.admin.services.PlatformAdminService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminPlatformSettingsController unit tests")
class AdminPlatformSettingsControllerTest {

  @Mock private AdminPlatformSettingsService adminPlatformSettingsService;
  @Mock private AdminStatsService adminStatsService;
  @Mock private PlatformAdminService platformAdminService;

  @InjectMocks private AdminPlatformSettingsController adminPlatformSettingsController;

  private static AdminPlatformSettingsResponse settings() {
    return new AdminPlatformSettingsResponse(300L, false, false);
  }

  @Nested
  @DisplayName("GET /api/admin/settings")
  class GetSettings {

    @Test
    @DisplayName("returns current platform settings")
    void getSettings_returnsResponse() {
      when(adminPlatformSettingsService.getSettings()).thenReturn(settings());

      var response = adminPlatformSettingsController.getSettings();

      assertThat(response.getBody()).isEqualTo(settings());
    }
  }

  @Nested
  @DisplayName("PUT /api/admin/settings/stats-cache")
  class UpdateStatsCache {

    @Test
    @DisplayName("updates ttl and evicts stats caches")
    void updateStatsCacheTtl_evictsCaches() {
      when(adminPlatformSettingsService.getSettings()).thenReturn(settings());

      var response =
          adminPlatformSettingsController.updateStatsCacheTtl(new StatsCacheTtlForm(600L));

      verify(adminPlatformSettingsService).updateStatsCacheTtlSeconds(600L);
      verify(adminStatsService).evictAllCaches();
      assertThat(response.getBody()).isEqualTo(settings());
    }
  }

  @Nested
  @DisplayName("PUT /api/admin/settings/feature-toggles")
  class UpdateFeatureToggles {

    @Test
    @DisplayName("delegates to settings service")
    void updateFeatureToggles_persistsToggles() {
      var toggles = new AdminFeatureTogglesForm(true, true);
      when(adminPlatformSettingsService.updateFeatureToggles(toggles)).thenReturn(settings());

      var response = adminPlatformSettingsController.updateFeatureToggles(toggles);

      assertThat(response.getBody()).isEqualTo(settings());
    }
  }

  @Nested
  @DisplayName("GET /api/admin/settings/platform-admins")
  class ListPlatformAdmins {

    @Test
    @DisplayName("returns platform admin metadata")
    void listPlatformAdmins_returnsMetadata() {
      when(platformAdminService.listPlatformAdminUsernames()).thenReturn(List.of("admin"));
      when(platformAdminService.bootstrapAdminUsername()).thenReturn("admin");
      when(platformAdminService.bootstrapAdminEnabled()).thenReturn(true);

      var response = adminPlatformSettingsController.listPlatformAdmins();

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().usernames()).containsExactly("admin");
      assertThat(response.getBody().bootstrapAdminUsername()).isEqualTo("admin");
      assertThat(response.getBody().bootstrapAdminEnabled()).isTrue();
    }
  }
}
