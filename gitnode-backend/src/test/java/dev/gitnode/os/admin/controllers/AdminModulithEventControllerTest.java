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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.admin.dtos.ModulithEventDetail;
import dev.gitnode.os.admin.dtos.ModulithEventFilters;
import dev.gitnode.os.admin.dtos.ModulithEventLifecycleFilter;
import dev.gitnode.os.admin.dtos.ModulithEventSearchResponse;
import dev.gitnode.os.admin.services.AdminModulithEventService;
import dev.gitnode.os.admin.services.PlatformAdminService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminModulithEventController unit tests")
class AdminModulithEventControllerTest {

  @Mock private AdminModulithEventService adminModulithEventService;
  @Mock private PlatformAdminService platformAdminService;

  @InjectMocks private AdminModulithEventController adminModulithEventController;

  @Nested
  @DisplayName("GET /api/admin/modulith-events/status")
  class Status {

    @Test
    @DisplayName("returns enabled message when viewer is available")
    void status_returnsEnabledMessage() {
      when(adminModulithEventService.isAvailable()).thenReturn(true);

      var response = adminModulithEventController.status();

      verify(platformAdminService).requirePlatformAdmin();
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().available()).isTrue();
      assertThat(response.getBody().message()).contains("enabled");
    }

    @Test
    @DisplayName("returns disabled message when viewer is off")
    void status_returnsDisabledMessage() {
      when(adminModulithEventService.isAvailable()).thenReturn(false);

      var response = adminModulithEventController.status();

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().available()).isFalse();
      assertThat(response.getBody().message())
          .isEqualTo(AdminModulithEventService.DISABLED_MESSAGE);
    }
  }

  @Nested
  @DisplayName("GET /api/admin/modulith-events")
  class Search {

    @Test
    @DisplayName("delegates search to service")
    void search_delegatesToService() {
      var searchResponse = new ModulithEventSearchResponse(List.of(), 0, 20, 0, 0, true, "ok");
      when(adminModulithEventService.search(
              any(Pageable.class),
              eq("repo"),
              isNull(),
              isNull(),
              isNull(),
              eq(ModulithEventLifecycleFilter.ALL),
              isNull(),
              isNull()))
          .thenReturn(searchResponse);

      var response =
          adminModulithEventController.search(
              0, 20, "repo", null, null, null, ModulithEventLifecycleFilter.ALL, null, null);

      verify(platformAdminService).requirePlatformAdmin();
      assertThat(response.getBody()).isSameAs(searchResponse);
    }
  }

  @Nested
  @DisplayName("GET /api/admin/modulith-events/filters")
  class Filters {

    @Test
    @DisplayName("returns filter options")
    void filters_returnsOptions() {
      var filters =
          new ModulithEventFilters(
              List.of("IssueCreatedEvent"), List.of("listener"), List.of("COMPLETED"), false);
      when(adminModulithEventService.listFilterOptions()).thenReturn(filters);

      var response = adminModulithEventController.filters();

      assertThat(response.getBody()).isSameAs(filters);
    }
  }

  @Nested
  @DisplayName("GET /api/admin/modulith-events/{id}")
  class Detail {

    @Test
    @DisplayName("returns event detail")
    void detail_returnsEvent() {
      var id = UUID.randomUUID();
      var detail =
          new ModulithEventDetail(
              id,
              "listener",
              "IssueCreatedEvent",
              "{}",
              Instant.EPOCH,
              null,
              "PROCESSING",
              0,
              null);
      when(adminModulithEventService.getDetail(id)).thenReturn(detail);

      var response = adminModulithEventController.detail(id);

      assertThat(response.getBody()).isSameAs(detail);
    }
  }
}
