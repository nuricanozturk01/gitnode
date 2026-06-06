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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.admin.dtos.PgAuditLogSearchResponse;
import com.nuricanozturk.originhub.admin.dtos.PgAuditLogStatus;
import com.nuricanozturk.originhub.admin.dtos.PgAuditStatusReason;
import com.nuricanozturk.originhub.admin.services.PgAuditLogReaderService;
import com.nuricanozturk.originhub.admin.services.PlatformAdminService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminPgAuditLogController unit tests")
class AdminPgAuditLogControllerTest {

  @Mock private PgAuditLogReaderService pgAuditLogReaderService;
  @Mock private PlatformAdminService platformAdminService;

  @InjectMocks private AdminPgAuditLogController adminPgAuditLogController;

  @Test
  @DisplayName("returns pgAudit availability status")
  void status_returnsAvailability() {
    when(pgAuditLogReaderService.status())
        .thenReturn(
            new PgAuditLogStatus(
                true, true, PgAuditStatusReason.READY, "/var/log/postgresql", "ok"));

    var response = adminPgAuditLogController.status();

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().available()).isTrue();
    verify(platformAdminService).requirePlatformAdmin();
  }

  @Test
  @DisplayName("returns paginated pgAudit search results")
  void search_returnsPage() {
    when(pgAuditLogReaderService.search(0, 20, "accounts", "admin", "WRITE", null, null))
        .thenReturn(new PgAuditLogSearchResponse(List.of(), 0, 20, 0, 0, true, "ok"));

    var response =
        adminPgAuditLogController.search(0, 20, "accounts", "admin", "WRITE", null, null);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().available()).isTrue();
  }
}
