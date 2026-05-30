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
package com.nuricanozturk.originhub.tag.services;

import com.nuricanozturk.originhub.shared.errorhandling.exceptions.AccessNotAllowedException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemAlreadyExistsException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import com.nuricanozturk.originhub.tag.dtos.CreateReleaseForm;
import com.nuricanozturk.originhub.tag.dtos.ReleaseInfo;
import com.nuricanozturk.originhub.tag.dtos.UpdateReleaseForm;
import com.nuricanozturk.originhub.tag.entities.Release;
import com.nuricanozturk.originhub.tag.mappers.ReleaseMapper;
import com.nuricanozturk.originhub.tag.repositories.ReleaseRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
@NullMarked
public class ReleaseTxService {

  private static final String ERR_REPO_NOT_FOUND = "Repo not found.";
  private static final String ERR_RELEASE_NOT_FOUND = "Release not found.";

  private final @NonNull ReleaseRepository releaseRepository;
  private final @NonNull RepoRepository repoRepository;
  private final @NonNull TenantRepository tenantRepository;
  private final @NonNull ReleaseMapper releaseMapper;

  @Transactional
  public @NonNull ReleaseInfo create(
      final @NonNull String owner,
      final @NonNull String repoName,
      final @NonNull UUID authorId,
      final @NonNull CreateReleaseForm form) {

    final var repo = this.findRepo(owner, repoName);
    final var author =
        this.tenantRepository
            .findById(authorId)
            .orElseThrow(() -> new ItemNotFoundException("Author not found"));

    if (this.releaseRepository.existsByRepoIdAndTagName(repo.getId(), form.getTagName())) {
      throw new ItemAlreadyExistsException("releaseAlreadyExists: " + form.getTagName());
    }

    final var release = this.buildRelease(repo, author, form);
    return this.releaseMapper.toInfo(this.releaseRepository.save(release));
  }

  public @NonNull List<ReleaseInfo> getAll(
      final @NonNull String owner, final @NonNull String repoName) {

    final var repo = this.findRepo(owner, repoName);
    return this.releaseRepository.findAllByRepoIdOrderByCreatedAtDesc(repo.getId()).stream()
        .map(this.releaseMapper::toInfo)
        .toList();
  }

  public @NonNull ReleaseInfo getById(final @NonNull UUID id) {
    return this.releaseRepository
        .findById(id)
        .map(this.releaseMapper::toInfo)
        .orElseThrow(() -> new ItemNotFoundException(ERR_RELEASE_NOT_FOUND));
  }

  public @NonNull Optional<ReleaseInfo> findByTagName(
      final @NonNull String owner, final @NonNull String repoName, final @NonNull String tagName) {

    final var repo = this.findRepo(owner, repoName);
    return this.releaseRepository
        .findByRepoIdAndTagName(repo.getId(), tagName)
        .map(this.releaseMapper::toInfo);
  }

  public @NonNull Optional<ReleaseInfo> findLatest(
      final @NonNull String owner, final @NonNull String repoName) {

    final var repo = this.findRepo(owner, repoName);
    return this.releaseRepository
        .findFirstByRepoIdAndIsDraftFalseAndIsPrereleaseFalseOrderByPublishedAtDesc(repo.getId())
        .map(this.releaseMapper::toInfo);
  }

  @Transactional
  public @NonNull ReleaseInfo update(
      final @NonNull UUID id,
      final @NonNull UUID requesterId,
      final @NonNull String repoOwnerUsername,
      final @NonNull UpdateReleaseForm form) {

    final var release =
        this.releaseRepository
            .findById(id)
            .orElseThrow(() -> new ItemNotFoundException(ERR_RELEASE_NOT_FOUND));

    this.assertCanModify(requesterId, release.getAuthor(), repoOwnerUsername);
    this.applyUpdates(form, release);
    return this.releaseMapper.toInfo(this.releaseRepository.save(release));
  }

  @Transactional
  public void delete(
      final @NonNull UUID id,
      final @NonNull UUID requesterId,
      final @NonNull String repoOwnerUsername) {

    final var release =
        this.releaseRepository
            .findById(id)
            .orElseThrow(() -> new ItemNotFoundException(ERR_RELEASE_NOT_FOUND));

    this.assertCanModify(requesterId, release.getAuthor(), repoOwnerUsername);
    this.releaseRepository.delete(release);
  }

  @Transactional
  public void deleteByTagName(
      final @NonNull String owner, final @NonNull String repoName, final @NonNull String tagName) {

    final var repo = this.findRepo(owner, repoName);
    this.releaseRepository.deleteByRepoIdAndTagName(repo.getId(), tagName);
  }

  public @NonNull Optional<ReleaseInfo> findByRepoIdAndTagName(
      final @NonNull UUID repoId, final @NonNull String tagName) {

    return this.releaseRepository
        .findByRepoIdAndTagName(repoId, tagName)
        .map(this.releaseMapper::toInfo);
  }

  public @NonNull Repo findRepo(final @NonNull String owner, final @NonNull String repoName) {
    return this.repoRepository
        .findByOwnerUsernameAndName(owner, repoName)
        .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));
  }

  private @NonNull Release buildRelease(
      final @NonNull Repo repo,
      final @NonNull Tenant author,
      final @NonNull CreateReleaseForm form) {

    final var release = new Release();
    release.setRepo(repo);
    release.setAuthor(author);
    release.setTagName(form.getTagName());
    release.setName(form.getName());
    release.setBody(form.getBody());
    release.setDraft(form.isDraft());
    release.setPrerelease(form.isPrerelease());

    if (!form.isDraft()) {
      release.setPublishedAt(Instant.now());
    }

    return release;
  }

  private void applyUpdates(final @NonNull UpdateReleaseForm form, final @NonNull Release release) {
    if (form.getName() != null) {
      release.setName(form.getName());
    }
    if (form.getBody() != null) {
      release.setBody(form.getBody());
    }
    if (form.getIsDraft() != null) {
      release.setDraft(form.getIsDraft());
      if (!form.getIsDraft() && release.getPublishedAt() == null) {
        release.setPublishedAt(Instant.now());
      }
    }
    if (form.getIsPrerelease() != null) {
      release.setPrerelease(form.getIsPrerelease());
    }
  }

  private void assertCanModify(
      final @NonNull UUID requesterId,
      final @NonNull Tenant author,
      final @NonNull String repoOwnerUsername) {

    if (requesterId.equals(author.getId())) {
      return;
    }

    final var repoOwner = this.tenantRepository.findByUsername(repoOwnerUsername);
    final boolean isRepoOwner =
        repoOwner.isPresent() && requesterId.equals(repoOwner.get().getId());

    if (!isRepoOwner) {
      throw new AccessNotAllowedException("notAuthorized");
    }
  }
}
