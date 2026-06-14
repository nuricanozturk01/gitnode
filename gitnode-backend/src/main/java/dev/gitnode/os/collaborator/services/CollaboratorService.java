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
package dev.gitnode.os.collaborator.services;

import dev.gitnode.os.collaborator.dtos.CollaboratorInfo;
import dev.gitnode.os.collaborator.dtos.InvitationTokenInfo;
import dev.gitnode.os.collaborator.dtos.InviteCollaboratorForm;
import dev.gitnode.os.collaborator.dtos.InviteLinkResponse;
import dev.gitnode.os.collaborator.dtos.UpdateCollaboratorPermissionsForm;
import dev.gitnode.os.collaborator.entities.CollaboratorStatus;
import dev.gitnode.os.collaborator.entities.RepoCollaborator;
import dev.gitnode.os.collaborator.mappers.CollaboratorMapper;
import dev.gitnode.os.collaborator.repositories.CollaboratorRepository;
import dev.gitnode.os.events.collaborator.CollaboratorInvitedEvent;
import dev.gitnode.os.events.collaborator.CollaboratorRemovedEvent;
import dev.gitnode.os.shared.audit.annotations.Audited;
import dev.gitnode.os.shared.collaborator.dtos.CollaboratorPermission;
import dev.gitnode.os.shared.collaborator.services.CollaboratorAccessPort;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemAlreadyExistsException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.repo.dtos.PageResponse;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@NullMarked
public class CollaboratorService implements CollaboratorAccessPort {

  private static final int INVITE_LINK_EXPIRY_DAYS = 7;

  private final CollaboratorRepository collaboratorRepository;
  private final RepoRepository repoRepository;
  private final TenantRepository tenantRepository;
  private final CollaboratorMapper collaboratorMapper;
  private final ApplicationEventPublisher eventPublisher;

  @Audited(
      action = "INVITE_COLLABORATOR",
      entityType = "COLLABORATOR",
      entityIdSpEL = "#result.getId().toString()",
      detailsSpEL =
          "'repo=' + #ownerUsername + '/' + #repoName + ', invited=' + #form.username"
              + " + ', permissions=' + #form.permissions")
  @Transactional
  public CollaboratorInfo invite(
      final UUID requesterId,
      final String ownerUsername,
      final String repoName,
      final InviteCollaboratorForm form) {

    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(ownerUsername, repoName)
            .orElseThrow(() -> new ItemNotFoundException("repoNotFound"));

    if (!repo.getOwner().getId().equals(requesterId)) {
      throw new AccessNotAllowedException("repoAccessDenied");
    }

    final var invitee =
        this.tenantRepository
            .findByUsername(form.getUsername())
            .orElseThrow(() -> new ItemNotFoundException("userNotFound"));

    if (invitee.getId().equals(requesterId)) {
      throw new BadRequestException("cannotInviteYourself");
    }

    if (this.collaboratorRepository.existsByRepoIdAndTenantIdAndStatus(
        repo.getId(), invitee.getId(), CollaboratorStatus.ACCEPTED)) {
      throw new ItemAlreadyExistsException("alreadyCollaborator");
    }

    final var existingOpt =
        this.collaboratorRepository.findByRepoIdAndTenantId(repo.getId(), invitee.getId());

    final RepoCollaborator collaborator;

    final var token = UUID.randomUUID().toString();
    final var expiresAt = Instant.now().plus(INVITE_LINK_EXPIRY_DAYS, ChronoUnit.DAYS);

    if (existingOpt.isPresent()) {
      collaborator = existingOpt.get();
      collaborator.setStatus(CollaboratorStatus.PENDING);
      collaborator.setPermissions(resolvePermissions(form.getPermissions()));
    } else {
      collaborator = new RepoCollaborator();
      collaborator.setRepo(repo);
      collaborator.setTenant(invitee);
      collaborator.setInvitedBy(repo.getOwner());
      collaborator.setPermissions(resolvePermissions(form.getPermissions()));
      collaborator.setStatus(CollaboratorStatus.PENDING);
    }
    collaborator.setInviteToken(token);
    collaborator.setTokenExpiresAt(expiresAt);

    final var saved = this.collaboratorRepository.save(collaborator);

    this.eventPublisher.publishEvent(
        new CollaboratorInvitedEvent(
            repo.getId(),
            repo.getName(),
            repo.getOwner().getUsername(),
            invitee.getId(),
            invitee.getUsername(),
            requesterId,
            token));

    return this.collaboratorMapper.toInfo(saved);
  }

  public PageResponse<CollaboratorInfo> list(
      final UUID requesterId,
      final String ownerUsername,
      final String repoName,
      final int page,
      final int size) {

    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(ownerUsername, repoName)
            .orElseThrow(() -> new ItemNotFoundException("repoNotFound"));

    if (!repo.getOwner().getId().equals(requesterId)) {
      if (!this.isActiveCollaborator(repo.getId(), requesterId)) {
        throw new AccessNotAllowedException("repoAccessDenied");
      }
    }

    final var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

    return PageResponse.from(
        this.collaboratorRepository
            .findAllByRepoId(repo.getId(), pageable)
            .map(this.collaboratorMapper::toInfo));
  }

  @Audited(
      action = "REMOVE_COLLABORATOR",
      entityType = "COLLABORATOR",
      detailsSpEL = "'repo=' + #ownerUsername + '/' + #repoName + ', removed=' + #targetUsername")
  @Transactional
  public void remove(
      final UUID requesterId,
      final String ownerUsername,
      final String repoName,
      final String targetUsername) {

    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(ownerUsername, repoName)
            .orElseThrow(() -> new ItemNotFoundException("repoNotFound"));

    final var target =
        this.tenantRepository
            .findByUsername(targetUsername)
            .orElseThrow(() -> new ItemNotFoundException("userNotFound"));

    final boolean isOwner = repo.getOwner().getId().equals(requesterId);
    final boolean isSelf = target.getId().equals(requesterId);

    if (!isOwner && !isSelf) {
      throw new AccessNotAllowedException("repoAccessDenied");
    }

    final var collaborator =
        this.collaboratorRepository
            .findByRepoIdAndTenantId(repo.getId(), target.getId())
            .orElseThrow(() -> new ItemNotFoundException("collaboratorNotFound"));

    this.collaboratorRepository.delete(collaborator);

    this.eventPublisher.publishEvent(
        new CollaboratorRemovedEvent(
            repo.getId(), repo.getName(), target.getId(), target.getUsername()));
  }

  @Audited(
      action = "UPDATE_COLLABORATOR_PERMISSIONS",
      entityType = "COLLABORATOR",
      entityIdSpEL = "#result.getId().toString()",
      detailsSpEL =
          "'repo=' + #ownerUsername + '/' + #repoName + ', user=' + #targetUsername"
              + " + ', permissions=' + #form.permissions")
  @Transactional
  public CollaboratorInfo updatePermissions(
      final UUID requesterId,
      final String ownerUsername,
      final String repoName,
      final String targetUsername,
      final UpdateCollaboratorPermissionsForm form) {

    final var collaborator =
        this.getCollaborator(requesterId, ownerUsername, repoName, targetUsername);

    collaborator.setPermissions(resolvePermissions(form.getPermissions()));
    final var saved = this.collaboratorRepository.save(collaborator);

    return this.collaboratorMapper.toInfo(saved);
  }

  @Transactional
  public CollaboratorInfo respondToInvitation(
      final UUID requesterId,
      final String ownerUsername,
      final String repoName,
      final boolean accept) {

    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(ownerUsername, repoName)
            .orElseThrow(() -> new ItemNotFoundException("repoNotFound"));

    final var collaborator =
        this.collaboratorRepository
            .findByRepoIdAndTenantId(repo.getId(), requesterId)
            .orElseThrow(() -> new ItemNotFoundException("invitationNotFound"));

    if (collaborator.getStatus() != CollaboratorStatus.PENDING) {
      throw new BadRequestException("invitationAlreadyHandled");
    }

    collaborator.setStatus(accept ? CollaboratorStatus.ACCEPTED : CollaboratorStatus.DECLINED);

    final var saved = this.collaboratorRepository.save(collaborator);

    return this.collaboratorMapper.toInfo(saved);
  }

  public CollaboratorInfo getMyInvitation(
      final UUID requesterId, final String ownerUsername, final String repoName) {

    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(ownerUsername, repoName)
            .orElseThrow(() -> new ItemNotFoundException("repoNotFound"));

    final var collaborator =
        this.collaboratorRepository
            .findByRepoIdAndTenantId(repo.getId(), requesterId)
            .orElseThrow(() -> new ItemNotFoundException("invitationNotFound"));

    return this.collaboratorMapper.toInfo(collaborator);
  }

  @Override
  public boolean isActiveCollaborator(final UUID repoId, final UUID tenantId) {
    return this.collaboratorRepository.existsByRepoIdAndTenantIdAndStatus(
        repoId, tenantId, CollaboratorStatus.ACCEPTED);
  }

  @Override
  public boolean hasPendingInvitation(final UUID repoId, final UUID tenantId) {
    return this.collaboratorRepository.existsByRepoIdAndTenantIdAndStatus(
        repoId, tenantId, CollaboratorStatus.PENDING);
  }

  @Override
  public boolean hasPermission(
      final UUID repoId, final UUID tenantId, final CollaboratorPermission permission) {

    return this.collaboratorRepository
        .findByRepoIdAndTenantId(repoId, tenantId)
        .filter(c -> c.getStatus() == CollaboratorStatus.ACCEPTED)
        .map(
            c ->
                c.getPermissions().contains(permission)
                    || c.getPermissions().contains(CollaboratorPermission.ADMIN))
        .orElse(false);
  }

  @Override
  public Set<UUID> findAcceptedRepoIds(final UUID tenantId, final String ownerUsername) {
    return this.collaboratorRepository.findAcceptedRepoIdsByTenantIdAndOwnerUsername(
        tenantId, ownerUsername);
  }

  @Transactional
  public InviteLinkResponse generateInviteLink(
      final UUID requesterId,
      final String ownerUsername,
      final String repoName,
      final String targetUsername) {

    final var collaborator =
        this.getCollaborator(requesterId, ownerUsername, repoName, targetUsername);

    final var token = UUID.randomUUID().toString();
    final var expiresAt = Instant.now().plus(INVITE_LINK_EXPIRY_DAYS, ChronoUnit.DAYS);

    collaborator.setInviteToken(token);
    collaborator.setTokenExpiresAt(expiresAt);
    this.collaboratorRepository.save(collaborator);

    return new InviteLinkResponse(token, expiresAt, ownerUsername, repoName, targetUsername);
  }

  public InvitationTokenInfo getInvitationByToken(final String token) {
    final var collaborator =
        this.collaboratorRepository
            .findByInviteToken(token)
            .orElseThrow(() -> new ItemNotFoundException("invitationNotFound"));

    if (collaborator.getTokenExpiresAt() == null
        || Instant.now().isAfter(collaborator.getTokenExpiresAt())) {
      throw new BadRequestException("invitationLinkExpired");
    }

    return new InvitationTokenInfo(
        collaborator.getRepo().getOwner().getUsername(),
        collaborator.getRepo().getName(),
        collaborator.getTenant().getUsername(),
        collaborator.getInvitedBy().getUsername(),
        collaborator.getPermissions(),
        collaborator.getTokenExpiresAt());
  }

  @Audited(
      action = "ACCEPT_COLLABORATOR_INVITE",
      entityType = "COLLABORATOR",
      entityIdSpEL = "#result.getId().toString()",
      detailsSpEL = "'user=' + #result.username + ', permissions=' + #result.permissions")
  @Transactional
  public CollaboratorInfo acceptViaToken(final UUID requesterId, final String token) {
    final var collaborator =
        this.collaboratorRepository
            .findByInviteToken(token)
            .orElseThrow(() -> new ItemNotFoundException("invitationNotFound"));

    if (!collaborator.getTenant().getId().equals(requesterId)) {
      throw new AccessNotAllowedException("invitationNotForYou");
    }

    if (collaborator.getTokenExpiresAt() == null
        || Instant.now().isAfter(collaborator.getTokenExpiresAt())) {
      throw new BadRequestException("invitationLinkExpired");
    }

    if (collaborator.getStatus() != CollaboratorStatus.PENDING) {
      throw new BadRequestException("invitationAlreadyHandled");
    }

    collaborator.setStatus(CollaboratorStatus.ACCEPTED);
    collaborator.setInviteToken(null);
    collaborator.setTokenExpiresAt(null);
    final var saved = this.collaboratorRepository.save(collaborator);

    return this.collaboratorMapper.toInfo(saved);
  }

  @Audited(
      action = "DECLINE_COLLABORATOR_INVITE",
      entityType = "COLLABORATOR",
      entityIdSpEL = "#result.getId().toString()",
      detailsSpEL = "'user=' + #result.username")
  @Transactional
  public CollaboratorInfo declineViaToken(final UUID requesterId, final String token) {
    final var collaborator =
        this.collaboratorRepository
            .findByInviteToken(token)
            .orElseThrow(() -> new ItemNotFoundException("invitationNotFound"));

    if (!collaborator.getTenant().getId().equals(requesterId)) {
      throw new AccessNotAllowedException("invitationNotForYou");
    }

    if (collaborator.getStatus() != CollaboratorStatus.PENDING) {
      throw new BadRequestException("invitationAlreadyHandled");
    }

    collaborator.setStatus(CollaboratorStatus.DECLINED);
    collaborator.setInviteToken(null);
    collaborator.setTokenExpiresAt(null);
    final var saved = this.collaboratorRepository.save(collaborator);

    return this.collaboratorMapper.toInfo(saved);
  }

  private static Set<CollaboratorPermission> resolvePermissions(
      final Set<CollaboratorPermission> requested) {

    if (requested.isEmpty()) {
      return EnumSet.of(CollaboratorPermission.READ);
    }

    final var result = EnumSet.copyOf(requested);
    result.add(CollaboratorPermission.READ);
    return result;
  }

  private RepoCollaborator getCollaborator(
      final UUID requesterId,
      final String ownerUsername,
      final String repoName,
      final String targetUsername) {

    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(ownerUsername, repoName)
            .orElseThrow(() -> new ItemNotFoundException("repoNotFound"));

    if (!repo.getOwner().getId().equals(requesterId)) {
      throw new AccessNotAllowedException("repoAccessDenied");
    }

    final var target =
        this.tenantRepository
            .findByUsername(targetUsername)
            .orElseThrow(() -> new ItemNotFoundException("userNotFound"));

    return this.collaboratorRepository
        .findByRepoIdAndTenantId(repo.getId(), target.getId())
        .orElseThrow(() -> new ItemNotFoundException("collaboratorNotFound"));
  }
}
