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

import dev.gitnode.os.events.tag.ReleaseChangedEvent;
import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.repo.api.CollaboratorAccessPort;
import dev.gitnode.os.repo.api.CollaboratorPermission;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.repo.cache.RepoCacheInvalidator;
import dev.gitnode.os.shared.audit.annotations.Audited;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemAlreadyExistsException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.tag.dtos.CreateReleaseForm;
import dev.gitnode.os.tag.dtos.ReleaseInfo;
import dev.gitnode.os.tag.dtos.TenantReleaseInfo;
import dev.gitnode.os.tag.dtos.UpdateReleaseForm;
import dev.gitnode.os.tag.entities.Release;
import dev.gitnode.os.tag.mappers.ReleaseMapper;
import dev.gitnode.os.tag.repositories.ReleaseRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
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
  private final RepoLookupPort repos;
  private final TenantQueryPort tenants;
  private final ReleaseMapper releaseMapper;
  private final RepoCacheInvalidator cacheInvalidator;
  private final CollaboratorAccessPort collaboratorAccess;
  private final ReleaseAssetService assetService;
  private final ApplicationEventPublisher eventPublisher;

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
        this.tenants
            .findById(authorId)
            .orElseThrow(() -> new ItemNotFoundException("Author not found"));

    if (this.releaseRepository.existsByRepoIdAndTagName(repo.id(), form.getTagName())) {
      throw new ItemAlreadyExistsException("releaseAlreadyExists: " + form.getTagName());
    }

    final var release = this.buildRelease(repo, author, form);
    final var saved =
        this.releaseMapper.toInfo(
            this.releaseRepository.save(release), this.releaseMapper.toTenantInfo(author));
    this.cacheInvalidator.evictTags(owner, repoName);
    this.publishReleaseEvent(repo.id(), release, authorId, false);
    return saved;
  }

  /**
   * Tells the Actions engine what GitHub would report: "created" for a new release and, once it is
   * public, "published" plus "released" or "prereleased". A draft that is published later reports
   * only the public part.
   */
  private void publishReleaseEvent(
      final UUID repoId, final Release release, final UUID actorId, final boolean wasDraft) {

    final var actions = new java.util.ArrayList<String>();
    if (!wasDraft) {
      actions.add("created");
    }
    if (!release.isDraft()) {
      actions.add("published");
      actions.add(release.isPrerelease() ? "prereleased" : "released");
    }
    if (actions.isEmpty()) {
      return;
    }
    this.eventPublisher.publishEvent(
        new ReleaseChangedEvent(
            repoId,
            release.getTagName(),
            release.getName(),
            release.isPrerelease(),
            release.isDraft(),
            List.copyOf(actions),
            actorId));
  }

  public List<ReleaseInfo> getAll(final String owner, final String repoName) {

    final var repo = this.findRepo(owner, repoName);
    final var latestId = this.effectiveLatestId(repo.id());
    final var releases = this.releaseRepository.findAllByRepoIdOrderByCreatedAtDesc(repo.id());
    final var authors = this.authors(releases);
    return releases.stream().map(release -> this.toInfo(release, latestId, authors)).toList();
  }

  public ReleaseInfo getById(final String owner, final String repoName, final UUID id) {
    final var repo = this.findRepo(owner, repoName);
    final var latestId = this.effectiveLatestId(repo.id());
    return this.releaseRepository
        .findByIdAndRepoId(id, repo.id())
        .map(release -> this.toInfo(release, latestId))
        .orElseThrow(() -> new ItemNotFoundException(ERR_RELEASE_NOT_FOUND));
  }

  public Optional<ReleaseInfo> findByTagName(
      final String owner, final String repoName, final String tagName) {

    final var repo = this.findRepo(owner, repoName);
    final var latestId = this.effectiveLatestId(repo.id());
    return this.releaseRepository
        .findByRepoIdAndTagName(repo.id(), tagName)
        .map(release -> this.toInfo(release, latestId));
  }

  public Optional<ReleaseInfo> findLatest(final String owner, final String repoName) {

    final var repo = this.findRepo(owner, repoName);
    return this.effectiveLatest(repo.id()).map(release -> this.toInfo(release, release.getId()));
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
    return this.toInfo(release, latestId, this.authors(List.of(release)));
  }

  private ReleaseInfo toInfo(
      final Release release,
      final @Nullable UUID latestId,
      final Map<UUID, TenantReleaseInfo> authors) {
    final var author = this.releaseMapper.toTenantInfoOrGhost(release.getAuthorId(), authors);
    return this.releaseMapper.toInfo(release, author).toBuilder()
        .isLatest(release.getId().equals(latestId))
        .build();
  }

  private Map<UUID, TenantReleaseInfo> authors(final Collection<Release> releases) {
    final var ids = releases.stream().map(Release::getAuthorId).collect(Collectors.toSet());
    return this.tenants.findAllByIds(new HashSet<>(ids)).values().stream()
        .collect(Collectors.toMap(TenantRef::id, this.releaseMapper::toTenantInfo));
  }

  /** Pins a published, stable release as the repository's latest. */
  @Transactional
  public ReleaseInfo markLatest(
      final UUID id, final UUID requesterId, final String owner, final String repoName) {
    final var repo = this.findRepo(owner, repoName);
    this.assertCanManage(repo, requesterId);
    final var release =
        this.releaseRepository
            .findByIdAndRepoId(id, repo.id())
            .orElseThrow(() -> new ItemNotFoundException(ERR_RELEASE_NOT_FOUND));
    if (release.isDraft() || release.isPrerelease()) {
      throw new BadRequestException("Only a published, non-prerelease release can be the latest");
    }
    this.releaseRepository.clearPinnedLatest(repo.id());
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
            .findByIdAndRepoId(id, repo.id())
            .orElseThrow(() -> new ItemNotFoundException(ERR_RELEASE_NOT_FOUND));

    this.assertCanManage(repo, requesterId);
    final boolean wasDraft = release.isDraft();
    this.applyUpdates(form, release);
    final var updated = this.toInfo(this.releaseRepository.save(release), null);
    this.cacheInvalidator.evictTags(repoOwnerUsername, repo.name());
    if (wasDraft && !release.isDraft()) {
      this.publishReleaseEvent(repo.id(), release, requesterId, true);
    }
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
            .findByIdAndRepoId(id, repo.id())
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
        .findByRepoIdAndTagName(repo.id(), tagName)
        .ifPresent(release -> this.assetService.purgeFiles(release.getId()));
    this.releaseRepository.deleteByRepoIdAndTagName(repo.id(), tagName);
    this.cacheInvalidator.evictTags(owner, repoName);
  }

  public Optional<ReleaseInfo> findByRepoIdAndTagName(final UUID repoId, final String tagName) {

    return this.releaseRepository
        .findByRepoIdAndTagName(repoId, tagName)
        .map(release -> this.toInfo(release, null));
  }

  public RepoRef findRepo(final String owner, final String repoName) {
    return this.repos
        .find(owner, repoName)
        .orElseThrow(() -> new ItemNotFoundException(ERR_REPO_NOT_FOUND));
  }

  private Release buildRelease(
      final RepoRef repo, final TenantRef author, final CreateReleaseForm form) {

    final var release = new Release();
    release.setRepoId(repo.id());
    release.setAuthorId(author.id());
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
    return requesterId.equals(repo.ownerId())
        || this.collaboratorAccess.hasPermission(
            repo.id(), requesterId, CollaboratorPermission.PUSH);
  }

  private void assertCanManage(final RepoRef repo, final UUID requesterId) {
    if (!requesterId.equals(repo.ownerId())
        && !this.collaboratorAccess.hasPermission(
            repo.id(), requesterId, CollaboratorPermission.PUSH)) {
      throw new AccessNotAllowedException("notAuthorized");
    }
  }
}
