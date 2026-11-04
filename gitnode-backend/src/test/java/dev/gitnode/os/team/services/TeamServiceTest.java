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
package dev.gitnode.os.team.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.events.team.TeamInvitationClosedEvent;
import dev.gitnode.os.events.team.TeamInvitedEvent;
import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemAlreadyExistsException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.team.api.TeamRole;
import dev.gitnode.os.team.entities.TeamMember;
import dev.gitnode.os.team.entities.TeamMemberStatus;
import dev.gitnode.os.team.repositories.TeamMemberRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeamService unit tests")
class TeamServiceTest {

  @Mock private TeamMemberRepository repository;
  @Mock private TenantQueryPort tenants;
  @Mock private ApplicationEventPublisher events;

  private TeamService service;
  private final UUID ownerId = UUID.randomUUID();
  private final UUID memberId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    this.service = new TeamService(this.repository, this.tenants, this.events);
  }

  private static TenantRef ref(final UUID id, final String username, final boolean enabled) {
    return new TenantRef(id, username, username + "@x.dev", null, null, enabled);
  }

  private TeamMember row(final TeamRole role, final TeamMemberStatus status) {
    final var member = new TeamMember();
    member.setId(UUID.randomUUID());
    member.setOwnerId(this.ownerId);
    member.setMemberId(this.memberId);
    member.setRole(role);
    member.setStatus(status);
    member.setCreatedAt(Instant.now());
    return member;
  }

  private void namesResolve() {
    when(this.tenants.findAllByIds(any()))
        .thenReturn(
            Map.of(
                this.ownerId, ref(this.ownerId, "ali", true),
                this.memberId, ref(this.memberId, "bob", true)));
  }

  private void savesEcho() {
    when(this.repository.saveAndFlush(any(TeamMember.class)))
        .thenAnswer(
            inv -> {
              final TeamMember saved = inv.getArgument(0);
              if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
                saved.setCreatedAt(Instant.now());
              }
              return saved;
            });
  }

  @Nested
  @DisplayName("invite")
  class Invite {

    @Test
    @DisplayName("creates a pending row in the caller's team and publishes the event")
    void invite_createsPendingRowAndPublishesEvent() {
      when(tenants.findByUsernameIgnoreCase("Bob"))
          .thenReturn(Optional.of(ref(memberId, "bob", true)));
      when(repository.findByOwnerIdAndMemberId(ownerId, memberId)).thenReturn(Optional.empty());
      savesEcho();
      namesResolve();

      final var info = service.invite(ownerId, "Bob", TeamRole.DEVELOPER);

      final var captor = ArgumentCaptor.forClass(TeamMember.class);
      verify(repository).saveAndFlush(captor.capture());
      assertThat(captor.getValue().getOwnerId()).isEqualTo(ownerId);
      assertThat(captor.getValue().getMemberId()).isEqualTo(memberId);
      assertThat(captor.getValue().getStatus()).isEqualTo(TeamMemberStatus.PENDING);
      assertThat(info.owner().username()).isEqualTo("ali");
      assertThat(info.member().username()).isEqualTo("bob");
      final var event = ArgumentCaptor.forClass(TeamInvitedEvent.class);
      verify(events).publishEvent(event.capture());
      assertThat(event.getValue().memberId()).isEqualTo(memberId);
      assertThat(event.getValue().ownerId()).isEqualTo(ownerId);
      assertThat(event.getValue().role()).isEqualTo("DEVELOPER");
    }

    @Test
    @DisplayName("self invite is a bad request")
    void invite_selfIsBadRequest() {
      when(tenants.findByUsernameIgnoreCase("ali"))
          .thenReturn(Optional.of(ref(ownerId, "ali", true)));

      assertThatThrownBy(() -> service.invite(ownerId, "ali", TeamRole.VIEWER))
          .isInstanceOf(BadRequestException.class);
      verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("unknown and disabled users share the same not-found error")
    void invite_unknownAndDisabledShareNotFound() {
      when(tenants.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());
      when(tenants.findByUsernameIgnoreCase("off"))
          .thenReturn(Optional.of(ref(UUID.randomUUID(), "off", false)));

      assertThatThrownBy(() -> service.invite(ownerId, "ghost", TeamRole.VIEWER))
          .isInstanceOf(ItemNotFoundException.class)
          .hasMessage("userNotFound");
      assertThatThrownBy(() -> service.invite(ownerId, "off", TeamRole.VIEWER))
          .isInstanceOf(ItemNotFoundException.class)
          .hasMessage("userNotFound");
    }

    @Test
    @DisplayName("an existing row in the caller's team is a conflict")
    void invite_existingIsConflict() {
      when(tenants.findByUsernameIgnoreCase("bob"))
          .thenReturn(Optional.of(ref(memberId, "bob", true)));
      when(repository.findByOwnerIdAndMemberId(ownerId, memberId))
          .thenReturn(Optional.of(row(TeamRole.VIEWER, TeamMemberStatus.PENDING)));

      assertThatThrownBy(() -> service.invite(ownerId, "bob", TeamRole.VIEWER))
          .isInstanceOf(ItemAlreadyExistsException.class);
    }

    @Test
    @DisplayName("a concurrent duplicate insert maps to conflict")
    void invite_concurrentDuplicateIsConflict() {
      when(tenants.findByUsernameIgnoreCase("bob"))
          .thenReturn(Optional.of(ref(memberId, "bob", true)));
      when(repository.findByOwnerIdAndMemberId(ownerId, memberId)).thenReturn(Optional.empty());
      when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

      assertThatThrownBy(() -> service.invite(ownerId, "bob", TeamRole.VIEWER))
          .isInstanceOf(ItemAlreadyExistsException.class);
      verify(events, never()).publishEvent(any());
    }
  }

  @Nested
  @DisplayName("invitee actions")
  class InviteeActions {

    @Test
    @DisplayName("accept turns the invitation into an accepted membership")
    void accept_marksAccepted() {
      final var invitation = row(TeamRole.VIEWER, TeamMemberStatus.PENDING);
      when(repository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
      savesEcho();
      namesResolve();

      final var info = service.accept(memberId, invitation.getId());

      assertThat(info.status()).isEqualTo(TeamMemberStatus.ACCEPTED);
      assertThat(info.acceptedAt()).isNotNull();
      verify(events).publishEvent(new TeamInvitationClosedEvent(invitation.getId(), memberId));
    }

    @Test
    @DisplayName("only the invitee can accept or decline")
    void acceptDecline_onlyInvitee() {
      final var invitation = row(TeamRole.VIEWER, TeamMemberStatus.PENDING);
      when(repository.findById(invitation.getId())).thenReturn(Optional.of(invitation));

      assertThatThrownBy(() -> service.accept(ownerId, invitation.getId()))
          .isInstanceOf(ItemNotFoundException.class);
      assertThatThrownBy(() -> service.decline(UUID.randomUUID(), invitation.getId()))
          .isInstanceOf(ItemNotFoundException.class);
      verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("an accepted membership cannot be accepted again")
    void accept_acceptedIsNotFound() {
      final var accepted = row(TeamRole.VIEWER, TeamMemberStatus.ACCEPTED);
      when(repository.findById(accepted.getId())).thenReturn(Optional.of(accepted));

      assertThatThrownBy(() -> service.accept(memberId, accepted.getId()))
          .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    @DisplayName("decline deletes the invitation")
    void decline_deletes() {
      final var invitation = row(TeamRole.VIEWER, TeamMemberStatus.PENDING);
      when(repository.findById(invitation.getId())).thenReturn(Optional.of(invitation));

      service.decline(memberId, invitation.getId());

      verify(repository).delete(invitation);
      verify(events).publishEvent(new TeamInvitationClosedEvent(invitation.getId(), memberId));
    }

    @Test
    @DisplayName("leave deletes only the caller's accepted membership")
    void leave_deletesOwnAcceptedMembership() {
      final var accepted = row(TeamRole.DEVELOPER, TeamMemberStatus.ACCEPTED);
      when(repository.findById(accepted.getId())).thenReturn(Optional.of(accepted));

      assertThatThrownBy(() -> service.leave(ownerId, accepted.getId()))
          .isInstanceOf(ItemNotFoundException.class);
      service.leave(memberId, accepted.getId());

      verify(repository, times(1)).delete(accepted);
      verify(events, never()).publishEvent(any(TeamInvitationClosedEvent.class));
    }

    @Test
    @DisplayName("a concurrent delete maps to not found")
    void decline_concurrentDeleteIsNotFound() {
      final var invitation = row(TeamRole.VIEWER, TeamMemberStatus.PENDING);
      when(repository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
      org.mockito.Mockito.doThrow(
              new ObjectOptimisticLockingFailureException(TeamMember.class, invitation.getId()))
          .when(repository)
          .flush();

      assertThatThrownBy(() -> service.decline(memberId, invitation.getId()))
          .isInstanceOf(ItemNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("owner actions")
  class OwnerActions {

    @Test
    @DisplayName("change role updates the accepted member of the caller's team")
    void changeRole_updatesRole() {
      final var accepted = row(TeamRole.VIEWER, TeamMemberStatus.ACCEPTED);
      when(repository.findById(accepted.getId())).thenReturn(Optional.of(accepted));
      savesEcho();
      namesResolve();

      final var info = service.changeRole(ownerId, accepted.getId(), TeamRole.MAINTAINER);

      assertThat(info.role()).isEqualTo(TeamRole.MAINTAINER);
    }

    @Test
    @DisplayName("another owner cannot change, remove or cancel rows of this team")
    void otherOwner_cannotTouchRows() {
      final var accepted = row(TeamRole.VIEWER, TeamMemberStatus.ACCEPTED);
      final var pending = row(TeamRole.VIEWER, TeamMemberStatus.PENDING);
      when(repository.findById(accepted.getId())).thenReturn(Optional.of(accepted));
      when(repository.findById(pending.getId())).thenReturn(Optional.of(pending));
      final var stranger = UUID.randomUUID();

      assertThatThrownBy(() -> service.changeRole(stranger, accepted.getId(), TeamRole.ADMIN))
          .isInstanceOf(ItemNotFoundException.class);
      assertThatThrownBy(() -> service.remove(stranger, accepted.getId()))
          .isInstanceOf(ItemNotFoundException.class);
      assertThatThrownBy(() -> service.cancelInvitation(stranger, pending.getId()))
          .isInstanceOf(ItemNotFoundException.class);
      assertThatThrownBy(() -> service.changeRole(memberId, accepted.getId(), TeamRole.ADMIN))
          .isInstanceOf(ItemNotFoundException.class);
      verify(repository, never()).delete(any());
      verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("cancel removes a pending invitation but not an accepted member")
    void cancel_onlyPending() {
      final var pending = row(TeamRole.VIEWER, TeamMemberStatus.PENDING);
      final var accepted = row(TeamRole.VIEWER, TeamMemberStatus.ACCEPTED);
      when(repository.findById(pending.getId())).thenReturn(Optional.of(pending));
      when(repository.findById(accepted.getId())).thenReturn(Optional.of(accepted));

      service.cancelInvitation(ownerId, pending.getId());
      assertThatThrownBy(() -> service.cancelInvitation(ownerId, accepted.getId()))
          .isInstanceOf(ItemNotFoundException.class);

      verify(repository).delete(pending);
      verify(events).publishEvent(new TeamInvitationClosedEvent(pending.getId(), memberId));
      verify(events, times(1)).publishEvent(any(TeamInvitationClosedEvent.class));
    }

    @Test
    @DisplayName("remove of a pending invitation closes it, of an accepted member does not")
    void remove_pendingPublishesClosedAcceptedDoesNot() {
      final var pending = row(TeamRole.VIEWER, TeamMemberStatus.PENDING);
      final var accepted = row(TeamRole.VIEWER, TeamMemberStatus.ACCEPTED);
      when(repository.findById(pending.getId())).thenReturn(Optional.of(pending));
      when(repository.findById(accepted.getId())).thenReturn(Optional.of(accepted));

      service.remove(ownerId, accepted.getId());
      verify(events, never()).publishEvent(any(TeamInvitationClosedEvent.class));
      service.remove(ownerId, pending.getId());

      verify(events).publishEvent(new TeamInvitationClosedEvent(pending.getId(), memberId));
    }

    @Test
    @DisplayName("remove deletes a member of the caller's team, even an ADMIN")
    void remove_deletesAdminMember() {
      final var admin = row(TeamRole.ADMIN, TeamMemberStatus.ACCEPTED);
      when(repository.findById(admin.getId())).thenReturn(Optional.of(admin));

      service.remove(ownerId, admin.getId());

      verify(repository).delete(admin);
    }

    @Test
    @DisplayName("a concurrent role change on a removed row maps to not found")
    void changeRole_optimisticLockIsNotFound() {
      final var accepted = row(TeamRole.VIEWER, TeamMemberStatus.ACCEPTED);
      when(repository.findById(accepted.getId())).thenReturn(Optional.of(accepted));
      when(repository.saveAndFlush(any()))
          .thenThrow(new ObjectOptimisticLockingFailureException(TeamMember.class, "x"));

      assertThatThrownBy(() -> service.changeRole(ownerId, accepted.getId(), TeamRole.ADMIN))
          .isInstanceOf(ItemNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("listing")
  class Listing {

    @Test
    @DisplayName("members are paged by owner, size clamped and names resolved in one batch")
    void members_pagedClampedAndBatchResolved() {
      final var rows =
          List.of(
              row(TeamRole.VIEWER, TeamMemberStatus.ACCEPTED),
              row(TeamRole.ADMIN, TeamMemberStatus.ACCEPTED));
      final var pageable = ArgumentCaptor.forClass(Pageable.class);
      when(repository.findAllByOwnerIdAndStatus(
              org.mockito.ArgumentMatchers.eq(ownerId),
              org.mockito.ArgumentMatchers.eq(TeamMemberStatus.ACCEPTED),
              pageable.capture()))
          .thenAnswer(inv -> new PageImpl<>(rows, inv.getArgument(2), 2));
      namesResolve();

      final var page = service.members(ownerId, TeamMemberStatus.ACCEPTED, -3, 100000);

      assertThat(pageable.getValue().getPageNumber()).isZero();
      assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
      assertThat(page.content()).hasSize(2);
      final ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.captor();
      verify(tenants, times(1)).findAllByIds(ids.capture());
      assertThat(ids.getValue()).containsExactlyInAnyOrder(ownerId, memberId);
    }

    @Test
    @DisplayName("size below one is clamped to one")
    void members_sizeClampedToOne() {
      final var pageable = ArgumentCaptor.forClass(Pageable.class);
      when(repository.findAllByOwnerIdAndStatus(
              org.mockito.ArgumentMatchers.eq(ownerId),
              org.mockito.ArgumentMatchers.eq(TeamMemberStatus.PENDING),
              pageable.capture()))
          .thenAnswer(inv -> new PageImpl<TeamMember>(List.of(), inv.getArgument(2), 0));

      service.members(ownerId, TeamMemberStatus.PENDING, 0, 0);

      assertThat(pageable.getValue().getPageSize()).isEqualTo(1);
      verify(tenants, never()).findAllByIds(any());
    }

    @Test
    @DisplayName("memberships list accepted rows and invitations list pending rows of the caller")
    void membershipsAndInvitations_queryByMemberAndStatus() {
      when(repository.findAllByMemberIdAndStatus(
              org.mockito.ArgumentMatchers.eq(memberId),
              org.mockito.ArgumentMatchers.any(TeamMemberStatus.class),
              any(Pageable.class)))
          .thenAnswer(inv -> new PageImpl<TeamMember>(List.of(), inv.getArgument(2), 0));

      service.memberships(memberId, 0, 20);
      service.myInvitations(memberId, 0, 20);

      verify(repository)
          .findAllByMemberIdAndStatus(
              org.mockito.ArgumentMatchers.eq(memberId),
              org.mockito.ArgumentMatchers.eq(TeamMemberStatus.ACCEPTED),
              any(Pageable.class));
      verify(repository)
          .findAllByMemberIdAndStatus(
              org.mockito.ArgumentMatchers.eq(memberId),
              org.mockito.ArgumentMatchers.eq(TeamMemberStatus.PENDING),
              any(Pageable.class));
    }

    @Test
    @DisplayName("me counts pending invitations")
    void me_countsPending() {
      when(repository.countByMemberIdAndStatus(memberId, TeamMemberStatus.PENDING)).thenReturn(3L);

      assertThat(service.me(memberId).pendingInvitations()).isEqualTo(3);
    }
  }
}
