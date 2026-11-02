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
package dev.gitnode.os.tag.services;

import dev.gitnode.os.shared.audit.annotations.Audited;
import dev.gitnode.os.shared.cache.RepoCacheInvalidator;
import dev.gitnode.os.shared.collaborator.dtos.CollaboratorPermission;
import dev.gitnode.os.shared.collaborator.services.CollaboratorAccessPort;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemAlreadyExistsException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.repo.entities.Repo;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import dev.gitnode.os.tag.dtos.CreateReleaseForm;
import dev.gitnode.os.tag.dtos.ReleaseInfo;
import dev.gitnode.os.tag.dtos.UpdateReleaseForm;
import dev.gitnode.os.tag.entities.Release;
import dev.gitnode.os.tag.mappers.ReleaseMapper;
import dev.gitnode.os.tag.repositories.ReleaseRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
@NullMarked
public class ReleaseTxService {

  private static final String ERR_REPO_NOT_FOUND = "Repo not found.";
  private static final String ERR_RELEASE_NOT_FOUND = "Release not found.";

  private final ReleaseRepository releaseRepository;
  private final RepoRepository repoRepository;
  private final TenantRepository tenantRepository;
  private final ReleaseMapper releaseMapper;
  private final RepoCacheInvalidator cacheInvalidator;
  private final CollaboratorAccessPort collaboratorAccess;
  private final ReleaseAssetService assetService;

  @Audited(
      action = "CREATE_RELEASE",
      entityType = "RELEASE",
      entityIdSpEL = "#result.id().toString()",
      detailsSpEL = "'repo=' + #owner + '/' + #repoName + ', tag=' + #form.tagName")
  @Transactional
  public ReleaseInfo create(
      final String owner,
      final String repoName,
      final UUID authorId,
      final CreateReleaseForm form) {

    final var repo = this.findRepo(owner, repoName);
    final var author =
        this.tenantRepository
            .findById(authorId)
            .orElseThrow(() -> new ItemNotFoundException("Author not found"));

    if (this.releaseRepository.existsByRepoIdAndTagName(repo.getId(), form.getTagName())) {
      throw new ItemAlreadyExistsException("releaseAlreadyExists: " + form.getTagName());
    }

    final var release = this.buildRelease(repo, author, form);
    final var saved = this.releaseMapper.toInfo(this.releaseRepository.save(release));
    this.cacheInvalidator.evictTags(owner, repoName);
    return saved;
  }

  public List<ReleaseInfo> getAll(final String owner, final String repoName) {

    final var repo = this.findRepo(owner, repoName);
    final var latestId = this.effectiveLatestId(repo.getId());
    return this.releaseRepository.findAllByRepoIdOrderByCreatedAtDesc(repo.getId()).stream()
        .map(release -> this.toInfo(release, latestId))
        .toList();
  }

  public ReleaseInfo getById(final String owner, final String repoName, final UUID id) {
    final var repo = this.findRepo(owner, repoName);
    final var latestId = this.effectiveLatestId(repo.getId());
    return this.releaseRepository
        .findByIdAndRepoId(id, repo.getId())
        .map(release -> this.toInfo(release, latestId))
        .orElseThrow(() -> new ItemNotFoundException(ERR_RELEASE_NOT_FOUND));
  }

  public Optional<ReleaseInfo> findByTagName(
      final String owner, final String repoName, final String tagName) {

    final var repo = this.findRepo(owner, repoName);
    final var latestId = this.effectiveLatestId(repo.getId());
    return this.releaseRepository
        .findByRepoIdAndTagName(repo.getId(), tagName)
        .map(release -> this.toInfo(release, latestId));
  }

  public Optional<ReleaseInfo> findLatest(final String owner, final String repoName) {

    final var repo = this.findRepo(owner, repoName);
    return this.effectiveLatest(repo.getId()).map(release -> this.toInfo(release, release.getId()));
  }

  /** The pinned release when it is still published and stable, else the newest such release. */
  private Optional<Release> effectiveLatest(final UUID repoId) {
    return this.releaseRepository
        .findFirstByRepoIdAndPinnedLatestTrueAndIsDraftFalseAndIsPrereleaseFalse(repoId)
        .or(
            () ->
                this.releaseRepository
                    .findFirstByRepoIdAndIsDraftFalseAndIsPrereleaseFalseOrderByPublishedAtDesc(
                        repoId));
  }

  private @Nullable UUID effectiveLatestId(final UUID repoId) {
    return this.effectiveLatest(repoId).map(Release::getId).orElse(null);
  }

  private ReleaseInfo toInfo(final Release release, final @Nullable UUID latestId) {
    return this.releaseMapper.toInfo(release).toBuilder()
        .isLatest(release.getId().equals(latestId))
        .build();
  }

  /** Pins a published, stable release as the repository's latest. */
  @Transactional
  public ReleaseInfo markLatest(
      final UUID id, final UUID requesterId, final String owner, final String repoName) {
    final var repo = this.findRepo(owner, repoName);
    this.assertCanManage(repo, requesterId);
    final var release =
        this.releaseRepository
            .findByIdAndRepoId(id, repo.getId())
            .orElseThrow(() -> new ItemNotFoundException(ERR_RELEASE_NOT_FOUND));
    if (release.isDraft() || release.isPrerelease()) {
      throw new BadRequestException("Only a published, non-prerelease release can be the latest");
    }
    this.releaseRepository.clearPinnedLatest(repo.getId());
    release.setPinnedLatest(true);
    this.releaseRepository.save(release);
    this.cacheInvalidator.evictTags(owner, repoName);
    return this.toInfo(release, release.getId());
  }

  @Transactional
  public ReleaseInfo update(
      final UUID id,
      final UUID requesterId,
      final String repoOwnerUsername,
      final String repoName,
      final UpdateReleaseForm form) {

    final var repo = this.findRepo(repoOwnerUsername, repoName);
    final var release =
        this.releaseRepository
            .findByIdAndRepoId(id, repo.getId())
            .orElseThrow(() -> new ItemNotFoundException(ERR_RELEASE_NOT_FOUND));

    this.assertCanManage(repo, requesterId);
    this.applyUpdates(form, release);
    final var updated = this.releaseMapper.toInfo(this.releaseRepository.save(release));
    this.cacheInvalidator.evictTags(repoOwnerUsername, release.getRepo().getName());
    return updated;
  }

  @Audited(
      action = "DELETE_RELEASE",
      entityType = "RELEASE",
      entityIdSpEL = "#id.toString()",
      detailsSpEL = "'owner=' + #repoOwnerUsername")
  @Transactional
  public void delete(
      final UUID id,
      final UUID requesterId,
      final String repoOwnerUsername,
      final String repoName) {

    final var repo = this.findRepo(repoOwnerUsername, repoName);
    final var release =
        this.releaseRepository
            .findByIdAndRepoId(id, repo.getId())
            .orElseThrow(() -> new ItemNotFoundException(ERR_RELEASE_NOT_FOUND));

    this.assertCanManage(repo, requesterId);
    this.releaseRepository.delete(release);
    this.assetService.purgeFiles(release.getId());
    this.cacheInvalidator.evictTags(repoOwnerUsername, repoName);
  }

  @Transactional
  public void deleteByTagName(final String owner, final String repoName, final String tagName) {

    final var repo = this.findRepo(owner, repoName);
    this.releaseRepository
        .findByRepoIdAndTagName(repo.getId(), tagName)
        .ifPresent(release -> this.assetService.purgeFiles(release.getId()));
    this.releaseRepository.deleteByRepoIdAndTagName(repo.getId(), tagName);
    this.cacheInvalidator.evictTags(owner, repoName);
  }

  public Optional<ReleaseInfo> findByRepoIdAndTagName(final UUID repoId, final String tagName) {

    return this.releaseRepository
        .findByRepoIdAndTagName(repoId, tagName)
        .map(this.releaseMapper::toInfo);
  }

  public Repo findRepo(final String owner, final String repoName) {
    return this.repoRepository
        .findByOwnerUsernameAndName(owner, repoName)
        .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));
  }

  private Release buildRelease(final Repo repo, final Tenant author, final CreateReleaseForm form) {

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

  private void applyUpdates(final UpdateReleaseForm form, final Release release) {
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

  public void assertCanManage(final String owner, final String repoName, final UUID requesterId) {
    this.assertCanManage(this.findRepo(owner, repoName), requesterId);
  }

  public boolean canManage(
      final String owner, final String repoName, final @Nullable UUID requesterId) {
    if (requesterId == null) return false;
    final var repo = this.findRepo(owner, repoName);
    return requesterId.equals(repo.getOwner().getId())
        || this.collaboratorAccess.hasPermission(
            repo.getId(), requesterId, CollaboratorPermission.PUSH);
  }

  private void assertCanManage(final Repo repo, final UUID requesterId) {
    if (!requesterId.equals(repo.getOwner().getId())
        && !this.collaboratorAccess.hasPermission(
            repo.getId(), requesterId, CollaboratorPermission.PUSH)) {
      throw new AccessNotAllowedException("notAuthorized");
    }
  }
}
