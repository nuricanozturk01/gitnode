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
package dev.gitnode.os.admin.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.shared.repo.entities.Repo;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import dev.gitnode.os.shared.tenant.entities.Tenant;
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
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminRepoService unit tests")
class AdminRepoServiceTest {

  @Mock private RepoRepository repoRepository;

  @InjectMocks private AdminRepoService adminRepoService;

  @Test
  @DisplayName("lists all repos when query and owner are blank")
  void listRepos_usesFindAll_whenNoFilters() {
    var pageable = PageRequest.of(0, 20);
    var repo = sampleRepo("alice", "demo");
    when(repoRepository.findAllWithOwner(pageable)).thenReturn(new PageImpl<>(List.of(repo)));

    var page = adminRepoService.listRepos(pageable, "  ", null);

    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getContent().get(0).fullName()).isEqualTo("alice/demo");
    assertThat(page.getContent().get(0).ownerUsername()).isEqualTo("alice");
    verify(repoRepository).findAllWithOwner(pageable);
  }

  @Test
  @DisplayName("searches repos when query or owner is provided")
  void listRepos_usesSearch_whenFiltersPresent() {
    var pageable = PageRequest.of(0, 10);
    var repo = sampleRepo("bob", "api");
    when(repoRepository.searchWithOwner("api", "bob", pageable))
        .thenReturn(new PageImpl<>(List.of(repo)));

    var page = adminRepoService.listRepos(pageable, " api ", " bob ");

    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getContent().get(0).name()).isEqualTo("api");
    verify(repoRepository).searchWithOwner(eq("api"), eq("bob"), eq(pageable));
  }

  @Test
  @DisplayName("maps repo flags and metadata into summary")
  void listRepos_mapsRepoFields() {
    var pageable = PageRequest.of(0, 5);
    var repo = sampleRepo("carol", "private-app");
    repo.setPrivate(true);
    repo.setArchived(true);
    repo.setDescription("Internal app");
    repo.setDefaultBranch("develop");
    when(repoRepository.findAllWithOwner(pageable)).thenReturn(new PageImpl<>(List.of(repo)));

    var summary = adminRepoService.listRepos(pageable, null, null).getContent().get(0);

    assertThat(summary.isPrivate()).isTrue();
    assertThat(summary.isArchived()).isTrue();
    assertThat(summary.description()).isEqualTo("Internal app");
    assertThat(summary.defaultBranch()).isEqualTo("develop");
    assertThat(summary.createdAt()).isEqualTo(Instant.EPOCH);
  }

  private static Repo sampleRepo(final String ownerUsername, final String name) {
    var owner = new Tenant();
    owner.setUsername(ownerUsername);

    var repo = new Repo();
    repo.setId(UUID.randomUUID());
    repo.setOwner(owner);
    repo.setName(name);
    repo.setCreatedAt(Instant.EPOCH);
    repo.setUpdatedAt(Instant.EPOCH);
    return repo;
  }
}
