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

import dev.gitnode.os.admin.dtos.AdminFeatureTogglesForm;
import dev.gitnode.os.admin.dtos.AdminPlatformSettingsResponse;
import dev.gitnode.os.admin.entities.PlatformSetting;
import dev.gitnode.os.admin.repositories.PlatformSettingRepository;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NullMarked
public class AdminPlatformSettingsService {

  static final String STATS_CACHE_TTL_KEY = "stats_cache_ttl_seconds";
  static final String PGAUDIT_VIEWER_ENABLED_KEY = "pgaudit_viewer_enabled";
  static final String MODULITH_EVENTS_VIEWER_ENABLED_KEY = "modulith_events_viewer_enabled";

  private final PlatformSettingRepository platformSettingRepository;

  @Value("${gitnode.admin.stats.cache-ttl-seconds:300}")
  private long defaultStatsCacheTtlSeconds;

  @Value("${gitnode.admin.pgaudit.enabled:false}")
  private boolean defaultPgAuditViewerEnabled;

  @Value("${gitnode.admin.modulith-events.enabled:false}")
  private boolean defaultModulithEventsViewerEnabled;

  private final AtomicLong cachedStatsCacheTtlSeconds = new AtomicLong(-1L);
  private final AtomicReference<@Nullable Boolean> cachedPgAuditViewerEnabled =
      new AtomicReference<>();
  private final AtomicReference<@Nullable Boolean> cachedModulithEventsViewerEnabled =
      new AtomicReference<>();

  @Transactional(readOnly = true)
  public AdminPlatformSettingsResponse getSettings() {

    return new AdminPlatformSettingsResponse(
        this.getStatsCacheTtlSeconds(),
        this.isPgAuditViewerEnabled(),
        this.isModulithEventsViewerEnabled());
  }

  @Transactional(readOnly = true)
  public long getStatsCacheTtlSeconds() {

    final long cached = this.cachedStatsCacheTtlSeconds.get();
    if (cached >= 0L) {
      return cached;
    }

    final long ttl =
        this.platformSettingRepository
            .findById(STATS_CACHE_TTL_KEY)
            .map(PlatformSetting::getSettingValue)
            .flatMap(this::parsePositiveLong)
            .orElse(this.defaultStatsCacheTtlSeconds);

    this.cachedStatsCacheTtlSeconds.set(ttl);
    return ttl;
  }

  public boolean isPgAuditViewerEnabled() {

    final Boolean cached = this.cachedPgAuditViewerEnabled.get();
    if (cached != null) {
      return cached;
    }

    final var enabled =
        this.readBooleanSetting(PGAUDIT_VIEWER_ENABLED_KEY, this.defaultPgAuditViewerEnabled);
    this.cachedPgAuditViewerEnabled.set(enabled);
    return enabled;
  }

  public boolean isModulithEventsViewerEnabled() {

    final Boolean cached = this.cachedModulithEventsViewerEnabled.get();
    if (cached != null) {
      return cached;
    }

    final var enabled =
        this.readBooleanSetting(
            MODULITH_EVENTS_VIEWER_ENABLED_KEY, this.defaultModulithEventsViewerEnabled);
    this.cachedModulithEventsViewerEnabled.set(enabled);
    return enabled;
  }

  @Transactional
  public long updateStatsCacheTtlSeconds(final long statsCacheTtlSeconds) {

    this.persistSetting(STATS_CACHE_TTL_KEY, Long.toString(statsCacheTtlSeconds));
    this.cachedStatsCacheTtlSeconds.set(statsCacheTtlSeconds);
    return statsCacheTtlSeconds;
  }

  @Transactional
  public AdminPlatformSettingsResponse updateFeatureToggles(final AdminFeatureTogglesForm form) {

    this.persistSetting(PGAUDIT_VIEWER_ENABLED_KEY, Boolean.toString(form.pgAuditViewerEnabled()));
    this.persistSetting(
        MODULITH_EVENTS_VIEWER_ENABLED_KEY, Boolean.toString(form.modulithEventsViewerEnabled()));
    this.cachedPgAuditViewerEnabled.set(form.pgAuditViewerEnabled());
    this.cachedModulithEventsViewerEnabled.set(form.modulithEventsViewerEnabled());
    return this.getSettings();
  }

  public void invalidateCache() {

    this.cachedStatsCacheTtlSeconds.set(-1L);
    this.cachedPgAuditViewerEnabled.set(null);
    this.cachedModulithEventsViewerEnabled.set(null);
  }

  private void persistSetting(final String key, final String value) {

    final var setting =
        this.platformSettingRepository
            .findById(key)
            .orElseGet(
                () -> {
                  final var created = new PlatformSetting();
                  created.setSettingKey(key);
                  return created;
                });

    setting.setSettingValue(value);
    this.platformSettingRepository.save(setting);
  }

  private boolean readBooleanSetting(final String key, final boolean defaultValue) {

    return this.platformSettingRepository
        .findById(key)
        .map(PlatformSetting::getSettingValue)
        .map(this::parseBoolean)
        .orElse(defaultValue);
  }

  private boolean parseBoolean(final String value) {

    return Boolean.parseBoolean(value.trim());
  }

  private java.util.Optional<Long> parsePositiveLong(final String value) {

    try {
      final var parsed = Long.parseLong(value.trim());

      if (parsed <= 0L) {
        return java.util.Optional.empty();
      }

      return java.util.Optional.of(parsed);
    } catch (final NumberFormatException ignored) {
      return java.util.Optional.empty();
    }
  }
}
