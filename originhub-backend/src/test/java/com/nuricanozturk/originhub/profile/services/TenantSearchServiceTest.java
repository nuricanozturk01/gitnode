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
package com.nuricanozturk.originhub.profile.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.profile.dtos.TenantSearchResult;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantSearchService unit tests")
class TenantSearchServiceTest {

  @Mock private TenantRepository tenantRepository;

  @InjectMocks private TenantSearchService tenantSearchService;

  @Test
  @DisplayName("search returns empty list when repository returns no tenants")
  void search_returnsEmptyList_whenNoTenants() {
    when(tenantRepository.findTop10ByUsernameIgnoreCaseStartingWithOrderByUsernameAsc(anyString()))
        .thenReturn(List.of());

    List<TenantSearchResult> result = tenantSearchService.search("foo");

    assertThat(result).isEmpty();
    verify(tenantRepository).findTop10ByUsernameIgnoreCaseStartingWithOrderByUsernameAsc("foo");
  }

  @Test
  @DisplayName("search returns results with username and displayName from tenant")
  void search_returnsMappedResults_whenTenantsExist() {
    Tenant t1 = new Tenant();
    t1.setUsername("alice");
    t1.setDisplayName("Alice Doe");
    t1.setAvatarUrl("https://avatar/alice");
    Tenant t2 = new Tenant();
    t2.setUsername("bob");
    t2.setDisplayName(null);
    t2.setAvatarUrl(null);

    when(tenantRepository.findTop10ByUsernameIgnoreCaseStartingWithOrderByUsernameAsc("al"))
        .thenReturn(List.of(t1, t2));

    List<TenantSearchResult> result = tenantSearchService.search("al");

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getUsername()).isEqualTo("alice");
    assertThat(result.get(0).getDisplayName()).isEqualTo("Alice Doe");
    assertThat(result.get(0).getAvatarUrl()).isEqualTo("https://avatar/alice");
    assertThat(result.get(1).getUsername()).isEqualTo("bob");
    assertThat(result.get(1).getDisplayName()).isEqualTo("bob");
    assertThat(result.get(1).getAvatarUrl()).isNull();
  }

  @Test
  @DisplayName("search delegates prefix to repository unchanged")
  void search_delegatesPrefixToRepository() {
    when(tenantRepository.findTop10ByUsernameIgnoreCaseStartingWithOrderByUsernameAsc("x"))
        .thenReturn(List.of());

    tenantSearchService.search("x");

    verify(tenantRepository).findTop10ByUsernameIgnoreCaseStartingWithOrderByUsernameAsc("x");
  }
}
