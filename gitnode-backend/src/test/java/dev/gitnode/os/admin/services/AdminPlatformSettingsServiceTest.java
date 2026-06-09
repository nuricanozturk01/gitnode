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
package dev.gitnode.os.admin.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.admin.dtos.AdminFeatureTogglesForm;
import dev.gitnode.os.admin.entities.PlatformSetting;
import dev.gitnode.os.admin.repositories.PlatformSettingRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminPlatformSettingsService unit tests")
class AdminPlatformSettingsServiceTest {

  @Mock private PlatformSettingRepository platformSettingRepository;

  @InjectMocks private AdminPlatformSettingsService adminPlatformSettingsService;

  @BeforeEach
  void setUp() {

    ReflectionTestUtils.setField(adminPlatformSettingsService, "defaultStatsCacheTtlSeconds", 300L);
    ReflectionTestUtils.setField(
        adminPlatformSettingsService, "defaultPgAuditViewerEnabled", false);
    ReflectionTestUtils.setField(
        adminPlatformSettingsService, "defaultModulithEventsViewerEnabled", false);
    adminPlatformSettingsService.invalidateCache();
  }

  @Test
  @DisplayName("getStatsCacheTtlSeconds reads persisted value")
  void getStatsCacheTtlSeconds_readsFromDatabase() {

    final var setting = new PlatformSetting();
    setting.setSettingKey(AdminPlatformSettingsService.STATS_CACHE_TTL_KEY);
    setting.setSettingValue("600");

    when(platformSettingRepository.findById(AdminPlatformSettingsService.STATS_CACHE_TTL_KEY))
        .thenReturn(Optional.of(setting));

    assertThat(adminPlatformSettingsService.getStatsCacheTtlSeconds()).isEqualTo(600L);
  }

  @Test
  @DisplayName("updateStatsCacheTtlSeconds persists new value")
  void updateStatsCacheTtlSeconds_persistsValue() {

    when(platformSettingRepository.findById(AdminPlatformSettingsService.STATS_CACHE_TTL_KEY))
        .thenReturn(Optional.empty());
    when(platformSettingRepository.save(any(PlatformSetting.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    final var ttl = adminPlatformSettingsService.updateStatsCacheTtlSeconds(900L);

    assertThat(ttl).isEqualTo(900L);
    assertThat(adminPlatformSettingsService.getStatsCacheTtlSeconds()).isEqualTo(900L);

    final var captor = ArgumentCaptor.forClass(PlatformSetting.class);
    verify(platformSettingRepository).save(captor.capture());
    assertThat(captor.getValue().getSettingValue()).isEqualTo("900");
  }

  @Test
  @DisplayName("updateFeatureToggles persists pgAudit and modulith viewer flags")
  void updateFeatureToggles_persistsBooleanSettings() {
    when(platformSettingRepository.findById(
            AdminPlatformSettingsService.PGAUDIT_VIEWER_ENABLED_KEY))
        .thenReturn(java.util.Optional.empty());
    when(platformSettingRepository.findById(
            AdminPlatformSettingsService.MODULITH_EVENTS_VIEWER_ENABLED_KEY))
        .thenReturn(java.util.Optional.empty());
    when(platformSettingRepository.save(org.mockito.ArgumentMatchers.any(PlatformSetting.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        adminPlatformSettingsService.updateFeatureToggles(new AdminFeatureTogglesForm(true, false));

    assertThat(response.pgAuditViewerEnabled()).isTrue();
    assertThat(response.modulithEventsViewerEnabled()).isFalse();
    assertThat(adminPlatformSettingsService.isPgAuditViewerEnabled()).isTrue();
    assertThat(adminPlatformSettingsService.isModulithEventsViewerEnabled()).isFalse();
  }

  @Test
  @DisplayName("getSettings aggregates all platform settings")
  void getSettings_returnsCombinedResponse() {
    when(platformSettingRepository.findById(AdminPlatformSettingsService.STATS_CACHE_TTL_KEY))
        .thenReturn(Optional.empty());

    var response = adminPlatformSettingsService.getSettings();

    assertThat(response.statsCacheTtlSeconds()).isEqualTo(300L);
    assertThat(response.pgAuditViewerEnabled()).isFalse();
    assertThat(response.modulithEventsViewerEnabled()).isFalse();
  }

  @Test
  @DisplayName("getStatsCacheTtlSeconds falls back to default for invalid persisted value")
  void getStatsCacheTtlSeconds_usesDefault_whenInvalid() {
    var setting = new PlatformSetting();
    setting.setSettingKey(AdminPlatformSettingsService.STATS_CACHE_TTL_KEY);
    setting.setSettingValue("not-a-number");
    when(platformSettingRepository.findById(AdminPlatformSettingsService.STATS_CACHE_TTL_KEY))
        .thenReturn(Optional.of(setting));

    assertThat(adminPlatformSettingsService.getStatsCacheTtlSeconds()).isEqualTo(300L);
  }

  @Test
  @DisplayName("isPgAuditViewerEnabled reads persisted boolean")
  void isPgAuditViewerEnabled_readsFromDatabase() {
    var setting = new PlatformSetting();
    setting.setSettingKey(AdminPlatformSettingsService.PGAUDIT_VIEWER_ENABLED_KEY);
    setting.setSettingValue("true");
    when(platformSettingRepository.findById(
            AdminPlatformSettingsService.PGAUDIT_VIEWER_ENABLED_KEY))
        .thenReturn(Optional.of(setting));

    assertThat(adminPlatformSettingsService.isPgAuditViewerEnabled()).isTrue();
  }
}
