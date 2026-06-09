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
package dev.gitnode.os.tag.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.shared.cache.RepoCacheInvalidator;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemAlreadyExistsException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.repo.entities.Repo;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import dev.gitnode.os.tag.dtos.CreateReleaseForm;
import dev.gitnode.os.tag.dtos.ReleaseInfo;
import dev.gitnode.os.tag.dtos.TenantReleaseInfo;
import dev.gitnode.os.tag.dtos.UpdateReleaseForm;
import dev.gitnode.os.tag.entities.Release;
import dev.gitnode.os.tag.mappers.ReleaseMapper;
import dev.gitnode.os.tag.repositories.ReleaseRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReleaseTxService unit tests")
class ReleaseTxServiceTest {

  @Mock private ReleaseRepository releaseRepository;
  @Mock private RepoRepository repoRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private ReleaseMapper releaseMapper;
  @Mock private RepoCacheInvalidator cacheInvalidator;

  @InjectMocks private ReleaseTxService releaseTxService;

  @Test
  @DisplayName("create saves release and returns info when tag not taken")
  void create_savesRelease_whenTagNotTaken() {
    final var repoId = UUID.randomUUID();
    final var authorId = UUID.randomUUID();
    final var repo = createRepo(repoId, "owner", "my-repo");
    final var author = createTenant(authorId, "owner");
    final var form = createReleaseForm("v1.0.0", "Release 1.0", false, false);

    when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
        .thenReturn(Optional.of(repo));
    when(tenantRepository.findById(authorId)).thenReturn(Optional.of(author));
    when(releaseRepository.existsByRepoIdAndTagName(repoId, "v1.0.0")).thenReturn(false);

    final var savedRelease = createRelease(UUID.randomUUID(), repo, author, "v1.0.0");
    final var expectedInfo = createReleaseInfo(savedRelease);
    when(releaseRepository.save(any(Release.class))).thenReturn(savedRelease);
    when(releaseMapper.toInfo(savedRelease)).thenReturn(expectedInfo);

    final var result = releaseTxService.create("owner", "my-repo", authorId, form);

    assertThat(result.tagName()).isEqualTo("v1.0.0");
    verify(releaseRepository).save(any(Release.class));
  }

  @Test
  @DisplayName("create throws ItemAlreadyExistsException when release for tag already exists")
  void create_throws_whenReleaseAlreadyExists() {
    final var repoId = UUID.randomUUID();
    final var authorId = UUID.randomUUID();
    final var repo = createRepo(repoId, "owner", "my-repo");
    final var author = createTenant(authorId, "owner");
    final var form = createReleaseForm("v1.0.0", "Duplicate", false, false);

    when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
        .thenReturn(Optional.of(repo));
    when(tenantRepository.findById(authorId)).thenReturn(Optional.of(author));
    when(releaseRepository.existsByRepoIdAndTagName(repoId, "v1.0.0")).thenReturn(true);

    assertThatThrownBy(() -> releaseTxService.create("owner", "my-repo", authorId, form))
        .isInstanceOf(ItemAlreadyExistsException.class)
        .hasMessageContaining("releaseAlreadyExists");
  }

  @Test
  @DisplayName("getAll returns empty list when no releases exist")
  void getAll_returnsEmptyList_whenNoReleases() {
    final var repoId = UUID.randomUUID();
    final var repo = createRepo(repoId, "owner", "my-repo");

    when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
        .thenReturn(Optional.of(repo));
    when(releaseRepository.findAllByRepoIdOrderByCreatedAtDesc(repoId)).thenReturn(List.of());

    final var result = releaseTxService.getAll("owner", "my-repo");

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName(
      "update throws AccessNotAllowedException when requester is neither author nor repo owner")
  void update_throws_whenNotAuthorOrRepoOwner() {
    final var releaseId = UUID.randomUUID();
    final var authorId = UUID.randomUUID();
    final var requesterId = UUID.randomUUID();
    final var author = createTenant(authorId, "author");
    final var repo = createRepo(UUID.randomUUID(), "repoOwner", "repo");
    final var release = createRelease(releaseId, repo, author, "v1.0.0");
    final var form = new UpdateReleaseForm();
    form.setName("Updated");

    when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(release));
    when(tenantRepository.findByUsername("repoOwner")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> releaseTxService.update(releaseId, requesterId, "repoOwner", form))
        .isInstanceOf(AccessNotAllowedException.class);
  }

  @Test
  @DisplayName("delete removes release when requester is the author")
  void delete_removesRelease_whenRequesterIsAuthor() {
    final var releaseId = UUID.randomUUID();
    final var authorId = UUID.randomUUID();
    final var author = createTenant(authorId, "owner");
    final var repo = createRepo(UUID.randomUUID(), "owner", "repo");
    final var release = createRelease(releaseId, repo, author, "v2.0.0");

    when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(release));

    releaseTxService.delete(releaseId, authorId, "owner");

    verify(releaseRepository).delete(release);
  }

  @Test
  @DisplayName("findLatest returns empty optional when no published non-draft releases exist")
  void findLatest_returnsEmpty_whenNoPublishedReleases() {
    final var repoId = UUID.randomUUID();
    final var repo = createRepo(repoId, "owner", "repo");

    when(repoRepository.findByOwnerUsernameAndName("owner", "repo")).thenReturn(Optional.of(repo));
    when(releaseRepository
            .findFirstByRepoIdAndIsDraftFalseAndIsPrereleaseFalseOrderByPublishedAtDesc(repoId))
        .thenReturn(Optional.empty());

    final var result = releaseTxService.findLatest("owner", "repo");

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("findRepo throws ItemNotFoundException when repo does not exist")
  void findRepo_throws_whenRepoNotFound() {
    when(repoRepository.findByOwnerUsernameAndName("owner", "missing"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> releaseTxService.findRepo("owner", "missing"))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("Repo not found");
  }

  private static Repo createRepo(final UUID id, final String ownerUsername, final String name) {
    final var owner = new Tenant();
    owner.setId(UUID.randomUUID());
    owner.setUsername(ownerUsername);
    final var repo = new Repo();
    repo.setId(id);
    repo.setOwner(owner);
    repo.setName(name);
    repo.setDefaultBranch("main");
    return repo;
  }

  private static Tenant createTenant(final UUID id, final String username) {
    final var tenant = new Tenant();
    tenant.setId(id);
    tenant.setUsername(username);
    tenant.setDisplayName(username);
    return tenant;
  }

  private static Release createRelease(
      final UUID id, final Repo repo, final Tenant author, final String tagName) {

    final var release = new Release();
    release.setId(id);
    release.setRepo(repo);
    release.setAuthor(author);
    release.setTagName(tagName);
    release.setCreatedAt(Instant.now());
    release.setUpdatedAt(Instant.now());
    return release;
  }

  private static CreateReleaseForm createReleaseForm(
      final String tagName, final String name, final boolean isDraft, final boolean isPrerelease) {

    final var form = new CreateReleaseForm();
    form.setTagName(tagName);
    form.setName(name);
    form.setDraft(isDraft);
    form.setPrerelease(isPrerelease);
    return form;
  }

  private static ReleaseInfo createReleaseInfo(final Release release) {
    return ReleaseInfo.builder()
        .id(release.getId())
        .tagName(release.getTagName())
        .isDraft(release.isDraft())
        .isPrerelease(release.isPrerelease())
        .author(
            new TenantReleaseInfo(
                release.getAuthor().getId(), release.getAuthor().getUsername(), null, null))
        .createdAt(release.getCreatedAt())
        .updatedAt(release.getUpdatedAt())
        .build();
  }
}
