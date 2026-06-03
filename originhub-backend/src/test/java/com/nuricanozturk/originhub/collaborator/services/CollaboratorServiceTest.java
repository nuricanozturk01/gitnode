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
package com.nuricanozturk.originhub.collaborator.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.collaborator.dtos.CollaboratorInfo;
import com.nuricanozturk.originhub.collaborator.dtos.InviteCollaboratorForm;
import com.nuricanozturk.originhub.collaborator.dtos.UpdateCollaboratorPermissionsForm;
import com.nuricanozturk.originhub.collaborator.entities.CollaboratorStatus;
import com.nuricanozturk.originhub.collaborator.entities.RepoCollaborator;
import com.nuricanozturk.originhub.collaborator.mappers.CollaboratorMapper;
import com.nuricanozturk.originhub.collaborator.repositories.CollaboratorRepository;
import com.nuricanozturk.originhub.shared.collaborator.dtos.CollaboratorPermission;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.AccessNotAllowedException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.BadRequestException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemAlreadyExistsException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollaboratorService unit tests")
class CollaboratorServiceTest {

  @Mock private CollaboratorRepository collaboratorRepository;
  @Mock private RepoRepository repoRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private CollaboratorMapper collaboratorMapper;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private CollaboratorService collaboratorService;

  private static final UUID OWNER_ID = UUID.randomUUID();
  private static final UUID INVITEE_ID = UUID.randomUUID();
  private static final UUID REPO_ID = UUID.randomUUID();

  private Tenant owner;
  private Tenant invitee;
  private Repo repo;

  @BeforeEach
  void setup() {
    owner = new Tenant();
    owner.setId(OWNER_ID);
    owner.setUsername("owner");
    owner.setEmail("owner@example.com");

    invitee = new Tenant();
    invitee.setId(INVITEE_ID);
    invitee.setUsername("invitee");
    invitee.setEmail("invitee@example.com");

    repo = new Repo();
    repo.setId(REPO_ID);
    repo.setName("my-repo");
    repo.setOwner(owner);
    repo.setPrivate(true);
    repo.setDefaultBranch("main");
    repo.setCreatedAt(Instant.now());
    repo.setUpdatedAt(Instant.now());
  }

  @Nested
  @DisplayName("invite")
  class InviteTests {

    @Test
    @DisplayName("throws ItemNotFoundException when repo not found")
    void invite_throwsRepoNotFound() {
      when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
          .thenReturn(Optional.empty());

      assertThatThrownBy(
              () ->
                  collaboratorService.invite(
                      OWNER_ID, "owner", "my-repo", form("invitee", Set.of())))
          .isInstanceOf(ItemNotFoundException.class)
          .hasMessageContaining("repoNotFound");
    }

    @Test
    @DisplayName("throws AccessNotAllowedException when requester is not repo owner")
    void invite_throwsAccessDenied_whenNotOwner() {
      when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
          .thenReturn(Optional.of(repo));

      assertThatThrownBy(
              () ->
                  collaboratorService.invite(
                      INVITEE_ID, "owner", "my-repo", form("invitee", Set.of())))
          .isInstanceOf(AccessNotAllowedException.class);
    }

    @Test
    @DisplayName("throws ItemNotFoundException when invitee user not found")
    void invite_throwsUserNotFound() {
      when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
          .thenReturn(Optional.of(repo));
      when(tenantRepository.findByUsername("invitee")).thenReturn(Optional.empty());

      assertThatThrownBy(
              () ->
                  collaboratorService.invite(
                      OWNER_ID, "owner", "my-repo", form("invitee", Set.of())))
          .isInstanceOf(ItemNotFoundException.class)
          .hasMessageContaining("userNotFound");
    }

    @Test
    @DisplayName("throws BadRequestException when owner invites themselves")
    void invite_throwsBadRequest_whenSelfInvite() {
      when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
          .thenReturn(Optional.of(repo));
      when(tenantRepository.findByUsername("owner")).thenReturn(Optional.of(owner));

      assertThatThrownBy(
              () ->
                  collaboratorService.invite(OWNER_ID, "owner", "my-repo", form("owner", Set.of())))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("cannotInviteYourself");
    }

    @Test
    @DisplayName("throws ItemAlreadyExistsException when user is already an accepted collaborator")
    void invite_throwsAlreadyExists_whenAlreadyCollaborator() {
      when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
          .thenReturn(Optional.of(repo));
      when(tenantRepository.findByUsername("invitee")).thenReturn(Optional.of(invitee));
      when(collaboratorRepository.existsByRepoIdAndTenantIdAndStatus(
              REPO_ID, INVITEE_ID, CollaboratorStatus.ACCEPTED))
          .thenReturn(true);

      assertThatThrownBy(
              () ->
                  collaboratorService.invite(
                      OWNER_ID, "owner", "my-repo", form("invitee", Set.of())))
          .isInstanceOf(ItemAlreadyExistsException.class)
          .hasMessageContaining("alreadyCollaborator");
    }

    @Test
    @DisplayName("creates new collaborator with READ permission always included")
    void invite_createsCollaborator_withReadAlwaysIncluded() {
      when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
          .thenReturn(Optional.of(repo));
      when(tenantRepository.findByUsername("invitee")).thenReturn(Optional.of(invitee));
      when(collaboratorRepository.existsByRepoIdAndTenantIdAndStatus(any(), any(), any()))
          .thenReturn(false);
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.empty());

      final var saved =
          collaborator(
              CollaboratorStatus.PENDING,
              Set.of(CollaboratorPermission.READ, CollaboratorPermission.PUSH));
      when(collaboratorRepository.save(any())).thenReturn(saved);
      final var expectedInfo = info(CollaboratorStatus.PENDING);
      when(collaboratorMapper.toInfo(saved)).thenReturn(expectedInfo);

      final var result =
          collaboratorService.invite(
              OWNER_ID, "owner", "my-repo", form("invitee", Set.of(CollaboratorPermission.PUSH)));

      assertThat(result).isEqualTo(expectedInfo);

      final var captor = ArgumentCaptor.forClass(RepoCollaborator.class);
      verify(collaboratorRepository).save(captor.capture());
      assertThat(captor.getValue().getPermissions()).contains(CollaboratorPermission.READ);
      assertThat(captor.getValue().getPermissions()).contains(CollaboratorPermission.PUSH);
    }

    @Test
    @DisplayName("re-invites declined collaborator by resetting status to PENDING")
    void invite_reInvites_whenPreviouslyDeclined() {
      final var existing = collaborator(CollaboratorStatus.DECLINED, Set.of());
      when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
          .thenReturn(Optional.of(repo));
      when(tenantRepository.findByUsername("invitee")).thenReturn(Optional.of(invitee));
      when(collaboratorRepository.existsByRepoIdAndTenantIdAndStatus(any(), any(), any()))
          .thenReturn(false);
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.of(existing));
      when(collaboratorRepository.save(any())).thenReturn(existing);
      when(collaboratorMapper.toInfo(any())).thenReturn(info(CollaboratorStatus.PENDING));

      collaboratorService.invite(OWNER_ID, "owner", "my-repo", form("invitee", Set.of()));

      assertThat(existing.getStatus()).isEqualTo(CollaboratorStatus.PENDING);
    }
  }

  @Nested
  @DisplayName("respondToInvitation")
  class RespondToInvitationTests {

    @Test
    @DisplayName("accepts invitation successfully")
    void respond_acceptsInvitation() {
      final var existing =
          collaborator(CollaboratorStatus.PENDING, Set.of(CollaboratorPermission.READ));
      when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
          .thenReturn(Optional.of(repo));
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.of(existing));
      when(collaboratorRepository.save(any())).thenReturn(existing);
      when(collaboratorMapper.toInfo(any())).thenReturn(info(CollaboratorStatus.ACCEPTED));

      final var result =
          collaboratorService.respondToInvitation(INVITEE_ID, "owner", "my-repo", true);

      assertThat(existing.getStatus()).isEqualTo(CollaboratorStatus.ACCEPTED);
      assertThat(result.getStatus()).isEqualTo(CollaboratorStatus.ACCEPTED);
    }

    @Test
    @DisplayName("declines invitation successfully")
    void respond_declinesInvitation() {
      final var existing =
          collaborator(CollaboratorStatus.PENDING, Set.of(CollaboratorPermission.READ));
      when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
          .thenReturn(Optional.of(repo));
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.of(existing));
      when(collaboratorRepository.save(any())).thenReturn(existing);
      when(collaboratorMapper.toInfo(any())).thenReturn(info(CollaboratorStatus.DECLINED));

      collaboratorService.respondToInvitation(INVITEE_ID, "owner", "my-repo", false);

      assertThat(existing.getStatus()).isEqualTo(CollaboratorStatus.DECLINED);
    }

    @Test
    @DisplayName("throws BadRequestException when invitation already handled")
    void respond_throwsBadRequest_whenAlreadyHandled() {
      final var existing = collaborator(CollaboratorStatus.ACCEPTED, Set.of());
      when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
          .thenReturn(Optional.of(repo));
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.of(existing));

      assertThatThrownBy(
              () -> collaboratorService.respondToInvitation(INVITEE_ID, "owner", "my-repo", true))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("invitationAlreadyHandled");
    }
  }

  @Nested
  @DisplayName("remove")
  class RemoveTests {

    @Test
    @DisplayName("owner can remove any collaborator")
    void remove_ownerCanRemove() {
      final var existing = collaborator(CollaboratorStatus.ACCEPTED, Set.of());
      when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
          .thenReturn(Optional.of(repo));
      when(tenantRepository.findByUsername("invitee")).thenReturn(Optional.of(invitee));
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.of(existing));

      collaboratorService.remove(OWNER_ID, "owner", "my-repo", "invitee");

      verify(collaboratorRepository).delete(existing);
    }

    @Test
    @DisplayName("collaborator can remove themselves")
    void remove_collaboratorCanRemoveSelf() {
      final var existing = collaborator(CollaboratorStatus.ACCEPTED, Set.of());
      when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
          .thenReturn(Optional.of(repo));
      when(tenantRepository.findByUsername("invitee")).thenReturn(Optional.of(invitee));
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.of(existing));

      collaboratorService.remove(INVITEE_ID, "owner", "my-repo", "invitee");

      verify(collaboratorRepository).delete(existing);
    }

    @Test
    @DisplayName("throws AccessNotAllowedException when third party tries to remove collaborator")
    void remove_throwsAccessDenied_forThirdParty() {
      final var thirdPartyId = UUID.randomUUID();
      when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
          .thenReturn(Optional.of(repo));
      when(tenantRepository.findByUsername("invitee")).thenReturn(Optional.of(invitee));

      assertThatThrownBy(
              () -> collaboratorService.remove(thirdPartyId, "owner", "my-repo", "invitee"))
          .isInstanceOf(AccessNotAllowedException.class);
    }
  }

  @Nested
  @DisplayName("updatePermissions")
  class UpdatePermissionsTests {

    @Test
    @DisplayName("owner can update collaborator permissions")
    void updatePermissions_ownerCanUpdate() {
      final var existing =
          collaborator(CollaboratorStatus.ACCEPTED, Set.of(CollaboratorPermission.READ));
      when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
          .thenReturn(Optional.of(repo));
      when(tenantRepository.findByUsername("invitee")).thenReturn(Optional.of(invitee));
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.of(existing));
      when(collaboratorRepository.save(any())).thenReturn(existing);
      when(collaboratorMapper.toInfo(any())).thenReturn(info(CollaboratorStatus.ACCEPTED));

      final var form = new UpdateCollaboratorPermissionsForm();
      form.setPermissions(Set.of(CollaboratorPermission.READ, CollaboratorPermission.PUSH));

      collaboratorService.updatePermissions(OWNER_ID, "owner", "my-repo", "invitee", form);

      assertThat(existing.getPermissions()).contains(CollaboratorPermission.PUSH);
    }

    @Test
    @DisplayName("throws AccessNotAllowedException when non-owner tries to update permissions")
    void updatePermissions_throwsAccessDenied_whenNotOwner() {
      when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
          .thenReturn(Optional.of(repo));

      final var form = new UpdateCollaboratorPermissionsForm();
      form.setPermissions(Set.of(CollaboratorPermission.PUSH));

      assertThatThrownBy(
              () ->
                  collaboratorService.updatePermissions(
                      INVITEE_ID, "owner", "my-repo", "invitee", form))
          .isInstanceOf(AccessNotAllowedException.class);
    }
  }

  @Nested
  @DisplayName("isActiveCollaborator")
  class IsActiveCollaboratorTests {

    @Test
    @DisplayName("returns true when collaborator has ACCEPTED status")
    void isActive_returnsTrue_whenAccepted() {
      when(collaboratorRepository.existsByRepoIdAndTenantIdAndStatus(
              REPO_ID, INVITEE_ID, CollaboratorStatus.ACCEPTED))
          .thenReturn(true);

      assertThat(collaboratorService.isActiveCollaborator(REPO_ID, INVITEE_ID)).isTrue();
    }

    @Test
    @DisplayName("returns false when not a collaborator")
    void isActive_returnsFalse_whenNotCollaborator() {
      when(collaboratorRepository.existsByRepoIdAndTenantIdAndStatus(
              REPO_ID, INVITEE_ID, CollaboratorStatus.ACCEPTED))
          .thenReturn(false);

      assertThat(collaboratorService.isActiveCollaborator(REPO_ID, INVITEE_ID)).isFalse();
    }
  }

  @Nested
  @DisplayName("hasPermission")
  class HasPermissionTests {

    @Test
    @DisplayName("returns true when collaborator has the specific permission")
    void hasPermission_returnsTrue_whenGranted() {
      final var existing =
          collaborator(CollaboratorStatus.ACCEPTED, Set.of(CollaboratorPermission.PUSH));
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.of(existing));

      assertThat(
              collaboratorService.hasPermission(REPO_ID, INVITEE_ID, CollaboratorPermission.PUSH))
          .isTrue();
    }

    @Test
    @DisplayName("returns true when collaborator has ADMIN permission")
    void hasPermission_returnsTrue_whenAdmin() {
      final var existing =
          collaborator(CollaboratorStatus.ACCEPTED, Set.of(CollaboratorPermission.ADMIN));
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.of(existing));

      assertThat(
              collaboratorService.hasPermission(
                  REPO_ID, INVITEE_ID, CollaboratorPermission.SETTINGS_WRITE))
          .isTrue();
    }

    @Test
    @DisplayName("returns false when permission not granted")
    void hasPermission_returnsFalse_whenNotGranted() {
      final var existing =
          collaborator(CollaboratorStatus.ACCEPTED, Set.of(CollaboratorPermission.READ));
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.of(existing));

      assertThat(
              collaboratorService.hasPermission(REPO_ID, INVITEE_ID, CollaboratorPermission.PUSH))
          .isFalse();
    }

    @Test
    @DisplayName("returns false when status is not ACCEPTED")
    void hasPermission_returnsFalse_whenPending() {
      final var existing =
          collaborator(CollaboratorStatus.PENDING, Set.of(CollaboratorPermission.PUSH));
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.of(existing));

      assertThat(
              collaboratorService.hasPermission(REPO_ID, INVITEE_ID, CollaboratorPermission.PUSH))
          .isFalse();
    }
  }

  @Nested
  @DisplayName("list")
  class ListTests {

    @Test
    @DisplayName("owner can list collaborators")
    void list_ownerCanList() {
      when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
          .thenReturn(Optional.of(repo));
      when(collaboratorRepository.findAllByRepoId(any(UUID.class), any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of()));

      final var result = collaboratorService.list(OWNER_ID, "owner", "my-repo", 0, 20);

      assertThat(result.content()).isEmpty();
    }

    @Test
    @DisplayName("non-owner non-collaborator is denied")
    void list_throwsAccessDenied_forStranger() {
      final var strangerId = UUID.randomUUID();
      when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
          .thenReturn(Optional.of(repo));
      when(collaboratorRepository.existsByRepoIdAndTenantIdAndStatus(
              REPO_ID, strangerId, CollaboratorStatus.ACCEPTED))
          .thenReturn(false);

      assertThatThrownBy(() -> collaboratorService.list(strangerId, "owner", "my-repo", 0, 20))
          .isInstanceOf(AccessNotAllowedException.class);
    }
  }

  @Nested
  @DisplayName("hasPermission — detailed permission semantics")
  class PermissionSemanticTests {

    @Test
    @DisplayName("each individual permission is independently honoured")
    void eachPermission_isIndependentlyHonoured() {
      for (final CollaboratorPermission perm : CollaboratorPermission.values()) {
        final var c = collaborator(CollaboratorStatus.ACCEPTED, EnumSet.of(perm));
        when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
            .thenReturn(Optional.of(c));

        assertThat(collaboratorService.hasPermission(REPO_ID, INVITEE_ID, perm))
            .as("expected %s to be granted when explicitly in permissions set", perm)
            .isTrue();
      }
    }

    @Test
    @DisplayName("ADMIN grants every specific permission implicitly")
    void admin_grantsEveryPermission() {
      final var c =
          collaborator(CollaboratorStatus.ACCEPTED, EnumSet.of(CollaboratorPermission.ADMIN));
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.of(c));

      for (final CollaboratorPermission perm : CollaboratorPermission.values()) {
        assertThat(collaboratorService.hasPermission(REPO_ID, INVITEE_ID, perm))
            .as("ADMIN should imply %s", perm)
            .isTrue();
      }
    }

    @Test
    @DisplayName("READ-only collaborator is denied every write permission")
    void readOnly_deniedEveryWritePermission() {
      final var c =
          collaborator(CollaboratorStatus.ACCEPTED, EnumSet.of(CollaboratorPermission.READ));
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.of(c));

      for (final CollaboratorPermission perm :
          EnumSet.of(
              CollaboratorPermission.PUSH,
              CollaboratorPermission.PULL_REQUEST_CREATE,
              CollaboratorPermission.PULL_REQUEST_REVIEW,
              CollaboratorPermission.PULL_REQUEST_MERGE,
              CollaboratorPermission.ISSUE_MANAGE,
              CollaboratorPermission.SETTINGS_READ,
              CollaboratorPermission.SETTINGS_WRITE,
              CollaboratorPermission.ADMIN)) {
        assertThat(collaboratorService.hasPermission(REPO_ID, INVITEE_ID, perm))
            .as("READ-only should NOT imply %s", perm)
            .isFalse();
      }
    }

    @Test
    @DisplayName("DECLINED collaborator has no permissions despite stored permissions")
    void declined_hasNoPermissions() {
      final var c =
          collaborator(CollaboratorStatus.DECLINED, EnumSet.allOf(CollaboratorPermission.class));
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.of(c));

      for (final CollaboratorPermission perm : CollaboratorPermission.values()) {
        assertThat(collaboratorService.hasPermission(REPO_ID, INVITEE_ID, perm))
            .as("DECLINED collaborator should have no %s", perm)
            .isFalse();
      }
    }

    @Test
    @DisplayName("PENDING collaborator has no permissions despite stored permissions")
    void pending_hasNoPermissions() {
      final var c =
          collaborator(CollaboratorStatus.PENDING, EnumSet.allOf(CollaboratorPermission.class));
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.of(c));

      assertThat(
              collaboratorService.hasPermission(REPO_ID, INVITEE_ID, CollaboratorPermission.PUSH))
          .isFalse();
    }

    @Test
    @DisplayName("PUSH permission does not grant PULL_REQUEST_CREATE")
    void push_doesNotImplyPrCreate() {
      final var c =
          collaborator(
              CollaboratorStatus.ACCEPTED,
              EnumSet.of(CollaboratorPermission.READ, CollaboratorPermission.PUSH));
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.of(c));

      assertThat(
              collaboratorService.hasPermission(
                  REPO_ID, INVITEE_ID, CollaboratorPermission.PULL_REQUEST_CREATE))
          .isFalse();
    }

    @Test
    @DisplayName("PULL_REQUEST_MERGE does not grant ISSUE_MANAGE")
    void prMerge_doesNotImplyIssueManage() {
      final var c =
          collaborator(
              CollaboratorStatus.ACCEPTED,
              EnumSet.of(
                  CollaboratorPermission.READ,
                  CollaboratorPermission.PULL_REQUEST_CREATE,
                  CollaboratorPermission.PULL_REQUEST_REVIEW,
                  CollaboratorPermission.PULL_REQUEST_MERGE));
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.of(c));

      assertThat(
              collaboratorService.hasPermission(
                  REPO_ID, INVITEE_ID, CollaboratorPermission.ISSUE_MANAGE))
          .isFalse();
    }

    @Test
    @DisplayName(
        "SETTINGS_WRITE does not automatically imply SETTINGS_READ via hasPermission — READ must be explicitly granted")
    void settingsWrite_doesNotImplySettingsRead_viaHasPermission() {
      final var c =
          collaborator(
              CollaboratorStatus.ACCEPTED,
              EnumSet.of(CollaboratorPermission.READ, CollaboratorPermission.SETTINGS_WRITE));
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.of(c));

      assertThat(
              collaboratorService.hasPermission(
                  REPO_ID, INVITEE_ID, CollaboratorPermission.SETTINGS_WRITE))
          .isTrue();
      assertThat(
              collaboratorService.hasPermission(
                  REPO_ID, INVITEE_ID, CollaboratorPermission.SETTINGS_READ))
          .isFalse();
    }
  }

  @Nested
  @DisplayName("resolvePermissions — READ always included")
  class ResolvePermissionsTests {

    @Test
    @DisplayName("empty permission set resolves to READ only")
    void emptySet_resolvesToReadOnly() {
      when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
          .thenReturn(Optional.of(repo));
      when(tenantRepository.findByUsername("invitee")).thenReturn(Optional.of(invitee));
      when(collaboratorRepository.existsByRepoIdAndTenantIdAndStatus(any(), any(), any()))
          .thenReturn(false);
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.empty());
      when(collaboratorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(collaboratorMapper.toInfo(any())).thenReturn(info(CollaboratorStatus.PENDING));

      collaboratorService.invite(OWNER_ID, "owner", "my-repo", form("invitee", Set.of()));

      verify(collaboratorRepository)
          .save(
              argThat(
                  c ->
                      c.getPermissions().contains(CollaboratorPermission.READ)
                          && c.getPermissions().size() == 1));
    }

    @Test
    @DisplayName("ADMIN permission set includes READ automatically")
    void admin_alwaysContainsRead() {
      when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
          .thenReturn(Optional.of(repo));
      when(tenantRepository.findByUsername("invitee")).thenReturn(Optional.of(invitee));
      when(collaboratorRepository.existsByRepoIdAndTenantIdAndStatus(any(), any(), any()))
          .thenReturn(false);
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.empty());
      when(collaboratorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(collaboratorMapper.toInfo(any())).thenReturn(info(CollaboratorStatus.PENDING));

      collaboratorService.invite(
          OWNER_ID, "owner", "my-repo", form("invitee", Set.of(CollaboratorPermission.ADMIN)));

      verify(collaboratorRepository)
          .save(
              argThat(
                  c ->
                      c.getPermissions().contains(CollaboratorPermission.READ)
                          && c.getPermissions().contains(CollaboratorPermission.ADMIN)));
    }
  }

  @Nested
  @DisplayName("generateInviteLink")
  class GenerateInviteLinkTests {

    @Test
    @DisplayName("generates token with 7-day expiry for pending collaborator")
    void generate_createsTokenWithExpiry() {
      final var existing =
          collaborator(CollaboratorStatus.PENDING, Set.of(CollaboratorPermission.READ));
      when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
          .thenReturn(Optional.of(repo));
      when(tenantRepository.findByUsername("invitee")).thenReturn(Optional.of(invitee));
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.of(existing));
      when(collaboratorRepository.save(any())).thenReturn(existing);

      final var result =
          collaboratorService.generateInviteLink(OWNER_ID, "owner", "my-repo", "invitee");

      assertThat(result.token()).isNotBlank();
      assertThat(result.expiresAt()).isAfter(Instant.now().plus(6, ChronoUnit.DAYS));
      assertThat(result.repoOwner()).isEqualTo("owner");
      assertThat(result.repoName()).isEqualTo("my-repo");
      assertThat(result.inviteeUsername()).isEqualTo("invitee");

      verify(collaboratorRepository)
          .save(argThat(c -> c.getInviteToken() != null && c.getTokenExpiresAt() != null));
    }

    @Test
    @DisplayName("throws AccessNotAllowedException when requester is not owner")
    void generate_throwsAccessDenied_whenNotOwner() {
      when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
          .thenReturn(Optional.of(repo));

      assertThatThrownBy(
              () ->
                  collaboratorService.generateInviteLink(INVITEE_ID, "owner", "my-repo", "invitee"))
          .isInstanceOf(AccessNotAllowedException.class);
    }

    @Test
    @DisplayName("throws ItemNotFoundException when collaborator does not exist")
    void generate_throwsNotFound_whenNoCollaborator() {
      when(repoRepository.findByOwnerUsernameAndName("owner", "my-repo"))
          .thenReturn(Optional.of(repo));
      when(tenantRepository.findByUsername("invitee")).thenReturn(Optional.of(invitee));
      when(collaboratorRepository.findByRepoIdAndTenantId(REPO_ID, INVITEE_ID))
          .thenReturn(Optional.empty());

      assertThatThrownBy(
              () -> collaboratorService.generateInviteLink(OWNER_ID, "owner", "my-repo", "invitee"))
          .isInstanceOf(ItemNotFoundException.class)
          .hasMessageContaining("collaboratorNotFound");
    }
  }

  @Nested
  @DisplayName("acceptViaToken")
  class AcceptViaTokenTests {

    @Test
    @DisplayName("accepts invitation via valid token and clears the token")
    void accept_setsAcceptedAndClearsToken() {
      final var existing =
          collaborator(CollaboratorStatus.PENDING, Set.of(CollaboratorPermission.READ));
      existing.setInviteToken("valid-token");
      existing.setTokenExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));

      when(collaboratorRepository.findByInviteToken("valid-token"))
          .thenReturn(Optional.of(existing));
      when(collaboratorRepository.save(any())).thenReturn(existing);
      when(collaboratorMapper.toInfo(any())).thenReturn(info(CollaboratorStatus.ACCEPTED));

      final var result = collaboratorService.acceptViaToken(INVITEE_ID, "valid-token");

      assertThat(existing.getStatus()).isEqualTo(CollaboratorStatus.ACCEPTED);
      assertThat(existing.getInviteToken()).isNull();
      assertThat(existing.getTokenExpiresAt()).isNull();
      assertThat(result.getStatus()).isEqualTo(CollaboratorStatus.ACCEPTED);
    }

    @Test
    @DisplayName("throws BadRequestException when token is expired")
    void accept_throwsBadRequest_whenExpired() {
      final var existing =
          collaborator(CollaboratorStatus.PENDING, Set.of(CollaboratorPermission.READ));
      existing.setInviteToken("expired-token");
      existing.setTokenExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));

      when(collaboratorRepository.findByInviteToken("expired-token"))
          .thenReturn(Optional.of(existing));

      assertThatThrownBy(() -> collaboratorService.acceptViaToken(INVITEE_ID, "expired-token"))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("invitationLinkExpired");
    }

    @Test
    @DisplayName("throws AccessNotAllowedException when token belongs to different user")
    void accept_throwsAccessDenied_forWrongUser() {
      final var existing =
          collaborator(CollaboratorStatus.PENDING, Set.of(CollaboratorPermission.READ));
      existing.setInviteToken("some-token");
      existing.setTokenExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));

      when(collaboratorRepository.findByInviteToken("some-token"))
          .thenReturn(Optional.of(existing));

      final var wrongUserId = UUID.randomUUID();
      assertThatThrownBy(() -> collaboratorService.acceptViaToken(wrongUserId, "some-token"))
          .isInstanceOf(AccessNotAllowedException.class)
          .hasMessageContaining("invitationNotForYou");
    }

    @Test
    @DisplayName("throws BadRequestException when invitation is already accepted")
    void accept_throwsBadRequest_whenAlreadyAccepted() {
      final var existing =
          collaborator(CollaboratorStatus.ACCEPTED, Set.of(CollaboratorPermission.READ));
      existing.setInviteToken("used-token");
      existing.setTokenExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));

      when(collaboratorRepository.findByInviteToken("used-token"))
          .thenReturn(Optional.of(existing));

      assertThatThrownBy(() -> collaboratorService.acceptViaToken(INVITEE_ID, "used-token"))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("invitationAlreadyHandled");
    }

    @Test
    @DisplayName("throws ItemNotFoundException when token does not exist")
    void accept_throwsNotFound_whenTokenMissing() {
      when(collaboratorRepository.findByInviteToken("ghost-token")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> collaboratorService.acceptViaToken(INVITEE_ID, "ghost-token"))
          .isInstanceOf(ItemNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("getInvitationByToken")
  class GetInvitationByTokenTests {

    @Test
    @DisplayName("returns invitation info for valid non-expired token")
    void get_returnsInfo_forValidToken() {
      final var existing =
          collaborator(CollaboratorStatus.PENDING, Set.of(CollaboratorPermission.READ));
      existing.setInviteToken("valid-token");
      existing.setTokenExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));

      when(collaboratorRepository.findByInviteToken("valid-token"))
          .thenReturn(Optional.of(existing));

      final var result = collaboratorService.getInvitationByToken("valid-token");

      assertThat(result.repoOwner()).isEqualTo("owner");
      assertThat(result.repoName()).isEqualTo("my-repo");
      assertThat(result.invitedUsername()).isEqualTo("invitee");
      assertThat(result.invitedBy()).isEqualTo("owner");
    }

    @Test
    @DisplayName("throws BadRequestException when token has expired")
    void get_throwsBadRequest_whenExpired() {
      final var existing =
          collaborator(CollaboratorStatus.PENDING, Set.of(CollaboratorPermission.READ));
      existing.setInviteToken("old-token");
      existing.setTokenExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));

      when(collaboratorRepository.findByInviteToken("old-token")).thenReturn(Optional.of(existing));

      assertThatThrownBy(() -> collaboratorService.getInvitationByToken("old-token"))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("invitationLinkExpired");
    }

    @Test
    @DisplayName("throws ItemNotFoundException for unknown token")
    void get_throwsNotFound_forUnknownToken() {
      when(collaboratorRepository.findByInviteToken("unknown")).thenReturn(Optional.empty());

      assertThatThrownBy(() -> collaboratorService.getInvitationByToken("unknown"))
          .isInstanceOf(ItemNotFoundException.class);
    }
  }

  // --- helpers ---

  private InviteCollaboratorForm form(
      final String username, final Set<CollaboratorPermission> perms) {
    final var f = new InviteCollaboratorForm();
    f.setUsername(username);
    f.setPermissions(perms);
    return f;
  }

  private RepoCollaborator collaborator(
      final CollaboratorStatus status, final Set<CollaboratorPermission> perms) {
    final var c = new RepoCollaborator();
    c.setId(UUID.randomUUID());
    c.setRepo(repo);
    c.setTenant(invitee);
    c.setInvitedBy(owner);
    c.setStatus(status);
    c.setPermissions(
        perms.isEmpty() ? EnumSet.noneOf(CollaboratorPermission.class) : EnumSet.copyOf(perms));
    c.setCreatedAt(Instant.now());
    c.setUpdatedAt(Instant.now());
    return c;
  }

  private CollaboratorInfo info(final CollaboratorStatus status) {
    return new CollaboratorInfo(
        UUID.randomUUID(),
        invitee.getUsername(),
        invitee.getDisplayName(),
        "https://gravatar",
        Set.of(CollaboratorPermission.READ),
        status,
        owner.getUsername(),
        Instant.now(),
        Instant.now());
  }
}
