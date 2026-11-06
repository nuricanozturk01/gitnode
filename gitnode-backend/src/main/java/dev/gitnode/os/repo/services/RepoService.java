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

import dev.gitnode.os.events.repo.RepoCreatedEvent;
import dev.gitnode.os.events.repo.RepoDeletedEvent;
import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.repo.api.CollaboratorAccessPort;
import dev.gitnode.os.repo.api.CollaboratorPermission;
import dev.gitnode.os.repo.api.GitAccessPort;
import dev.gitnode.os.repo.api.RepoAccessPort;
import dev.gitnode.os.repo.api.RepoQueryPort;
import dev.gitnode.os.repo.dtos.RepoForm;
import dev.gitnode.os.repo.dtos.RepoInfo;
import dev.gitnode.os.repo.entities.Repo;
import dev.gitnode.os.repo.repositories.RepoRepository;
import dev.gitnode.os.shared.audit.annotations.Audited;
import dev.gitnode.os.shared.cache.CacheNames;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemAlreadyExistsException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
@NullMarked
public class RepoService implements RepoAccessPort, RepoQueryPort, GitAccessPort {

  public static final String NEW_REPO_DEFAULT_BRANCH = "main";

  public static final String ERR_REPO_NOT_FOUND = "repoNotFound";

  private final RepoRepository repoRepository;
  private final TenantQueryPort tenantRepository;
  private final RepoOwnerResolver ownerResolver;
  private final RepoInfoAssembler infoAssembler;
  private final ApplicationEventPublisher eventPublisher;
  private final RepoStorageService repoStorageService;
  private final CollaboratorAccessPort collaboratorAccessPort;
  private final RepoInfoCache repoInfoCache;

  @Audited(
      action = "CREATE_REPO",
      entityType = "REPO",
      entityIdSpEL = "#result.getId().toString()",
      detailsSpEL = "'name=' + #form.name + ', private=' + #form.isPrivate")
  @Transactional
  public RepoInfo create(final UUID tenantId, final RepoForm form) {

    final var tenant = this.getTenantById(tenantId);

    final var repoOpt = this.repoRepository.findByOwnerIdAndName(tenantId, form.getName());

    if (repoOpt.isPresent()) {
      return this.infoAssembler.toDto(repoOpt.get());
    }

    final var repoObj = new Repo();
    repoObj.setOwnerId(tenant.id());
    repoObj.setName(form.getName());
    repoObj.setDescription(form.getDescription());
    repoObj.setDefaultBranch(NEW_REPO_DEFAULT_BRANCH);
    repoObj.setTopics(form.getTopics());
    repoObj.setPrivate(form.getIsPrivate() == null || form.getIsPrivate());

    final var repo = this.repoRepository.save(repoObj);

    this.eventPublisher.publishEvent(new RepoCreatedEvent(tenant.username(), form.getName()));

    return this.infoAssembler.toDto(repo);
  }

  @CacheEvict(cacheNames = CacheNames.REPO_META, key = "#owner + ':' + #repoName")
  @Transactional
  public RepoInfo update(
      final UUID tenantId, final String owner, final String repoName, final RepoForm form) {

    final var tenant = this.getTenantById(tenantId);
    final var repoOwner = this.getTenantByUsername(owner);

    final var repo =
        this.repoRepository
            .findByOwnerIdAndName(repoOwner.id(), repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    this.checkUpdateAccess(tenant, repoOwner, repo, tenantId, form);
    this.applyFormToRepo(repo, form);

    return this.infoAssembler.toDto(this.repoRepository.save(repo));
  }

  private void checkUpdateAccess(
      final TenantRef tenant,
      final TenantRef repoOwner,
      final Repo repo,
      final UUID tenantId,
      final RepoForm form) {

    final boolean isOwnerOnlyOp =
        !form.getName().equals(repo.getName()) || form.getIsPrivate() != null;

    if (isOwnerOnlyOp) {
      this.checkIsRepoOwner(tenant, repoOwner);
    } else if (!tenant.id().equals(repoOwner.id())) {
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

    if (form.getAiPrReviewEnabled() != null) {
      repo.setAiPrReviewEnabled(form.getAiPrReviewEnabled());
    }
  }

  @CacheEvict(cacheNames = CacheNames.REPO_META, key = "#repoOwner + ':' + #repoName")
  @Transactional
  public void delete(final String repoOwner, final String repoName) {

    final var repo = this.findByOwnerUsernameAndName(repoOwner, repoName);

    this.repoRepository.deleteById(repo.getId());
  }

  @CacheEvict(cacheNames = CacheNames.REPO_META, key = "#owner + ':' + #newName")
  @Transactional
  public void rollbackRepoName(final String owner, final String oldName, final String newName) {

    final var repo = this.findByOwnerUsernameAndName(owner, newName);

    repo.setName(oldName);

    this.repoRepository.save(repo);
  }

  @CacheEvict(cacheNames = CacheNames.REPO_META, key = "#owner + ':' + #repoName")
  @Audited(
      action = "DELETE_REPO",
      entityType = "REPO",
      detailsSpEL = "'owner=' + #owner + ', repo=' + #repoName")
  @Transactional
  public void delete(final UUID tenantId, final String repoName, final String owner) {

    final var ownerTenant = this.getTenantByUsername(owner);

    if (!tenantId.equals(ownerTenant.id())) {
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
      this.repoInfoCache.evict(
          this.ownerResolver.username(forkedFrom.getOwnerId()), forkedFrom.getName());
    }

    this.eventPublisher.publishEvent(new RepoDeletedEvent(owner, repoName));
  }

  @CacheEvict(cacheNames = CacheNames.REPO_META, key = "#sourceOwner + ':' + #sourceRepoName")
  @Transactional
  public RepoInfo fork(final UUID tenantId, final String sourceOwner, final String sourceRepoName) {

    final var forker = this.getTenantById(tenantId);

    final var sourceRepo =
        this.repoRepository
            .findByOwnerUsernameAndName(sourceOwner, sourceRepoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    if (sourceRepo.isPrivate()
        && !sourceRepo.getOwnerId().equals(tenantId)
        && !this.collaboratorAccessPort.isActiveCollaborator(sourceRepo.getId(), tenantId)) {
      throw new AccessNotAllowedException("repoAccessDenied");
    }

    if (sourceRepo.getOwnerId().equals(tenantId)) {
      throw new AccessNotAllowedException("cannotForkOwnRepo");
    }

    if (this.repoRepository.existsByForkedFromIdAndOwnerId(sourceRepo.getId(), tenantId)) {
      throw new ItemAlreadyExistsException("alreadyForked");
    }

    final var fork = new Repo();
    fork.setOwnerId(forker.id());
    fork.setName(sourceRepoName);
    fork.setDescription(sourceRepo.getDescription());
    fork.setDefaultBranch(sourceRepo.getDefaultBranch());
    fork.setTopics(sourceRepo.getTopics());
    fork.setPrivate(sourceRepo.isPrivate());
    fork.setForkedFrom(sourceRepo);

    final var savedFork = this.repoRepository.save(fork);
    this.repoRepository.incrementForkCount(sourceRepo.getId());
    this.repoStorageService.forkRepo(
        sourceOwner, sourceRepoName, forker.username(), sourceRepoName);

    this.eventPublisher.publishEvent(new RepoCreatedEvent(forker.username(), sourceRepoName));

    return this.infoAssembler.toDto(savedFork);
  }

  public Page<RepoInfo> findAllByOwner(
      final String owner,
      final Pageable pageable,
      final @org.jspecify.annotations.Nullable UUID requesterId) {

    if (requesterId != null && this.canListPrivateRepos(owner, requesterId)) {
      return this.infoAssembler.toDtoPage(
          this.repoRepository.findAllByOwnerUsername(owner, pageable));
    }

    if (requesterId != null) {
      final var collaboratorRepoIds =
          this.collaboratorAccessPort.findAcceptedRepoIds(requesterId, owner);

      if (!collaboratorRepoIds.isEmpty()) {
        return this.infoAssembler.toDtoPage(
            this.repoRepository.findVisibleReposByOwner(owner, collaboratorRepoIds, pageable));
      }
    }

    return this.infoAssembler.toDtoPage(
        this.repoRepository.findAllByOwnerUsernameAndIsPrivateFalse(owner, pageable));
  }

  private boolean canListPrivateRepos(final String owner, final UUID requesterId) {
    return this.tenantRepository
        .findByUsername(owner)
        .map(
            tenant ->
                tenant.id().equals(requesterId)
                    || this.collaboratorAccessPort.hasTeamAccess(requesterId, tenant.id()))
        .orElse(false);
  }

  @Override
  public RepoInfo findByOwnerAndName(
      final String owner, final String repoName, final @Nullable UUID requesterId) {

    final var repo = this.findByOwnerUsernameAndName(owner, repoName);

    if (!repo.isPrivate()) { // Public Repo
      return this.repoInfoCache.fetch(owner, repoName);
    }

    final var ownerTenant = this.tenantRepository.findByUsername(owner);

    final boolean isOwner =
        requesterId != null
            && ownerTenant.isPresent()
            && ownerTenant.get().id().equals(requesterId);

    if (isOwner) {
      return this.repoInfoCache.fetch(owner, repoName);
    }

    final boolean isCollaborator =
        requesterId != null
            && this.collaboratorAccessPort.isActiveCollaborator(repo.getId(), requesterId);

    if (!isCollaborator) {
      throw new AccessNotAllowedException("repoAccessDenied");
    }

    return this.repoInfoCache.fetch(owner, repoName);
  }

  @Override
  public void assertIsRepoOwner(final UUID tenantId, final String owner, final String repoName) {

    final var ownerTenant = this.getTenantByUsername(owner);
    if (!ownerTenant.id().equals(tenantId)) {
      throw new AccessNotAllowedException("insufficientPermissions");
    }
  }

  @Override
  public void assertUserHasPermission(
      final UUID tenantId,
      final String owner,
      final String repoName,
      final CollaboratorPermission permission) {

    final var repo = this.findByOwnerUsernameAndName(owner, repoName);
    final var ownerTenant = this.getTenantByUsername(owner);

    if (ownerTenant.id().equals(tenantId)) {
      return; // repo owner always has all permissions
    }

    if (!repo.isPrivate() && permission == CollaboratorPermission.PULL_REQUEST_CREATE) {
      return; // any authenticated user may open a PR on a public repo
    }

    if (this.collaboratorAccessPort.hasPermission(repo.getId(), tenantId, permission)) {
      return;
    }

    throw this.permissionDenied(repo.getId(), tenantId);
  }

  /**
   * Asserts that the given user is the repo owner or has at least one of the specified collaborator
   * permissions (or ADMIN). Used when multiple permissions are enough for an operation (e.g.,
   * SETTINGS_READ or SETTINGS_WRITE both allow reading webhook config).
   */
  @Override
  public void assertUserHasAnyPermission(
      final UUID tenantId,
      final String owner,
      final String repoName,
      final CollaboratorPermission... permissions) {

    final var repo = this.findByOwnerUsernameAndName(owner, repoName);
    final var ownerTenant = this.getTenantByUsername(owner);

    if (ownerTenant.id().equals(tenantId)) {
      return; // repo owner always has all permissions
    }

    for (final CollaboratorPermission permission : permissions) {
      if (this.collaboratorAccessPort.hasPermission(repo.getId(), tenantId, permission)) {
        return;
      }
    }

    throw this.permissionDenied(repo.getId(), tenantId);
  }

  private AccessNotAllowedException permissionDenied(final UUID repoId, final UUID tenantId) {
    return new AccessNotAllowedException(
        this.collaboratorAccessPort.isActiveCollaborator(repoId, tenantId)
            ? "insufficientPermissions"
            : "repoAccessDenied");
  }

  @Override
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

      if (!tenant.id().equals(repoOwner.id())) {
        if (!this.collaboratorAccessPort.isActiveCollaborator(repo.getId(), tenantId)) {
          throw new AccessNotAllowedException("repoAccessDenied");
        }
      }
    }
  }

  @Override
  public boolean canRead(final @Nullable UUID tenantId, final String owner, final String repoName) {
    return this.repoRepository
        .findByOwnerUsernameAndName(owner, repoName)
        .map(
            repo ->
                !repo.isPrivate()
                    || tenantId != null
                        && (repo.getOwnerId().equals(tenantId)
                            || this.collaboratorAccessPort.isActiveCollaborator(
                                repo.getId(), tenantId)))
        .orElse(false);
  }

  @Override
  public boolean canWrite(final UUID tenantId, final String owner, final String repoName) {
    return this.repoRepository
        .findByOwnerUsernameAndName(owner, repoName)
        .map(
            repo ->
                repo.getOwnerId().equals(tenantId)
                    || this.collaboratorAccessPort.hasPermission(
                        repo.getId(), tenantId, CollaboratorPermission.PUSH))
        .orElse(false);
  }

  private void checkIsRepoOwner(final TenantRef tenant, final TenantRef repoOwner) {

    if (tenant.id().equals(repoOwner.id())) {
      return;
    }

    throw new AccessNotAllowedException("repoAccessDenied");
  }

  private TenantRef getTenantById(final UUID tenantId) {

    return this.tenantRepository
        .findById(tenantId)
        .orElseThrow(() -> new ItemNotFoundException("userNotFound"));
  }

  private TenantRef getTenantByUsername(final String username) {

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
