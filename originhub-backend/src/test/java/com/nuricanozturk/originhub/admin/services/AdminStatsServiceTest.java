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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.admin.dtos.RepoActivityStat;
import com.nuricanozturk.originhub.auth.api.OrganizationAdminPort;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.repo.services.RepoStorageService;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminStatsService unit tests")
class AdminStatsServiceTest {

  @Mock private TenantRepository tenantRepository;
  @Mock private RepoRepository repoRepository;
  @Mock private RepoStorageService repoStorageService;
  @Mock private OrganizationAdminPort organizationAdminPort;
  @Mock private AdminPlatformSettingsService adminPlatformSettingsService;

  @InjectMocks private AdminStatsService adminStatsService;

  @BeforeEach
  void setUp() {

    lenient().when(adminPlatformSettingsService.getStatsCacheTtlSeconds()).thenReturn(300L);
  }

  @Test
  @DisplayName("getOverview aggregates totals and recent counts")
  void getOverview_returnsAggregatedTotals() {

    when(tenantRepository.count()).thenReturn(10L);
    when(tenantRepository.countByEnabledTrue()).thenReturn(9L);
    when(repoRepository.count()).thenReturn(4L);
    when(repoStorageService.calculateTotalStorageBytes()).thenReturn(1024L);
    when(tenantRepository.countByCreatedAtAfter(org.mockito.ArgumentMatchers.any(Instant.class)))
        .thenReturn(2L, 5L);
    when(repoRepository.countByCreatedAtAfter(org.mockito.ArgumentMatchers.any(Instant.class)))
        .thenReturn(1L, 3L);
    when(organizationAdminPort.countOrganizations()).thenReturn(2L);
    when(organizationAdminPort.countSsoEnabledOrganizations()).thenReturn(1L);

    final var response = adminStatsService.getOverview(false);
    final var overview = response.overview();

    assertThat(response.cacheTtlSeconds()).isEqualTo(300L);
    assertThat(response.fromCache()).isFalse();
    assertThat(overview.totalUsers()).isEqualTo(10L);
    assertThat(overview.enabledUsers()).isEqualTo(9L);
    assertThat(overview.totalRepos()).isEqualTo(4L);
    assertThat(overview.totalStorageBytes()).isEqualTo(1024L);
    assertThat(overview.newUsersToday()).isEqualTo(2L);
    assertThat(overview.newUsersThisWeek()).isEqualTo(5L);
    assertThat(overview.newReposToday()).isEqualTo(1L);
    assertThat(overview.newReposThisWeek()).isEqualTo(3L);
    assertThat(overview.totalOrganizations()).isEqualTo(2L);
    assertThat(overview.ssoEnabledOrganizations()).isEqualTo(1L);
  }

  @Test
  @DisplayName("getOverview returns fromCache=false (caching delegated to Spring Redis proxy)")
  void getOverview_alwaysReturnsFreshFlag() {

    when(tenantRepository.count()).thenReturn(10L);
    when(tenantRepository.countByEnabledTrue()).thenReturn(9L);
    when(repoRepository.count()).thenReturn(4L);
    when(repoStorageService.calculateTotalStorageBytes()).thenReturn(1024L);
    when(tenantRepository.countByCreatedAtAfter(org.mockito.ArgumentMatchers.any(Instant.class)))
        .thenReturn(0L);
    when(repoRepository.countByCreatedAtAfter(org.mockito.ArgumentMatchers.any(Instant.class)))
        .thenReturn(0L);
    when(organizationAdminPort.countOrganizations()).thenReturn(0L);
    when(organizationAdminPort.countSsoEnabledOrganizations()).thenReturn(0L);

    assertThat(adminStatsService.getOverview(false).fromCache()).isFalse();
    assertThat(adminStatsService.getOverview(true).fromCache()).isFalse();
  }

  @Test
  @DisplayName("getRepoActivity groups repos by owner for period")
  void getRepoActivity_groupsByOwner() {

    final var owner = new Tenant();
    owner.setUsername("alice");

    final var repo = new Repo();
    repo.setOwner(owner);
    repo.setName("demo");
    repo.setCreatedAt(Instant.now().minus(2, ChronoUnit.HOURS));

    when(repoRepository.findAllByCreatedAtAfter(org.mockito.ArgumentMatchers.any(Instant.class)))
        .thenReturn(List.of(repo));

    final var response = adminStatsService.getRepoActivity("day", false);

    assertThat(response.summary().period()).isEqualTo("day");
    assertThat(response.summary().totalCount()).isEqualTo(1L);
    assertThat(response.byOwner()).containsExactly(new RepoActivityStat("alice", 1L));
    assertThat(response.timeSeries()).isNotEmpty();
  }

  @Test
  @DisplayName("getUploadActivity aggregates storage bytes by owner")
  void getUploadActivity_aggregatesByOwner() {
    final var owner = new Tenant();
    owner.setUsername("alice");

    final var repo = new Repo();
    repo.setOwner(owner);
    repo.setName("demo");
    repo.setCreatedAt(Instant.now().minus(2, ChronoUnit.HOURS));

    when(repoRepository.findAllByCreatedAtAfter(org.mockito.ArgumentMatchers.any(Instant.class)))
        .thenReturn(List.of(repo));
    when(repoStorageService.calculateRepoStorageBytes("alice", "demo")).thenReturn(2048L);

    final var response = adminStatsService.getUploadActivity("week", false);

    assertThat(response.summary().period()).isEqualTo("week");
    assertThat(response.summary().totalCount()).isEqualTo(1L);
    assertThat(response.byOwner()).hasSize(1);
    assertThat(response.byOwner().get(0).ownerUsername()).isEqualTo("alice");
    assertThat(response.byOwner().get(0).estimatedBytes()).isEqualTo(2048L);
  }

  @Test
  @DisplayName("getRepoActivity normalizes unknown period to week")
  void getRepoActivity_normalizesUnknownPeriod() {
    when(repoRepository.findAllByCreatedAtAfter(org.mockito.ArgumentMatchers.any(Instant.class)))
        .thenReturn(List.of());

    final var response = adminStatsService.getRepoActivity("month", false);

    assertThat(response.summary().period()).isEqualTo("week");
  }

  @Test
  @DisplayName("evictAllCaches completes without error")
  void evictAllCaches_completesWithoutError() {
    adminStatsService.evictAllCaches();
  }
}
