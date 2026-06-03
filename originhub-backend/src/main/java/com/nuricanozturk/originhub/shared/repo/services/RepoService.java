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
package com.nuricanozturk.originhub.shared.repo.services;

import com.nuricanozturk.originhub.shared.collaborator.dtos.CollaboratorPermission;
import com.nuricanozturk.originhub.shared.collaborator.services.CollaboratorAccessPort;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.AccessNotAllowedException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemAlreadyExistsException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.dtos.RepoForm;
import com.nuricanozturk.originhub.shared.repo.dtos.RepoInfo;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.events.RepoCreatedEvent;
import com.nuricanozturk.originhub.shared.repo.events.RepoDeletedEvent;
import com.nuricanozturk.originhub.shared.repo.mappers.RepoMapper;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
@NullMarked
public class RepoService {

  public static final String NEW_REPO_DEFAULT_BRANCH = "main";

  public static final String ERR_REPO_NOT_FOUND = "repoNotFound";

  private final RepoRepository repoRepository;
  private final TenantRepository tenantRepository;
  private final RepoMapper repoMapper;
  private final ApplicationEventPublisher eventPublisher;
  private final RepoStorageService repoStorageService;
  private final CollaboratorAccessPort collaboratorAccessPort;

  @Transactional
  public RepoInfo create(final UUID tenantId, final RepoForm form) {

    final var tenant = this.getTenantById(tenantId);

    final var repoOpt = this.repoRepository.findByOwnerIdAndName(tenantId, form.getName());

    if (repoOpt.isPresent()) {
      return this.repoMapper.toDto(repoOpt.get());
    }

    final var repoObj = new Repo();
    repoObj.setOwner(tenant);
    repoObj.setName(form.getName());
    repoObj.setDescription(form.getDescription());
    repoObj.setDefaultBranch(NEW_REPO_DEFAULT_BRANCH);
    repoObj.setTopics(form.getTopics());
    repoObj.setPrivate(form.getIsPrivate() == null || form.getIsPrivate());

    final var repo = this.repoRepository.save(repoObj);

    this.eventPublisher.publishEvent(new RepoCreatedEvent(tenant.getUsername(), form.getName()));

    return this.repoMapper.toDto(repo);
  }

  @Transactional
  public RepoInfo update(
      final UUID tenantId, final String owner, final String repoName, final RepoForm form) {

    final var tenant = this.getTenantById(tenantId);
    final var repoOwner = this.getTenantByUsername(owner);

    final var repo =
        this.repoRepository
            .findByOwnerIdAndName(repoOwner.getId(), repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    this.checkUpdateAccess(tenant, repoOwner, repo, tenantId, form);
    this.applyFormToRepo(repo, form);

    return this.repoMapper.toDto(this.repoRepository.save(repo));
  }

  private void checkUpdateAccess(
      final Tenant tenant,
      final Tenant repoOwner,
      final Repo repo,
      final UUID tenantId,
      final RepoForm form) {

    final boolean isOwnerOnlyOp =
        !form.getName().equals(repo.getName()) || form.getIsPrivate() != null;

    if (isOwnerOnlyOp) {
      this.checkIsRepoOwner(tenant, repoOwner);
    } else if (!tenant.getId().equals(repoOwner.getId())) {
      this.checkCollaboratorAndPermission(repo, tenantId);
    }
  }

  private void checkCollaboratorAndPermission(final Repo repo, final UUID tenantId) {

    if (!this.collaboratorAccessPort.isActiveCollaborator(repo.getId(), tenantId)) {
      throw new AccessNotAllowedException("repoAccessDenied");
    }

    if (!this.collaboratorAccessPort.hasPermission(
        repo.getId(), tenantId, CollaboratorPermission.SETTINGS_WRITE)) {
      throw new AccessNotAllowedException("insufficientPermissions");
    }
  }

  private void applyFormToRepo(final Repo repo, final RepoForm form) {

    if (!form.getName().equals(repo.getName())) {
      repo.setName(form.getName());
    }

    repo.setDescription(form.getDescription());

    if (form.getTopics() != null) {
      repo.setTopics(form.getTopics());
    }

    if (form.getIsPrivate() != null) {
      repo.setPrivate(form.getIsPrivate());
    }

    if (form.getDeleteHeadBranchOnPrMerge() != null) {
      repo.setDeleteHeadBranchOnPrMerge(form.getDeleteHeadBranchOnPrMerge());
    }

    if (form.getDeleteHeadBranchOnPrClose() != null) {
      repo.setDeleteHeadBranchOnPrClose(form.getDeleteHeadBranchOnPrClose());
    }
  }

  @Transactional
  public void delete(final String repoOwner, final String repoName) {

    final var repo = this.findByOwnerUsernameAndName(repoOwner, repoName);

    this.repoRepository.deleteById(repo.getId());
  }

  @Transactional
  public void rollbackRepoName(final String owner, final String oldName, final String newName) {

    final var repo = this.findByOwnerUsernameAndName(owner, newName);

    repo.setName(oldName);

    this.repoRepository.save(repo);
  }

  @Transactional
  public void delete(final UUID tenantId, final String repoName, final String owner) {

    final var ownerTenant = this.getTenantByUsername(owner);

    if (!tenantId.equals(ownerTenant.getId())) {
      throw new AccessNotAllowedException("repoAccessDenied");
    }

    final var repo =
        this.repoRepository
            .findByOwnerIdAndName(tenantId, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    final var forkedFrom = repo.getForkedFrom();
    this.repoRepository.deleteById(repo.getId());
    if (forkedFrom != null) {
      this.repoRepository.decrementForkCount(forkedFrom.getId());
    }

    this.eventPublisher.publishEvent(new RepoDeletedEvent(owner, repoName));
  }

  @Transactional
  public RepoInfo fork(final UUID tenantId, final String sourceOwner, final String sourceRepoName) {

    final var forker = this.getTenantById(tenantId);

    final var sourceRepo =
        this.repoRepository
            .findByOwnerUsernameAndName(sourceOwner, sourceRepoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    if (sourceRepo.isPrivate() && !sourceRepo.getOwner().getId().equals(tenantId)) {
      throw new AccessNotAllowedException("repoAccessDenied");
    }

    if (sourceRepo.getOwner().getId().equals(tenantId)) {
      throw new AccessNotAllowedException("cannotForkOwnRepo");
    }

    if (this.repoRepository.existsByForkedFromIdAndOwnerId(sourceRepo.getId(), tenantId)) {
      throw new ItemAlreadyExistsException("alreadyForked");
    }

    final var fork = new Repo();
    fork.setOwner(forker);
    fork.setName(sourceRepoName);
    fork.setDescription(sourceRepo.getDescription());
    fork.setDefaultBranch(sourceRepo.getDefaultBranch());
    fork.setTopics(sourceRepo.getTopics());
    fork.setPrivate(sourceRepo.isPrivate());
    fork.setForkedFrom(sourceRepo);

    final var savedFork = this.repoRepository.save(fork);
    this.repoRepository.incrementForkCount(sourceRepo.getId());
    this.repoStorageService.forkRepo(
        sourceOwner, sourceRepoName, forker.getUsername(), sourceRepoName);

    this.eventPublisher.publishEvent(new RepoCreatedEvent(forker.getUsername(), sourceRepoName));

    return this.repoMapper.toDto(savedFork);
  }

  public Page<RepoInfo> findAllByOwner(
      final String owner,
      final Pageable pageable,
      final @org.jspecify.annotations.Nullable UUID requesterId) {

    final var ownerTenant = this.tenantRepository.findByUsername(owner);
    final boolean isOwner =
        requesterId != null
            && ownerTenant.isPresent()
            && ownerTenant.get().getId().equals(requesterId);

    if (isOwner) {
      return this.repoRepository
          .findAllByOwnerUsername(owner, pageable)
          .map(this.repoMapper::toDto);
    }

    if (requesterId != null) {
      final var collaboratorRepoIds =
          this.collaboratorAccessPort.findAcceptedRepoIds(requesterId, owner);

      if (!collaboratorRepoIds.isEmpty()) {
        return this.repoRepository
            .findVisibleReposByOwner(owner, collaboratorRepoIds, pageable)
            .map(this.repoMapper::toDto);
      }
    }

    return this.repoRepository
        .findAllByOwnerUsernameAndIsPrivateFalse(owner, pageable)
        .map(this.repoMapper::toDto);
  }

  public RepoInfo findByOwnerAndName(
      final String owner, final String repoName, final @Nullable UUID requesterId) {

    final var repo = this.findByOwnerUsernameAndName(owner, repoName);

    if (!repo.isPrivate()) { // Public Repo
      return this.repoMapper.toDto(repo);
    }

    final var ownerTenant = this.tenantRepository.findByUsername(owner);

    final boolean isOwner =
        requesterId != null
            && ownerTenant.isPresent()
            && ownerTenant.get().getId().equals(requesterId);

    if (isOwner) {
      return this.repoMapper.toDto(repo);
    }

    final boolean isCollaborator =
        requesterId != null
            && this.collaboratorAccessPort.isActiveCollaborator(repo.getId(), requesterId);

    if (!isCollaborator) {
      throw new AccessNotAllowedException("repoAccessDenied");
    }

    return this.repoMapper.toDto(repo);
  }

  public void assertUserHasPermission(
      final UUID tenantId,
      final String owner,
      final String repoName,
      final CollaboratorPermission permission) {

    final var repo = this.findByOwnerUsernameAndName(owner, repoName);
    final var ownerTenant = this.getTenantByUsername(owner);

    if (ownerTenant.getId().equals(tenantId)) {
      return; // repo owner always has all permissions
    }

    if (!repo.isPrivate() && permission == CollaboratorPermission.PULL_REQUEST_CREATE) {
      return; // any authenticated user may open a PR on a public repo
    }

    if (!this.collaboratorAccessPort.isActiveCollaborator(repo.getId(), tenantId)) {
      throw new AccessNotAllowedException("repoAccessDenied");
    }

    if (!this.collaboratorAccessPort.hasPermission(repo.getId(), tenantId, permission)) {
      throw new AccessNotAllowedException("insufficientPermissions");
    }
  }

  /**
   * Asserts that the given user is the repo owner or has at least one of the specified collaborator
   * permissions (or ADMIN). Used when multiple permissions are enough for an operation (e.g.,
   * SETTINGS_READ or SETTINGS_WRITE both allow reading webhook config).
   */
  public void assertUserHasAnyPermission(
      final UUID tenantId,
      final String owner,
      final String repoName,
      final CollaboratorPermission... permissions) {

    final var repo = this.findByOwnerUsernameAndName(owner, repoName);
    final var ownerTenant = this.getTenantByUsername(owner);

    if (ownerTenant.getId().equals(tenantId)) {
      return; // repo owner always has all permissions
    }

    if (!this.collaboratorAccessPort.isActiveCollaborator(repo.getId(), tenantId)) {
      throw new AccessNotAllowedException("repoAccessDenied");
    }

    for (final CollaboratorPermission permission : permissions) {
      if (this.collaboratorAccessPort.hasPermission(repo.getId(), tenantId, permission)) {
        return;
      }
    }

    throw new AccessNotAllowedException("insufficientPermissions");
  }

  public void assertUserCanAccessRepo(
      final @org.jspecify.annotations.Nullable UUID tenantId,
      final String owner,
      final String repoName) {

    final var repo = this.findByOwnerUsernameAndName(owner, repoName);

    if (repo.isPrivate()) {
      if (tenantId == null) {
        throw new AccessNotAllowedException("repoAccessDenied");
      }
      final var tenant = this.getTenantById(tenantId);
      final var repoOwner = this.getTenantByUsername(owner);

      if (!tenant.getId().equals(repoOwner.getId())) {
        if (!this.collaboratorAccessPort.isActiveCollaborator(repo.getId(), tenantId)) {
          throw new AccessNotAllowedException("repoAccessDenied");
        }
      }
    }
  }

  private void checkIsRepoOwner(final Tenant tenant, final Tenant repoOwner) {

    if (tenant.getId().equals(repoOwner.getId())) {
      return;
    }

    throw new AccessNotAllowedException("repoAccessDenied");
  }

  private Tenant getTenantById(final UUID tenantId) {

    return this.tenantRepository
        .findById(tenantId)
        .orElseThrow(() -> new ItemNotFoundException("userNotFound"));
  }

  private Tenant getTenantByUsername(final String username) {

    return this.tenantRepository
        .findByUsername(username)
        .orElseThrow(() -> new ItemNotFoundException("userNotFound"));
  }

  private Repo findByOwnerUsernameAndName(final String owner, final String repoName) {

    return this.repoRepository
        .findByOwnerUsernameAndName(owner, repoName)
        .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));
  }
}
