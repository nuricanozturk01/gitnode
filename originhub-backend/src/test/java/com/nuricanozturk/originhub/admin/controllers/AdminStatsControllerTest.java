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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.admin.dtos.AdminStatsOverview;
import com.nuricanozturk.originhub.admin.dtos.AdminStatsOverviewResponse;
import com.nuricanozturk.originhub.admin.dtos.PeriodStats;
import com.nuricanozturk.originhub.admin.dtos.RepoActivityResponse;
import com.nuricanozturk.originhub.admin.dtos.RepoActivityStat;
import com.nuricanozturk.originhub.admin.dtos.TimeSeriesPoint;
import com.nuricanozturk.originhub.admin.dtos.UploadActivityResponse;
import com.nuricanozturk.originhub.admin.dtos.UploadActivityStat;
import com.nuricanozturk.originhub.admin.services.AdminStatsService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminStatsController unit tests")
class AdminStatsControllerTest {

  @Mock private AdminStatsService adminStatsService;

  @InjectMocks private AdminStatsController adminStatsController;

  private static AdminStatsOverviewResponse overviewResponse() {
    var overview = new AdminStatsOverview(10L, 9L, 4L, 1024L, true, "note", 1L, 2L, 1L, 3L, 2L, 1L);
    return new AdminStatsOverviewResponse(overview, Instant.EPOCH, 300L, false);
  }

  @Nested
  @DisplayName("GET /api/admin/stats/overview")
  class Overview {

    @Test
    @DisplayName("returns overview from service")
    void overview_returnsResponse() {
      when(adminStatsService.getOverview(false)).thenReturn(overviewResponse());

      var response = adminStatsController.overview(false);

      assertThat(response.getBody()).isEqualTo(overviewResponse());
    }

    @Test
    @DisplayName("passes refresh flag to service")
    void overview_passesRefreshFlag() {
      when(adminStatsService.getOverview(true)).thenReturn(overviewResponse());

      adminStatsController.overview(true);

      org.mockito.Mockito.verify(adminStatsService).getOverview(true);
    }
  }

  @Nested
  @DisplayName("GET /api/admin/stats/repos")
  class Repos {

    @Test
    @DisplayName("maps repo activity to response DTO")
    void repos_mapsActivity() {
      var bucket = Instant.parse("2026-06-01T00:00:00Z");
      var activity =
          new RepoActivityResponse(
              new PeriodStats("week", 2L),
              List.of(new RepoActivityStat("alice", 2L), new RepoActivityStat("bob", 1L)),
              List.of(new TimeSeriesPoint(bucket, 3L)));
      when(adminStatsService.getRepoActivity("week", false)).thenReturn(activity);

      var response = adminStatsController.repos("week", false);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().period()).isEqualTo("week");
      assertThat(response.getBody().contributors()).hasSize(2);
      assertThat(response.getBody().contributors().get(0).username()).isEqualTo("alice");
      assertThat(response.getBody().contributors().get(0).count()).isEqualTo(2L);
      assertThat(response.getBody().activity()).hasSize(1);
      assertThat(response.getBody().activity().get(0).repos()).isEqualTo(3L);
      assertThat(response.getBody().activity().get(0).uploads()).isZero();
    }
  }

  @Nested
  @DisplayName("GET /api/admin/stats/uploads")
  class Uploads {

    @Test
    @DisplayName("maps upload activity to response DTO")
    void uploads_mapsActivity() {
      var bucket = Instant.parse("2026-06-01T00:00:00Z");
      var activity =
          new UploadActivityResponse(
              new PeriodStats("day", 1L),
              List.of(new UploadActivityStat("alice", 1L, 512L)),
              List.of(new TimeSeriesPoint(bucket, 1L)),
              "note");
      when(adminStatsService.getUploadActivity("day", false)).thenReturn(activity);

      var response = adminStatsController.uploads("day", false);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().period()).isEqualTo("day");
      assertThat(response.getBody().activity()).hasSize(1);
      assertThat(response.getBody().activity().get(0).repos()).isZero();
      assertThat(response.getBody().activity().get(0).uploads()).isEqualTo(1L);
    }
  }
}
