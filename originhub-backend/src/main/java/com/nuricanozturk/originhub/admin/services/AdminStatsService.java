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

import com.nuricanozturk.originhub.admin.dtos.AdminStatsOverview;
import com.nuricanozturk.originhub.admin.dtos.AdminStatsOverviewResponse;
import com.nuricanozturk.originhub.admin.dtos.PeriodStats;
import com.nuricanozturk.originhub.admin.dtos.RepoActivityResponse;
import com.nuricanozturk.originhub.admin.dtos.RepoActivityStat;
import com.nuricanozturk.originhub.admin.dtos.TimeSeriesPoint;
import com.nuricanozturk.originhub.admin.dtos.UploadActivityResponse;
import com.nuricanozturk.originhub.admin.dtos.UploadActivityStat;
import com.nuricanozturk.originhub.auth.api.OrganizationAdminPort;
import com.nuricanozturk.originhub.shared.cache.CacheNames;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.repo.services.RepoStorageService;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NullMarked
public class AdminStatsService {

  private static final int SEVEN = 7;

  private static final String STORAGE_NOTE =
      "Total storage is summed from on-disk bare repo folders; push/upload events are not tracked.";

  private static final String UPLOAD_NOTE =
      "Upload activity uses new repository creation as a proxy; estimated bytes come from on-disk repo size.";

  private final TenantRepository tenantRepository;
  private final RepoRepository repoRepository;
  private final RepoStorageService repoStorageService;
  private final AdminPlatformSettingsService adminPlatformSettingsService;
  private final OrganizationAdminPort organizationAdminPort;

  @Caching(
      evict = {
        @CacheEvict(cacheNames = CacheNames.ADMIN_STATS_OVERVIEW, allEntries = true),
        @CacheEvict(cacheNames = CacheNames.ADMIN_STATS_REPO_ACTIVITY, allEntries = true),
        @CacheEvict(cacheNames = CacheNames.ADMIN_STATS_UPLOAD_ACTIVITY, allEntries = true)
      })
  public void evictAllCaches() {}

  @Caching(
      cacheable =
          @Cacheable(
              cacheNames = CacheNames.ADMIN_STATS_OVERVIEW,
              key = "'all'",
              condition = "!#refresh"),
      put =
          @CachePut(
              cacheNames = CacheNames.ADMIN_STATS_OVERVIEW,
              key = "'all'",
              condition = "#refresh"))
  @Transactional(readOnly = true)
  public AdminStatsOverviewResponse getOverview(final boolean refresh) {

    final var now = Instant.now();
    final var cacheTtlSeconds = this.adminPlatformSettingsService.getStatsCacheTtlSeconds();
    final var overview = this.computeOverview(now);
    return new AdminStatsOverviewResponse(overview, now, cacheTtlSeconds, false);
  }

  @Caching(
      cacheable =
          @Cacheable(
              cacheNames = CacheNames.ADMIN_STATS_REPO_ACTIVITY,
              key =
                  "#period != null && 'day'.equals(#period.trim().toLowerCase()) ? 'day' : 'week'",
              condition = "!#refresh"),
      put =
          @CachePut(
              cacheNames = CacheNames.ADMIN_STATS_REPO_ACTIVITY,
              key =
                  "#period != null && 'day'.equals(#period.trim().toLowerCase()) ? 'day' : 'week'",
              condition = "#refresh"))
  @Transactional(readOnly = true)
  public RepoActivityResponse getRepoActivity(final String period, final boolean refresh) {

    final var normalizedPeriod = this.normalizePeriod(period);
    final var since = this.periodStart(normalizedPeriod);
    final var repos = this.repoRepository.findAllByCreatedAtAfter(since);

    final var byOwner =
        repos.stream()
            .collect(
                Collectors.groupingBy(repo -> repo.getOwner().getUsername(), Collectors.counting()))
            .entrySet()
            .stream()
            .map(entry -> new RepoActivityStat(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(RepoActivityStat::ownerUsername))
            .toList();

    return new RepoActivityResponse(
        new PeriodStats(normalizedPeriod, repos.size()),
        byOwner,
        this.buildRepoTimeSeries(repos, since));
  }

  @Caching(
      cacheable =
          @Cacheable(
              cacheNames = CacheNames.ADMIN_STATS_UPLOAD_ACTIVITY,
              key =
                  "#period != null && 'day'.equals(#period.trim().toLowerCase()) ? 'day' : 'week'",
              condition = "!#refresh"),
      put =
          @CachePut(
              cacheNames = CacheNames.ADMIN_STATS_UPLOAD_ACTIVITY,
              key =
                  "#period != null && 'day'.equals(#period.trim().toLowerCase()) ? 'day' : 'week'",
              condition = "#refresh"))
  @Transactional(readOnly = true)
  public UploadActivityResponse getUploadActivity(final String period, final boolean refresh) {

    final var normalizedPeriod = this.normalizePeriod(period);
    final var since = this.periodStart(normalizedPeriod);
    final var repos = this.repoRepository.findAllByCreatedAtAfter(since);

    final Map<String, UploadActivityStat> byOwner = new LinkedHashMap<>();

    for (final Repo repo : repos) {
      final var ownerUsername = repo.getOwner().getUsername();
      final var estimatedBytes =
          this.repoStorageService.calculateRepoStorageBytes(ownerUsername, repo.getName());
      final var existing = byOwner.get(ownerUsername);

      if (existing == null) {
        byOwner.put(ownerUsername, new UploadActivityStat(ownerUsername, 1L, estimatedBytes));
      } else {
        byOwner.put(
            ownerUsername,
            new UploadActivityStat(
                ownerUsername,
                existing.repoCount() + 1L,
                existing.estimatedBytes() + estimatedBytes));
      }
    }

    final var sortedByOwner =
        byOwner.values().stream()
            .sorted(Comparator.comparing(UploadActivityStat::ownerUsername))
            .toList();

    return new UploadActivityResponse(
        new PeriodStats(normalizedPeriod, repos.size()),
        sortedByOwner,
        this.buildRepoTimeSeries(repos, since),
        UPLOAD_NOTE);
  }

  private AdminStatsOverview computeOverview(final Instant now) {

    final var dayStart = now.minus(1, ChronoUnit.DAYS);
    final var weekStart = now.minus(7, ChronoUnit.DAYS);

    return new AdminStatsOverview(
        this.tenantRepository.count(),
        this.tenantRepository.countByEnabledTrue(),
        this.repoRepository.count(),
        this.repoStorageService.calculateTotalStorageBytes(),
        true,
        STORAGE_NOTE,
        this.tenantRepository.countByCreatedAtAfter(dayStart),
        this.tenantRepository.countByCreatedAtAfter(weekStart),
        this.repoRepository.countByCreatedAtAfter(dayStart),
        this.repoRepository.countByCreatedAtAfter(weekStart),
        this.organizationAdminPort.countOrganizations(),
        this.organizationAdminPort.countSsoEnabledOrganizations());
  }

  private String normalizePeriod(final @Nullable String period) {

    final var normalized = period == null ? "week" : period.trim().toLowerCase(Locale.getDefault());

    if (!normalized.equals("day") && !normalized.equals("week")) {
      return "week";
    }

    return normalized;
  }

  private Instant periodStart(final String period) {

    if ("day".equals(period)) {
      return Instant.now().minus(1, ChronoUnit.DAYS);
    }

    return Instant.now().minus(SEVEN, ChronoUnit.DAYS);
  }

  private List<TimeSeriesPoint> buildRepoTimeSeries(final List<Repo> repos, final Instant since) {

    final var bucketUnit = ChronoUnit.DAYS;
    final var buckets = this.initializeBuckets(since, bucketUnit);

    for (final Repo repo : repos) {
      if (repo.getCreatedAt() == null) {
        continue;
      }

      final var bucketStart = this.truncate(repo.getCreatedAt(), bucketUnit);
      buckets.merge(bucketStart, 1L, Long::sum);
    }

    return buckets.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> new TimeSeriesPoint(entry.getKey(), entry.getValue()))
        .toList();
  }

  private Map<Instant, Long> initializeBuckets(final Instant since, final ChronoUnit unit) {

    final var buckets = new LinkedHashMap<Instant, Long>();
    var cursor = this.truncate(since, unit);
    final var end = this.truncate(Instant.now(), unit).plus(1, unit);

    while (cursor.isBefore(end)) {
      buckets.put(cursor, 0L);
      cursor = cursor.plus(1, unit);
    }

    return buckets;
  }

  private Instant truncate(final Instant instant, final ChronoUnit unit) {

    return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC).truncatedTo(unit).toInstant();
  }
}
