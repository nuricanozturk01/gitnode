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
package dev.gitnode.os.repo.services;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.identity.entities.Tenant;
import dev.gitnode.os.repo.api.CollaboratorAccessPort;
import dev.gitnode.os.repo.api.CollaboratorPermission;
import dev.gitnode.os.repo.entities.Repo;
import dev.gitnode.os.repo.repositories.RepoRepository;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RepoService permission assertions")
class RepoServicePermissionTest {

  private static final UUID OWNER_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();

  @Mock private RepoRepository repoRepository;
  @Mock private TenantQueryPort tenantRepository;
  @Mock private CollaboratorAccessPort collaboratorAccessPort;

  @InjectMocks private RepoService repoService;

  private Repo repo;

  @BeforeEach
  void setUp() {
    final var owner = new Tenant();
    owner.setId(OWNER_ID);
    owner.setUsername("alice");
    repo = new Repo();
    repo.setId(UUID.randomUUID());
    repo.setName("repo");
    repo.setOwnerId(owner.getId());
    repo.setPrivate(true);
    when(repoRepository.findByOwnerUsernameAndName("alice", "repo")).thenReturn(Optional.of(repo));
    when(tenantRepository.findByUsername("alice")).thenReturn(Optional.of(ref(owner)));
  }

  @Test
  @DisplayName("granted permission makes one hasPermission call and no isActiveCollaborator call")
  void assertUserHasPermission_granted_singleLookup() {
    when(collaboratorAccessPort.hasPermission(repo.getId(), USER_ID, CollaboratorPermission.PUSH))
        .thenReturn(true);

    assertThatNoException()
        .isThrownBy(
            () ->
                repoService.assertUserHasPermission(
                    USER_ID, "alice", "repo", CollaboratorPermission.PUSH));

    verify(collaboratorAccessPort, times(1)).hasPermission(any(), any(), any());
    verify(collaboratorAccessPort, never()).isActiveCollaborator(any(), any());
  }

  @Test
  @DisplayName("non-member is denied with repoAccessDenied")
  void assertUserHasPermission_nonMember_repoAccessDenied() {
    when(collaboratorAccessPort.hasPermission(repo.getId(), USER_ID, CollaboratorPermission.PUSH))
        .thenReturn(false);
    when(collaboratorAccessPort.isActiveCollaborator(repo.getId(), USER_ID)).thenReturn(false);

    assertThatThrownBy(
            () ->
                repoService.assertUserHasPermission(
                    USER_ID, "alice", "repo", CollaboratorPermission.PUSH))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining("repoAccessDenied");
  }

  @Test
  @DisplayName("member without the permission is denied with insufficientPermissions")
  void assertUserHasPermission_member_insufficientPermissions() {
    when(collaboratorAccessPort.hasPermission(repo.getId(), USER_ID, CollaboratorPermission.PUSH))
        .thenReturn(false);
    when(collaboratorAccessPort.isActiveCollaborator(repo.getId(), USER_ID)).thenReturn(true);

    assertThatThrownBy(
            () ->
                repoService.assertUserHasPermission(
                    USER_ID, "alice", "repo", CollaboratorPermission.PUSH))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining("insufficientPermissions");
  }

  @Test
  @DisplayName("any-permission grant stops at the first match without isActiveCollaborator")
  void assertUserHasAnyPermission_granted_noMembershipLookup() {
    when(collaboratorAccessPort.hasPermission(
            repo.getId(), USER_ID, CollaboratorPermission.SETTINGS_READ))
        .thenReturn(true);

    assertThatNoException()
        .isThrownBy(
            () ->
                repoService.assertUserHasAnyPermission(
                    USER_ID,
                    "alice",
                    "repo",
                    CollaboratorPermission.SETTINGS_READ,
                    CollaboratorPermission.SETTINGS_WRITE));

    verify(collaboratorAccessPort, times(1)).hasPermission(any(), any(), any());
    verify(collaboratorAccessPort, never()).isActiveCollaborator(any(), any());
  }

  @Test
  @DisplayName("any-permission keeps repoAccessDenied and insufficientPermissions codes")
  void assertUserHasAnyPermission_errorCodesUnchanged() {
    when(collaboratorAccessPort.hasPermission(any(), any(), any())).thenReturn(false);
    when(collaboratorAccessPort.isActiveCollaborator(repo.getId(), USER_ID))
        .thenReturn(false)
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                repoService.assertUserHasAnyPermission(
                    USER_ID, "alice", "repo", CollaboratorPermission.SETTINGS_READ))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining("repoAccessDenied");
    assertThatThrownBy(
            () ->
                repoService.assertUserHasAnyPermission(
                    USER_ID, "alice", "repo", CollaboratorPermission.SETTINGS_READ))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining("insufficientPermissions");
  }

  @Test
  @DisplayName("owner short-circuits without collaborator lookups")
  void assertUserHasPermission_owner_noLookups() {
    assertThatNoException()
        .isThrownBy(
            () ->
                repoService.assertUserHasPermission(
                    OWNER_ID, "alice", "repo", CollaboratorPermission.ADMIN));

    verify(collaboratorAccessPort, never()).hasPermission(any(), any(), any());
    verify(collaboratorAccessPort, never()).isActiveCollaborator(any(), any());
  }

  private static dev.gitnode.os.identity.api.TenantQueryPort.TenantRef ref(final Tenant t) {
    return new dev.gitnode.os.identity.api.TenantQueryPort.TenantRef(
        t.getId(), t.getUsername(), t.getEmail() == null ? "" : t.getEmail(), null, null, true);
  }
}
