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
package com.nuricanozturk.originhub.pr.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.pr.entities.PullRequest;
import com.nuricanozturk.originhub.pr.repositories.PrRepository;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PrFinder unit tests")
class PrFinderTest {

  @Mock private RepoRepository repoRepository;
  @Mock private PrRepository prRepository;
  @Mock private TenantRepository tenantRepository;

  @InjectMocks private PrFinder prFinder;

  @Test
  @DisplayName("findRepo throws ItemNotFoundException when repository missing")
  void findRepo_throws_whenMissing() {
    when(repoRepository.findByOwnerUsernameAndName("alice", "missing"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> prFinder.findRepo("alice", "missing"))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("Repository not found");
  }

  @Test
  @DisplayName("findRepo returns repository when present")
  void findRepo_returnsRepo_whenPresent() {
    Repo repo = new Repo();
    repo.setId(UUID.randomUUID());
    when(repoRepository.findByOwnerUsernameAndName("alice", "demo")).thenReturn(Optional.of(repo));

    assertThat(prFinder.findRepo("alice", "demo")).isSameAs(repo);
  }

  @Test
  @DisplayName("findPr throws ItemNotFoundException when pull request missing")
  void findPr_throws_whenMissing() {
    UUID repoId = UUID.randomUUID();
    when(prRepository.findByRepoIdAndNumber(repoId, 99)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> prFinder.findPr(repoId, 99))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("Pull request not found: #99");
  }

  @Test
  @DisplayName("findPr returns pull request when present")
  void findPr_returnsPr_whenPresent() {
    UUID repoId = UUID.randomUUID();
    PullRequest pr = new PullRequest();
    pr.setNumber(1);
    when(prRepository.findByRepoIdAndNumber(repoId, 1)).thenReturn(Optional.of(pr));

    assertThat(prFinder.findPr(repoId, 1)).isSameAs(pr);
  }

  @Test
  @DisplayName("findTenant throws ItemNotFoundException when user missing")
  void findTenant_throws_whenMissing() {
    UUID userId = UUID.randomUUID();
    when(tenantRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> prFinder.findTenant(userId))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("User not found");
  }

  @Test
  @DisplayName("findTenant returns tenant when present")
  void findTenant_returnsTenant_whenPresent() {
    UUID userId = UUID.randomUUID();
    Tenant tenant = new Tenant();
    tenant.setId(userId);
    when(tenantRepository.findById(userId)).thenReturn(Optional.of(tenant));

    assertThat(prFinder.findTenant(userId)).isSameAs(tenant);
  }
}
