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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.shared.audit.repositories.AuditLogRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAuditLogService unit tests")
class AdminAuditLogServiceTest {

  @Mock private AuditLogRepository auditLogRepository;

  @InjectMocks private AdminAuditLogService adminAuditLogService;

  @Test
  @DisplayName("search uses JPA Specification with normalized blank params")
  void search_usesSpecificationWithNormalizedParams() {
    when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    adminAuditLogService.search(PageRequest.of(0, 10), "  ", "  ", "  ", "  ", "  ", null, null);

    verify(auditLogRepository).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  @DisplayName("searchRecent caps lastHours at 720")
  void searchRecent_capsLastHours() {
    when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    adminAuditLogService.searchRecent(PageRequest.of(0, 10), 9999, null);

    verify(auditLogRepository).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  @DisplayName("listFilterOptions loads distinct actions and entity types")
  void listFilterOptions_returnsDistinctValues() {
    when(auditLogRepository.findDistinctActions(any(Pageable.class)))
        .thenReturn(List.of("CREATE_REPO"));
    when(auditLogRepository.findDistinctEntityTypes(any(Pageable.class)))
        .thenReturn(List.of("REPO"));

    var filters = adminAuditLogService.listFilterOptions();

    assertThat(filters.actions()).containsExactly("CREATE_REPO");
    assertThat(filters.entityTypes()).containsExactly("REPO");
  }

  @Test
  @DisplayName("capPageSize limits to 50")
  void capPageSize_limitsTo50() {
    assertThat(AdminAuditLogService.capPageSize(200)).isEqualTo(50);
    assertThat(AdminAuditLogService.capPageSize(10)).isEqualTo(10);
  }
}
