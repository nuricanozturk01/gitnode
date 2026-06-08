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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.admin.dtos.AdminUserDetail;
import dev.gitnode.os.admin.dtos.AdminUserSummary;
import dev.gitnode.os.admin.dtos.UserEnabledForm;
import dev.gitnode.os.admin.services.AdminUserService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserController unit tests")
class AdminUserControllerTest {

  @Mock private AdminUserService adminUserService;

  @InjectMocks private AdminUserController adminUserController;

  private static final UUID USER_ID = UUID.randomUUID();

  private static AdminUserSummary summary() {
    return new AdminUserSummary(USER_ID, "alice", "alice@example.com", true, Instant.EPOCH);
  }

  private static AdminUserDetail detail() {
    return new AdminUserDetail(
        USER_ID,
        "alice",
        "alice@example.com",
        true,
        "Alice",
        Instant.EPOCH,
        Instant.EPOCH,
        false,
        false);
  }

  @Nested
  @DisplayName("GET /api/admin/users")
  class ListUsers {

    @Test
    @DisplayName("returns paginated users with optional search")
    void list_returnsPage() {
      when(adminUserService.listUsers(any(Pageable.class), eq("ali")))
          .thenReturn(new PageImpl<>(List.of(summary())));

      var response = adminUserController.list(0, 10, "ali");

      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().content()).hasSize(1);
      verify(adminUserService)
          .listUsers(
              argThat(
                  pageable ->
                      pageable.getPageSize() == 10
                          && pageable.getSort().equals(Sort.by(Sort.Direction.DESC, "createdAt"))),
              eq("ali"));
    }

    @Test
    @DisplayName("caps page size at 500")
    void list_capsPageSize() {
      when(adminUserService.listUsers(any(Pageable.class), eq(null)))
          .thenReturn(new PageImpl<>(List.of()));

      adminUserController.list(0, 1000, null);

      verify(adminUserService)
          .listUsers(argThat(pageable -> pageable.getPageSize() == 500), eq(null));
    }
  }

  @Nested
  @DisplayName("GET /api/admin/users/{id}")
  class Get {

    @Test
    @DisplayName("returns user detail")
    void get_returnsDetail() {
      when(adminUserService.getUser(USER_ID)).thenReturn(detail());

      var response = adminUserController.get(USER_ID);

      assertThat(response.getBody()).isEqualTo(detail());
    }
  }

  @Nested
  @DisplayName("PUT /api/admin/users/{id}/enabled")
  class SetEnabled {

    @Test
    @DisplayName("updates enabled flag and returns summary")
    void setEnabled_returnsSummary() {
      var disabledDetail =
          new AdminUserDetail(
              USER_ID,
              "alice",
              "alice@example.com",
              false,
              "Alice",
              Instant.EPOCH,
              Instant.EPOCH,
              false,
              false);
      when(adminUserService.setEnabled(USER_ID, false)).thenReturn(disabledDetail);

      var response = adminUserController.setEnabled(USER_ID, new UserEnabledForm(false));

      verify(adminUserService).setEnabled(USER_ID, false);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().enabled()).isFalse();
      assertThat(response.getBody().username()).isEqualTo("alice");
    }
  }
}
