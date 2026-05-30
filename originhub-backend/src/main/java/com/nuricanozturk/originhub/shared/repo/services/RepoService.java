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

import com.nuricanozturk.originhub.shared.errorhandling.exceptions.AccessNotAllowedException;
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
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class RepoService {

  /** Initial branch name for new repositories; not client-configurable. */
  public static final @NonNull String NEW_REPO_DEFAULT_BRANCH = "main";

  public static final @NonNull String ERR_REPO_NOT_FOUND = "repoNotFound";

  private final @NonNull RepoRepository repoRepository;
  private final @NonNull TenantRepository tenantRepository;
  private final @NonNull RepoMapper repoMapper;
  private final @NonNull ApplicationEventPublisher eventPublisher;

  @Transactional
  public @NonNull RepoInfo create(final @NonNull UUID tenantId, final @NonNull RepoForm form) {

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
  public @NonNull RepoInfo update(
      final @NonNull UUID tenantId,
      final @NonNull String owner,
      final @NonNull String repoName,
      final @NonNull RepoForm form) {

    final var tenant = this.getTenantById(tenantId);
    final var repoOwner = this.getTenantByUsername(owner);

    this.checkIsRepoOwnerOrAdmin(tenant, repoOwner);

    final var repo =
        this.repoRepository
            .findByOwnerIdAndName(repoOwner.getId(), repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

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

    final var updatedRepo = this.repoRepository.save(repo);

    return this.repoMapper.toDto(updatedRepo);
  }

  @Transactional
  public void delete(final @NonNull String repoOwner, final @NonNull String repoName) {

    final var repo = this.findByOwnerUsernameAndName(repoOwner, repoName);

    this.repoRepository.deleteById(repo.getId());
  }

  @Transactional
  public void rollbackRepoName(
      final @NonNull String owner, final @NonNull String oldName, final @NonNull String newName) {

    final var repo = this.findByOwnerUsernameAndName(owner, newName);

    repo.setName(oldName);

    this.repoRepository.save(repo);
  }

  @Transactional
  public void delete(
      final @NonNull UUID tenantId, final @NonNull String repoName, final @NonNull String owner) {

    final var ownerTenant = this.getTenantByUsername(owner);

    if (!tenantId.equals(ownerTenant.getId())) {
      throw new ItemNotFoundException("invalidDeleteRequest");
    }

    final var repo =
        this.repoRepository
            .findByOwnerIdAndName(tenantId, repoName)
            .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));

    this.repoRepository.deleteById(repo.getId());

    this.eventPublisher.publishEvent(new RepoDeletedEvent(owner, repoName));
  }

  public @NonNull Page<RepoInfo> findAllByOwner(
      final @NonNull String owner,
      final @NonNull Pageable pageable,
      final @org.jspecify.annotations.Nullable UUID requesterId) {

    final var ownerTenant = this.tenantRepository.findByUsername(owner);
    final boolean isOwner =
        requesterId != null
            && ownerTenant.isPresent()
            && ownerTenant.get().getId().equals(requesterId);

    if (isOwner || (requesterId != null && this.isAdmin(requesterId))) {
      return this.repoRepository
          .findAllByOwnerUsername(owner, pageable)
          .map(this.repoMapper::toDto);
    }

    return this.repoRepository
        .findAllByOwnerUsernameAndIsPrivateFalse(owner, pageable)
        .map(this.repoMapper::toDto);
  }

  public @NonNull RepoInfo findByOwnerAndName(
      final @NonNull String owner,
      final @NonNull String repoName,
      final @org.jspecify.annotations.Nullable UUID requesterId) {

    final var repo = this.findByOwnerUsernameAndName(owner, repoName);

    if (repo.isPrivate()) {
      final var ownerTenant = this.tenantRepository.findByUsername(owner);
      final boolean isOwner =
          requesterId != null
              && ownerTenant.isPresent()
              && ownerTenant.get().getId().equals(requesterId);
      final boolean isAdmin = requesterId != null && this.isAdmin(requesterId);
      if (!isOwner && !isAdmin) {
        throw new AccessNotAllowedException("repoAccessDenied");
      }
    }

    return this.repoMapper.toDto(repo);
  }

  public void assertUserCanAccessRepo(
      final @org.jspecify.annotations.Nullable UUID tenantId,
      final @NonNull String owner,
      final @NonNull String repoName) {

    final var repo = this.findByOwnerUsernameAndName(owner, repoName);

    if (repo.isPrivate()) {
      if (tenantId == null) {
        throw new AccessNotAllowedException("repoAccessDenied");
      }
      final var tenant = this.getTenantById(tenantId);
      final var repoOwner = this.getTenantByUsername(owner);
      this.checkIsRepoOwnerOrAdmin(tenant, repoOwner);
    }
  }

  private boolean isAdmin(final @NonNull UUID tenantId) {
    return this.tenantRepository.findById(tenantId).map(Tenant::isAdmin).orElse(false);
  }

  private void checkIsRepoOwnerOrAdmin(
      final @NonNull Tenant tenant, final @NonNull Tenant repoOwner) {

    if (tenant.getId().equals(repoOwner.getId()) || tenant.isAdmin()) {
      return;
    }

    throw new AccessNotAllowedException("repoAccessDenied");
  }

  private @NonNull Tenant getTenantById(final @NonNull UUID tenantId) {

    return this.tenantRepository
        .findById(tenantId)
        .orElseThrow(() -> new ItemNotFoundException("userNotFound"));
  }

  private @NonNull Tenant getTenantByUsername(final @NonNull String username) {

    return this.tenantRepository
        .findByUsername(username)
        .orElseThrow(() -> new ItemNotFoundException("userNotFound"));
  }

  private @NonNull Repo findByOwnerUsernameAndName(
      final @NonNull String owner, final @NonNull String repoName) {

    return this.repoRepository
        .findByOwnerUsernameAndName(owner, repoName)
        .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));
  }
}
