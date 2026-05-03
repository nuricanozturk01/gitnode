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

import com.nuricanozturk.originhub.repo.dtos.RepoForm;
import com.nuricanozturk.originhub.repo.dtos.RepoInfo;
import com.nuricanozturk.originhub.repo.mappers.RepoMapper;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.AccessNotAllowedException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.events.RepoCreatedEvent;
import com.nuricanozturk.originhub.shared.repo.events.RepoDeletedEvent;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class RepoService {

  /** Initial branch name for new repositories; not client-configurable. */
  public static final @NonNull String NEW_REPO_DEFAULT_BRANCH = "main";

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
            .orElseThrow(() -> new ItemNotFoundException("repoNotFound"));

    if (!form.getName().equals(repo.getName())) {
      repo.setName(form.getName());
    }

    repo.setDescription(form.getDescription());
    if (form.getTopics() != null) {
      repo.setTopics(form.getTopics());
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
            .orElseThrow(() -> new ItemNotFoundException("repoNotFound"));

    this.repoRepository.deleteById(repo.getId());

    this.eventPublisher.publishEvent(new RepoDeletedEvent(owner, repoName));
  }

  public @NonNull List<RepoInfo> findAllByOwner(final @NonNull String owner) {

    final var repos = this.repoRepository.findAllByOwnerUsername(owner);

    return repos.stream().map(this.repoMapper::toDto).toList();
  }

  public @NonNull RepoInfo findByOwnerAndName(
      final @NonNull String owner, final @NonNull String repoName) {

    final var repo = this.findByOwnerUsernameAndName(owner, repoName);

    return this.repoMapper.toDto(repo);
  }

  public void assertUserCanAccessRepo(
      final @NonNull UUID tenantId, final @NonNull String owner, final @NonNull String repoName) {

    final var tenant = this.getTenantById(tenantId);
    final var repoOwner = this.getTenantByUsername(owner);

    this.checkIsRepoOwnerOrAdmin(tenant, repoOwner);
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
        .orElseThrow(() -> new ItemNotFoundException("repoNotFound"));
  }
}
