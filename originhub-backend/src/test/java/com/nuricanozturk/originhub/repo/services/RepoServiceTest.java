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
package com.nuricanozturk.originhub.repo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.shared.collaborator.services.CollaboratorAccessPort;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.AccessNotAllowedException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.dtos.RepoForm;
import com.nuricanozturk.originhub.shared.repo.dtos.RepoInfo;
import com.nuricanozturk.originhub.shared.repo.dtos.TenantRepoInfo;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.events.RepoCreatedEvent;
import com.nuricanozturk.originhub.shared.repo.events.RepoDeletedEvent;
import com.nuricanozturk.originhub.shared.repo.mappers.RepoMapper;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.repo.services.RepoService;
import com.nuricanozturk.originhub.shared.repo.services.RepoStorageService;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("RepoService unit tests")
class RepoServiceTest {

  @Mock private RepoRepository repoRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private RepoMapper repoMapper;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private RepoStorageService repoStorageService;
  @Mock private CollaboratorAccessPort collaboratorAccessPort;

  @InjectMocks private RepoService repoService;

  @Test
  @DisplayName("create throws ItemNotFoundException when tenant not found")
  void create_throwsUserNotFound_whenTenantMissing() {
    UUID tenantId = UUID.randomUUID();
    RepoForm form = RepoForm.builder().name("my-repo").build();
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> repoService.create(tenantId, form))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("userNotFound");
  }

  @Test
  @DisplayName("create saves repo and returns mapped RepoInfo")
  void create_returnsRepoInfo_whenSuccess() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    tenant.setUsername("alice");
    RepoForm form =
        RepoForm.builder().name("my-repo").description("desc").topics(Set.of("java")).build();
    Repo savedRepo = new Repo();
    savedRepo.setId(UUID.randomUUID());
    savedRepo.setName("my-repo");
    RepoInfo expectedInfo = createRepoInfo(savedRepo.getId(), "my-repo");

    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(repoRepository.save(any(Repo.class))).thenReturn(savedRepo);
    when(repoMapper.toDto(savedRepo)).thenReturn(expectedInfo);

    RepoInfo result = repoService.create(tenantId, form);

    assertThat(result).isSameAs(expectedInfo);
    ArgumentCaptor<Repo> repoCaptor = ArgumentCaptor.forClass(Repo.class);
    verify(repoRepository).save(repoCaptor.capture());
    assertThat(repoCaptor.getValue().getName()).isEqualTo("my-repo");
    assertThat(repoCaptor.getValue().getDefaultBranch())
        .isEqualTo(RepoService.NEW_REPO_DEFAULT_BRANCH);
    assertThat(repoCaptor.getValue().getOwner()).isSameAs(tenant);
    ArgumentCaptor<RepoCreatedEvent> eventCaptor = ArgumentCaptor.forClass(RepoCreatedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().repoOwner()).isEqualTo("alice");
    assertThat(eventCaptor.getValue().repoName()).isEqualTo("my-repo");
  }

  @Test
  @DisplayName("update throws ItemNotFoundException when repo owner user not found")
  void update_throwsUserNotFound_whenOwnerMissing() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantRepository.findByUsername("owner")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                repoService.update(
                    tenantId, "owner", "repo", RepoForm.builder().name("repo").build()))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("userNotFound");
  }

  @Test
  @DisplayName("update throws ItemNotFoundException when repo not found")
  void update_throwsRepoNotFound_whenRepoMissing() {
    UUID tenantId = UUID.randomUUID();
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    Tenant owner = new Tenant();
    owner.setId(tenantId);
    owner.setUsername("owner");
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
    when(repoRepository.findByOwnerIdAndName(owner.getId(), "repo")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                repoService.update(
                    tenantId, "owner", "repo", RepoForm.builder().name("repo").build()))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("repoNotFound");
  }

  @Test
  @DisplayName("update throws AccessNotAllowedException when caller is not repo owner")
  void update_throwsAccessDenied_whenNotOwner() {
    UUID tenantId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    Tenant caller = new Tenant();
    caller.setId(tenantId);
    caller.setUsername("other");
    Tenant owner = new Tenant();
    owner.setId(ownerId);
    owner.setUsername("owner");
    Repo repo = new Repo();
    repo.setId(UUID.randomUUID());
    repo.setName("repo");
    repo.setOwner(owner);

    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(caller));
    when(tenantRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
    when(repoRepository.findByOwnerIdAndName(ownerId, "repo")).thenReturn(Optional.of(repo));

    assertThatThrownBy(
            () ->
                repoService.update(
                    tenantId, "owner", "repo", RepoForm.builder().name("repo").build()))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining("repoAccessDenied");
  }

  @Test
  @DisplayName("update returns RepoInfo when caller is owner")
  void update_returnsRepoInfo_whenOwner() {
    UUID tenantId = UUID.randomUUID();
    Tenant owner = new Tenant();
    owner.setId(tenantId);
    owner.setUsername("owner");
    Repo repo = new Repo();
    repo.setId(UUID.randomUUID());
    repo.setName("repo");
    repo.setOwner(owner);
    RepoInfo expectedInfo = createRepoInfo(repo.getId(), "repo");
    RepoForm form = RepoForm.builder().name("repo").description("new").build();

    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(owner));
    when(tenantRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
    when(repoRepository.findByOwnerIdAndName(tenantId, "repo")).thenReturn(Optional.of(repo));
    when(repoRepository.save(repo)).thenReturn(repo);
    when(repoMapper.toDto(repo)).thenReturn(expectedInfo);

    RepoInfo result = repoService.update(tenantId, "owner", "repo", form);

    assertThat(result).isSameAs(expectedInfo);
    assertThat(repo.getDescription()).isEqualTo("new");
  }

  @Test
  @DisplayName("update preserves topics when form topics is null")
  void update_preservesTopics_whenTopicsNull() {
    UUID tenantId = UUID.randomUUID();
    Tenant owner = new Tenant();
    owner.setId(tenantId);
    owner.setUsername("owner");
    Repo repo = new Repo();
    repo.setId(UUID.randomUUID());
    repo.setName("repo");
    repo.setOwner(owner);
    repo.setTopics(Set.of("java", "spring"));
    RepoInfo expectedInfo = createRepoInfo(repo.getId(), "repo");
    RepoForm form = RepoForm.builder().name("repo").description("new").build();

    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(owner));
    when(tenantRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
    when(repoRepository.findByOwnerIdAndName(tenantId, "repo")).thenReturn(Optional.of(repo));
    when(repoRepository.save(repo)).thenReturn(repo);
    when(repoMapper.toDto(repo)).thenReturn(expectedInfo);

    repoService.update(tenantId, "owner", "repo", form);

    assertThat(repo.getTopics()).containsExactlyInAnyOrder("java", "spring");
  }

  @Test
  @DisplayName("update replaces topics when form includes topics")
  void update_replacesTopics_whenTopicsProvided() {
    UUID tenantId = UUID.randomUUID();
    Tenant owner = new Tenant();
    owner.setId(tenantId);
    owner.setUsername("owner");
    Repo repo = new Repo();
    repo.setId(UUID.randomUUID());
    repo.setName("repo");
    repo.setOwner(owner);
    repo.setTopics(Set.of("old"));
    RepoInfo expectedInfo = createRepoInfo(repo.getId(), "repo");
    RepoForm form = RepoForm.builder().name("repo").description("d").topics(Set.of("go")).build();

    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(owner));
    when(tenantRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
    when(repoRepository.findByOwnerIdAndName(tenantId, "repo")).thenReturn(Optional.of(repo));
    when(repoRepository.save(repo)).thenReturn(repo);
    when(repoMapper.toDto(repo)).thenReturn(expectedInfo);

    repoService.update(tenantId, "owner", "repo", form);

    assertThat(repo.getTopics()).containsExactly("go");
  }

  @Test
  @DisplayName("delete(owner, repoName) throws when repo not found")
  void deleteByOwnerAndName_throws_whenRepoNotFound() {
    when(repoRepository.findByOwnerUsernameAndName("owner", "repo")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> repoService.delete("owner", "repo"))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("repoNotFound");
  }

  @Test
  @DisplayName("delete(tenantId, repoName, owner) throws when tenantId does not match owner")
  void deleteByTenantId_throws_invalidDeleteRequest() {
    Tenant owner = new Tenant();
    owner.setId(UUID.randomUUID());
    when(tenantRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
    UUID differentTenantId = UUID.randomUUID();

    assertThatThrownBy(() -> repoService.delete(differentTenantId, "repo", "owner"))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining("repoAccessDenied");
  }

  @Test
  @DisplayName("delete(tenantId, repoName, owner) publishes RepoDeletedEvent on success")
  void deleteByTenantId_publishesEvent_whenSuccess() {
    UUID ownerId = UUID.randomUUID();
    Tenant owner = new Tenant();
    owner.setId(ownerId);
    owner.setUsername("owner");
    Repo repo = new Repo();
    repo.setId(UUID.randomUUID());
    repo.setName("repo");
    when(tenantRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
    when(repoRepository.findByOwnerIdAndName(ownerId, "repo")).thenReturn(Optional.of(repo));

    repoService.delete(ownerId, "repo", "owner");

    verify(repoRepository).deleteById(repo.getId());
    ArgumentCaptor<RepoDeletedEvent> captor = ArgumentCaptor.forClass(RepoDeletedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().repoOwner()).isEqualTo("owner");
    assertThat(captor.getValue().repoName()).isEqualTo("repo");
  }

  @Test
  @DisplayName("findAllByOwner returns public repos only when requester is anonymous")
  void findAllByOwner_returnsMappedList_forAnonymous() {
    Repo r1 = new Repo();
    r1.setId(UUID.randomUUID());
    r1.setName("a");
    r1.setPrivate(false);
    RepoInfo i1 = createRepoInfo(r1.getId(), "a");
    PageRequest pageable = PageRequest.of(0, 10);
    when(repoRepository.findAllByOwnerUsernameAndIsPrivateFalse("owner", pageable))
        .thenReturn(new PageImpl<>(List.of(r1), pageable, 1));
    when(repoMapper.toDto(r1)).thenReturn(i1);

    Page<RepoInfo> result = repoService.findAllByOwner("owner", pageable, null);

    assertThat(result.getContent()).singleElement().isSameAs(i1);
    assertThat(result.getTotalElements()).isEqualTo(1);
  }

  @Test
  @DisplayName("findAllByOwner returns all repos when requester is owner")
  void findAllByOwner_returnsAllRepos_forOwner() {
    UUID ownerId = UUID.randomUUID();
    Tenant ownerTenant = new Tenant();
    ownerTenant.setId(ownerId);
    Repo r1 = new Repo();
    r1.setId(UUID.randomUUID());
    r1.setName("private-repo");
    r1.setPrivate(true);
    RepoInfo i1 = createRepoInfo(r1.getId(), "private-repo");
    PageRequest pageable = PageRequest.of(0, 10);
    when(tenantRepository.findByUsername("owner")).thenReturn(Optional.of(ownerTenant));
    when(repoRepository.findAllByOwnerUsername("owner", pageable))
        .thenReturn(new PageImpl<>(List.of(r1), pageable, 1));
    when(repoMapper.toDto(r1)).thenReturn(i1);

    Page<RepoInfo> result = repoService.findAllByOwner("owner", pageable, ownerId);

    assertThat(result.getContent()).singleElement().isSameAs(i1);
    assertThat(result.getTotalElements()).isEqualTo(1);
  }

  @Test
  @DisplayName("findByOwnerAndName throws when repo not found")
  void findByOwnerAndName_throws_whenNotFound() {
    when(repoRepository.findByOwnerUsernameAndName("owner", "missing"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> repoService.findByOwnerAndName("owner", "missing", null))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("repoNotFound");
  }

  @Test
  @DisplayName("findByOwnerAndName returns RepoInfo for public repo without auth")
  void findByOwnerAndName_returnsRepoInfo_whenPublicAndAnonymous() {
    Repo repo = new Repo();
    repo.setId(UUID.randomUUID());
    repo.setName("my-repo");
    repo.setPrivate(false);
    RepoInfo expected = createRepoInfo(repo.getId(), "my-repo");
    when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
        .thenReturn(Optional.of(repo));
    when(repoMapper.toDto(repo)).thenReturn(expected);

    RepoInfo result = repoService.findByOwnerAndName("owner", "my-repo", null);

    assertThat(result).isSameAs(expected);
  }

  @Test
  @DisplayName("findByOwnerAndName throws AccessNotAllowedException for private repo without auth")
  void findByOwnerAndName_throws_whenPrivateAndAnonymous() {
    Repo repo = new Repo();
    repo.setId(UUID.randomUUID());
    repo.setName("secret");
    repo.setPrivate(true);
    when(repoRepository.findByOwnerUsernameAndName("owner", "secret"))
        .thenReturn(Optional.of(repo));
    when(tenantRepository.findByUsername("owner")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> repoService.findByOwnerAndName("owner", "secret", null))
        .isInstanceOf(
            com.nuricanozturk.originhub.shared.errorhandling.exceptions.AccessNotAllowedException
                .class)
        .hasMessageContaining("repoAccessDenied");
  }

  @Test
  @DisplayName("findByOwnerAndName returns RepoInfo for private repo when requester is owner")
  void findByOwnerAndName_returnsRepoInfo_whenPrivateAndOwner() {
    UUID ownerId = UUID.randomUUID();
    Tenant ownerTenant = new Tenant();
    ownerTenant.setId(ownerId);
    Repo repo = new Repo();
    repo.setId(UUID.randomUUID());
    repo.setName("private-repo");
    repo.setPrivate(true);
    RepoInfo expected = createRepoInfo(repo.getId(), "private-repo");
    when(repoRepository.findByOwnerUsernameAndName("owner", "private-repo"))
        .thenReturn(Optional.of(repo));
    when(tenantRepository.findByUsername("owner")).thenReturn(Optional.of(ownerTenant));
    when(repoMapper.toDto(repo)).thenReturn(expected);

    RepoInfo result = repoService.findByOwnerAndName("owner", "private-repo", ownerId);

    assertThat(result).isSameAs(expected);
  }

  @Test
  @DisplayName("rollbackRepoName throws when repo not found")
  void rollbackRepoName_throws_whenNotFound() {
    when(repoRepository.findByOwnerUsernameAndName("owner", "newName"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> repoService.rollbackRepoName("owner", "oldName", "newName"))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("repoNotFound");
  }

  @Test
  @DisplayName("rollbackRepoName updates name and saves")
  void rollbackRepoName_updatesNameAndSaves() {
    Repo repo = new Repo();
    repo.setId(UUID.randomUUID());
    repo.setName("newName");
    when(repoRepository.findByOwnerUsernameAndName("owner", "newName"))
        .thenReturn(Optional.of(repo));
    when(repoRepository.save(repo)).thenReturn(repo);

    repoService.rollbackRepoName("owner", "oldName", "newName");

    assertThat(repo.getName()).isEqualTo("oldName");
    verify(repoRepository).save(repo);
  }

  private static RepoInfo createRepoInfo(UUID id, String name) {
    return new RepoInfo(
        id,
        new TenantRepoInfo(id, "owner", null),
        name,
        null,
        false,
        false,
        "main",
        Set.of(),
        false,
        false,
        Instant.EPOCH,
        Instant.EPOCH,
        null,
        0);
  }
}
