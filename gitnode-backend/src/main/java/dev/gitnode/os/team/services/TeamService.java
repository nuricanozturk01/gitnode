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

import dev.gitnode.os.events.team.TeamInvitationClosedEvent;
import dev.gitnode.os.events.team.TeamInvitedEvent;
import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.repo.dtos.PageResponse;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemAlreadyExistsException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.team.api.TeamRole;
import dev.gitnode.os.team.dtos.TeamMeInfo;
import dev.gitnode.os.team.dtos.TeamMemberInfo;
import dev.gitnode.os.team.dtos.TeamUserInfo;
import dev.gitnode.os.team.entities.TeamMember;
import dev.gitnode.os.team.entities.TeamMemberStatus;
import dev.gitnode.os.team.repositories.TeamMemberRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NullMarked
public class TeamService {

  private static final int MAX_PAGE_SIZE = 50;
  private static final String MEMBER_NOT_FOUND = "teamMemberNotFound";
  private static final String INVITATION_NOT_FOUND = "teamInvitationNotFound";

  private final TeamMemberRepository repository;
  private final TenantQueryPort tenants;
  private final ApplicationEventPublisher events;

  @Transactional
  public TeamMemberInfo invite(final UUID ownerId, final String username, final TeamRole role) {
    final var invitee =
        this.tenants
            .findByUsernameIgnoreCase(username)
            .filter(TenantRef::enabled)
            .orElseThrow(() -> new ItemNotFoundException("userNotFound"));
    if (invitee.id().equals(ownerId)) {
      throw new BadRequestException("teamSelfInvite");
    }
    if (this.repository.findByOwnerIdAndMemberId(ownerId, invitee.id()).isPresent()) {
      throw new ItemAlreadyExistsException("teamMemberExists");
    }
    final var member = new TeamMember();
    member.setOwnerId(ownerId);
    member.setMemberId(invitee.id());
    member.setRole(role);
    member.setStatus(TeamMemberStatus.PENDING);
    final TeamMember saved;
    try {
      saved = this.repository.saveAndFlush(member);
    } catch (final DataIntegrityViolationException ex) {
      throw new ItemAlreadyExistsException("teamMemberExists");
    }
    this.events.publishEvent(
        new TeamInvitedEvent(invitee.id(), ownerId, role.name(), saved.getId()));
    return this.toInfos(List.of(saved)).getFirst();
  }

  @Transactional
  public void cancelInvitation(final UUID ownerId, final UUID invitationId) {
    this.deleteRow(
        this.require(
            invitationId,
            ownedBy(ownerId).and(status(TeamMemberStatus.PENDING)),
            INVITATION_NOT_FOUND));
  }

  @Transactional
  public TeamMemberInfo accept(final UUID memberId, final UUID invitationId) {
    final var invitation = this.requirePendingInvitation(memberId, invitationId);
    invitation.setStatus(TeamMemberStatus.ACCEPTED);
    invitation.setAcceptedAt(Instant.now());
    final var saved = this.saveRow(invitation);
    this.publishClosed(saved);
    return this.toInfos(List.of(saved)).getFirst();
  }

  @Transactional
  public void decline(final UUID memberId, final UUID invitationId) {
    this.deleteRow(this.requirePendingInvitation(memberId, invitationId));
  }

  @Transactional
  public TeamMemberInfo changeRole(final UUID ownerId, final UUID id, final TeamRole role) {
    final var member =
        this.require(id, ownedBy(ownerId).and(status(TeamMemberStatus.ACCEPTED)), MEMBER_NOT_FOUND);
    member.setRole(role);
    return this.toInfos(List.of(this.saveRow(member))).getFirst();
  }

  @Transactional
  public void remove(final UUID ownerId, final UUID id) {
    this.deleteRow(this.require(id, ownedBy(ownerId), MEMBER_NOT_FOUND));
  }

  @Transactional
  public void leave(final UUID memberId, final UUID id) {
    this.deleteRow(
        this.require(
            id, heldBy(memberId).and(status(TeamMemberStatus.ACCEPTED)), MEMBER_NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public PageResponse<TeamMemberInfo> members(
      final UUID ownerId, final TeamMemberStatus status, final int page, final int size) {
    return this.toPage(
        this.repository.findAllByOwnerIdAndStatus(ownerId, status, pageable(page, size)));
  }

  @Transactional(readOnly = true)
  public PageResponse<TeamMemberInfo> memberships(
      final UUID memberId, final int page, final int size) {
    return this.toPage(
        this.repository.findAllByMemberIdAndStatus(
            memberId, TeamMemberStatus.ACCEPTED, pageable(page, size)));
  }

  @Transactional(readOnly = true)
  public PageResponse<TeamMemberInfo> myInvitations(
      final UUID memberId, final int page, final int size) {
    return this.toPage(
        this.repository.findAllByMemberIdAndStatus(
            memberId, TeamMemberStatus.PENDING, pageable(page, size)));
  }

  @Transactional(readOnly = true)
  public TeamMeInfo me(final UUID memberId) {
    return new TeamMeInfo(
        this.repository.countByMemberIdAndStatus(memberId, TeamMemberStatus.PENDING));
  }

  private TeamMember requirePendingInvitation(final UUID memberId, final UUID invitationId) {
    return this.require(
        invitationId, heldBy(memberId).and(status(TeamMemberStatus.PENDING)), INVITATION_NOT_FOUND);
  }

  private TeamMember require(
      final UUID id, final Predicate<TeamMember> filter, final String notFoundKey) {
    return this.repository
        .findById(id)
        .filter(filter)
        .orElseThrow(() -> new ItemNotFoundException(notFoundKey));
  }

  private static Predicate<TeamMember> ownedBy(final UUID ownerId) {
    return member -> member.getOwnerId().equals(ownerId);
  }

  private static Predicate<TeamMember> heldBy(final UUID memberId) {
    return member -> member.getMemberId().equals(memberId);
  }

  private static Predicate<TeamMember> status(final TeamMemberStatus status) {
    return member -> member.getStatus() == status;
  }

  private TeamMember saveRow(final TeamMember member) {
    try {
      return this.repository.saveAndFlush(member);
    } catch (final OptimisticLockingFailureException ex) {
      throw new ItemNotFoundException(MEMBER_NOT_FOUND);
    }
  }

  private void deleteRow(final TeamMember member) {
    try {
      this.repository.delete(member);
      this.repository.flush();
    } catch (final OptimisticLockingFailureException ex) {
      throw new ItemNotFoundException(MEMBER_NOT_FOUND);
    }
    if (member.getStatus() == TeamMemberStatus.PENDING) {
      this.publishClosed(member);
    }
  }

  private void publishClosed(final TeamMember member) {
    this.events.publishEvent(new TeamInvitationClosedEvent(member.getId(), member.getMemberId()));
  }

  private static PageRequest pageable(final int page, final int size) {
    return PageRequest.of(
        Math.max(page, 0),
        Math.clamp(size, 1, MAX_PAGE_SIZE),
        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
  }

  private PageResponse<TeamMemberInfo> toPage(final Page<TeamMember> page) {
    return new PageResponse<>(
        this.toInfos(page.getContent()),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  private List<TeamMemberInfo> toInfos(final List<TeamMember> members) {
    final Set<UUID> ids = new HashSet<>();
    for (final var member : members) {
      ids.add(member.getOwnerId());
      ids.add(member.getMemberId());
    }
    final Map<UUID, TenantRef> names = ids.isEmpty() ? Map.of() : this.tenants.findAllByIds(ids);
    return members.stream().map(member -> toInfo(member, names)).toList();
  }

  private static TeamMemberInfo toInfo(final TeamMember member, final Map<UUID, TenantRef> names) {
    return new TeamMemberInfo(
        member.getId(),
        toUser(member.getOwnerId(), names),
        toUser(member.getMemberId(), names),
        member.getRole(),
        member.getStatus(),
        member.getCreatedAt(),
        member.getAcceptedAt());
  }

  private static TeamUserInfo toUser(final UUID id, final Map<UUID, TenantRef> names) {
    final var tenant = names.get(id);
    return tenant == null
        ? new TeamUserInfo(id, "", null, null)
        : new TeamUserInfo(id, tenant.username(), tenant.displayName(), tenant.avatarUrl());
  }
}
