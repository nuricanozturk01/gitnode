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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.admin.dtos.AdminAuditLogFilters;
import dev.gitnode.os.admin.services.AdminAuditLogService;
import dev.gitnode.os.admin.services.PlatformAdminService;
import dev.gitnode.os.shared.audit.entities.AuditLog;
import dev.gitnode.os.shared.repo.dtos.PageResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAuditLogController unit tests")
class AdminAuditLogControllerTest {

  @Mock private AdminAuditLogService adminAuditLogService;
  @Mock private PlatformAdminService platformAdminService;

  @InjectMocks private AdminAuditLogController adminAuditLogController;

  private static AuditLog entry(String actor, String action) {
    AuditLog log = new AuditLog();
    log.setActorUsername(actor);
    log.setAction(action);
    return log;
  }

  @Nested
  @DisplayName("GET /api/admin/audit-logs")
  class Search {

    @Test
    @DisplayName("returns HTTP 200 with PageResponse of audit logs")
    void search_returnsPageResponse() {
      var page = new PageImpl<>(List.of(entry("alice", "CREATE_REPO")));
      when(adminAuditLogService.search(
              any(Pageable.class),
              eq("repo"),
              eq("alice"),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull()))
          .thenReturn(page);

      var response =
          adminAuditLogController.search(
              0, 20, "repo", "alice", null, null, null, null, null, null);

      assertThat(response.getStatusCode().value()).isEqualTo(200);
      PageResponse<AuditLog> body = response.getBody();
      assertThat(body).isNotNull();
      assertThat(body.content()).hasSize(1);
      verify(platformAdminService).requirePlatformAdmin();
    }

    @Test
    @DisplayName("caps page size at 50 when larger value requested")
    void search_capsPageSize_at50() {
      when(adminAuditLogService.search(
              any(Pageable.class),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull()))
          .thenReturn(new PageImpl<>(List.of()));

      adminAuditLogController.search(0, 200, null, null, null, null, null, null, null, null);

      verify(adminAuditLogService)
          .search(
              argThat((Pageable p) -> p.getPageSize() == 50),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull());
    }
  }

  @Nested
  @DisplayName("GET /api/admin/audit-logs/filters")
  class Filters {

    @Test
    @DisplayName("returns distinct actions and entity types")
    void filters_returnsOptions() {
      when(adminAuditLogService.listFilterOptions())
          .thenReturn(new AdminAuditLogFilters(List.of("CREATE_REPO"), List.of("REPO")));

      var response = adminAuditLogController.filters();

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().actions()).containsExactly("CREATE_REPO");
      assertThat(response.getBody().entityTypes()).containsExactly("REPO");
    }
  }

  @Nested
  @DisplayName("GET /api/admin/audit-logs/by-actor")
  class GetByActor {

    @Test
    @DisplayName("returns page filtered by actor username")
    void getByActor_returnsFilteredPage() {
      var page = new PageImpl<>(List.of(entry("bob", "DELETE_REPO")));
      when(adminAuditLogService.search(
              any(Pageable.class),
              isNull(),
              eq("bob"),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull()))
          .thenReturn(page);

      var response = adminAuditLogController.getByActor("bob", 0, 10);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().content().get(0).getActorUsername()).isEqualTo("bob");
    }
  }

  @Nested
  @DisplayName("GET /api/admin/audit-logs/recent")
  class GetRecent {

    @Test
    @DisplayName("returns page of recent entries within requested hours")
    void getRecent_returnsPage_withinRequestedHours() {
      var page = new PageImpl<>(List.of(entry("alice", "MERGE_PR")));
      when(adminAuditLogService.searchRecent(any(Pageable.class), eq(24), isNull()))
          .thenReturn(page);

      var response = adminAuditLogController.getRecent(24, 0, 10, null);

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().content()).hasSize(1);
    }
  }
}
