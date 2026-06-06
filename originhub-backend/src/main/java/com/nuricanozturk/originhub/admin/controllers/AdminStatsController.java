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

import com.nuricanozturk.originhub.admin.dtos.AdminStatsOverviewResponse;
import com.nuricanozturk.originhub.admin.dtos.StatsActivityPoint;
import com.nuricanozturk.originhub.admin.dtos.StatsContributor;
import com.nuricanozturk.originhub.admin.dtos.StatsReposResponse;
import com.nuricanozturk.originhub.admin.dtos.StatsUploadsResponse;
import com.nuricanozturk.originhub.admin.dtos.TimeSeriesPoint;
import com.nuricanozturk.originhub.admin.services.AdminStatsService;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
@NullMarked
@PreAuthorize("@platformAdminService.isCurrentUserPlatformAdmin()")
public class AdminStatsController {

  private final AdminStatsService adminStatsService;

  @GetMapping("/overview")
  public ResponseEntity<AdminStatsOverviewResponse> overview(
      @RequestParam(defaultValue = "false") final boolean refresh) {

    return ResponseEntity.ok(this.adminStatsService.getOverview(refresh));
  }

  @GetMapping("/repos")
  public ResponseEntity<StatsReposResponse> repos(
      @RequestParam(defaultValue = "week") final String period,
      @RequestParam(defaultValue = "false") final boolean refresh) {

    final var activity = this.adminStatsService.getRepoActivity(period, refresh);

    return ResponseEntity.ok(
        new StatsReposResponse(
            activity.summary().period(),
            activity.byOwner().stream()
                .map(stat -> new StatsContributor(stat.ownerUsername(), stat.repoCount()))
                .sorted(Comparator.comparingLong(StatsContributor::count).reversed())
                .toList(),
            this.toRepoActivity(activity.timeSeries())));
  }

  @GetMapping("/uploads")
  public ResponseEntity<StatsUploadsResponse> uploads(
      @RequestParam(defaultValue = "week") final String period,
      @RequestParam(defaultValue = "false") final boolean refresh) {

    final var activity = this.adminStatsService.getUploadActivity(period, refresh);

    return ResponseEntity.ok(
        new StatsUploadsResponse(
            activity.summary().period(), this.toUploadActivity(activity.timeSeries())));
  }

  private List<StatsActivityPoint> toRepoActivity(final List<TimeSeriesPoint> timeSeries) {

    return timeSeries.stream()
        .map(
            point ->
                new StatsActivityPoint(
                    this.formatBucketDate(point.bucketStart()), point.count(), 0L))
        .toList();
  }

  private List<StatsActivityPoint> toUploadActivity(final List<TimeSeriesPoint> timeSeries) {

    return timeSeries.stream()
        .map(
            point ->
                new StatsActivityPoint(
                    this.formatBucketDate(point.bucketStart()), 0L, point.count()))
        .toList();
  }

  private String formatBucketDate(final java.time.Instant bucketStart) {

    return bucketStart.atZone(ZoneOffset.UTC).toLocalDate().toString();
  }
}
