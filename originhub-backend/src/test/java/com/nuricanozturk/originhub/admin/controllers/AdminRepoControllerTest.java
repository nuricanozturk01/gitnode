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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.admin.dtos.AdminRepoSummary;
import com.nuricanozturk.originhub.admin.services.AdminRepoService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminRepoController unit tests")
class AdminRepoControllerTest {

  @Mock private AdminRepoService adminRepoService;

  @InjectMocks private AdminRepoController adminRepoController;

  @Test
  @DisplayName("GET /api/admin/repos returns paginated repos with filters")
  void list_returnsPage() {
    var summary =
        new AdminRepoSummary(
            UUID.randomUUID(),
            "alice",
            "demo",
            "alice/demo",
            false,
            false,
            "main",
            null,
            Instant.EPOCH,
            Instant.EPOCH);
    when(adminRepoService.listRepos(any(Pageable.class), eq("demo"), eq("alice")))
        .thenReturn(new PageImpl<>(List.of(summary)));

    var response = adminRepoController.list(0, 20, "demo", "alice");

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().content()).hasSize(1);
    assertThat(response.getBody().content().get(0).fullName()).isEqualTo("alice/demo");
    verify(adminRepoService)
        .listRepos(
            argThat(
                pageable ->
                    pageable.getPageSize() == 20
                        && pageable.getSort().equals(Sort.by(Sort.Direction.DESC, "createdAt"))),
            eq("demo"),
            eq("alice"));
  }

  @Test
  @DisplayName("caps page size at 100")
  void list_capsPageSize() {
    when(adminRepoService.listRepos(any(Pageable.class), eq(null), eq(null)))
        .thenReturn(new PageImpl<>(List.of()));

    adminRepoController.list(0, 500, null, null);

    verify(adminRepoService)
        .listRepos(argThat(pageable -> pageable.getPageSize() == 100), eq(null), eq(null));
  }
}
