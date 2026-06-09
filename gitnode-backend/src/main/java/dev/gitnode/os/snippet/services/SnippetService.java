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
package dev.gitnode.os.snippet.services;

import dev.gitnode.os.events.snippet.SnippetCreatedEvent;
import dev.gitnode.os.events.snippet.SnippetDeletedEvent;
import dev.gitnode.os.events.snippet.SnippetUpdatedEvent;
import dev.gitnode.os.shared.cache.CacheNames;
import dev.gitnode.os.shared.cache.SnippetCacheInvalidator;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.repo.dtos.PageResponse;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import dev.gitnode.os.snippet.dtos.SnippetDetail;
import dev.gitnode.os.snippet.dtos.SnippetForm;
import dev.gitnode.os.snippet.dtos.SnippetInfo;
import dev.gitnode.os.snippet.dtos.SnippetRevisionDetail;
import dev.gitnode.os.snippet.dtos.SnippetRevisionInfo;
import dev.gitnode.os.snippet.dtos.SnippetUpdateForm;
import dev.gitnode.os.snippet.entities.Snippet;
import dev.gitnode.os.snippet.entities.SnippetFile;
import dev.gitnode.os.snippet.entities.SnippetRevision;
import dev.gitnode.os.snippet.entities.SnippetRevisionFile;
import dev.gitnode.os.snippet.entities.Visibility;
import dev.gitnode.os.snippet.mappers.SnippetMapper;
import dev.gitnode.os.snippet.repositories.SnippetRepository;
import dev.gitnode.os.snippet.repositories.SnippetRevisionRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
@NullMarked
public class SnippetService {

  private static final String ERR_USER_NOT_FOUND = "userNotFound";

  private final SnippetRepository snippetRepository;
  private final SnippetRevisionRepository revisionRepository;
  private final TenantRepository tenantRepository;
  private final RepoRepository repoRepository;
  private final SnippetMapper snippetMapper;
  private final SnippetFileStorageService fileStorage;
  private final ApplicationEventPublisher eventPublisher;
  private final SnippetCacheInvalidator snippetCacheInvalidator;

  @Transactional
  public SnippetDetail create(final UUID tenantId, final SnippetForm form) {

    final var owner =
        this.tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new ItemNotFoundException(ERR_USER_NOT_FOUND));

    final var snippet = new Snippet();
    snippet.setOwner(owner);
    snippet.setTitle(form.getTitle());
    snippet.setDescription(form.getDescription());
    snippet.setVisibility(form.getVisibility());

    for (int i = 0; i < form.getFiles().size(); i++) {
      final var fileForm = form.getFiles().get(i);
      final var file = new SnippetFile();
      file.setSnippet(snippet);
      file.setFilename(fileForm.getFilename());
      file.setPosition(i);
      snippet.getFiles().add(file);
    }

    snippet.setFileCount(form.getFiles().size());
    final var saved = this.snippetRepository.save(snippet);

    final Map<String, String> contentByFilename = new HashMap<>();
    for (final var fileForm : form.getFiles()) {
      contentByFilename.put(fileForm.getFilename(), fileForm.getContent());
    }
    for (final var file : saved.getFiles()) {
      this.fileStorage.writeFile(
          owner.getUsername(),
          saved.getId(),
          file.getId(),
          contentByFilename.getOrDefault(file.getFilename(), ""));
    }

    this.snapshotRevision(saved, tenantId, null, contentByFilename);

    this.eventPublisher.publishEvent(
        new SnippetCreatedEvent(saved.getId(), owner.getUsername(), saved.getTitle(), null));

    this.snippetCacheInvalidator.evictPublicList();
    return this.buildDetail(saved, owner.getUsername());
  }

  @CacheEvict(cacheNames = CacheNames.SNIPPET_DETAIL, key = "#snippetId")
  @Transactional
  public SnippetDetail update(
      final UUID tenantId, final UUID snippetId, final SnippetUpdateForm form) {

    final var snippet = this.loadSnippet(snippetId);
    this.requireOwner(snippet, tenantId);
    final var username = snippet.getOwner().getUsername();

    this.applyFieldUpdates(snippet, form);
    final var contentByFilename = this.applyFileUpdates(snippet, form, username);

    final var saved = this.snippetRepository.save(snippet);

    final var resolvedContent = this.resolveFileContents(saved, username, contentByFilename);
    this.snapshotRevision(saved, tenantId, form.getSummary(), resolvedContent);

    this.eventPublisher.publishEvent(
        new SnippetUpdatedEvent(saved.getId(), username, saved.getTitle(), null));

    this.snippetCacheInvalidator.evictPublicList();
    return this.buildDetail(saved, username);
  }

  private void applyFieldUpdates(final Snippet snippet, final SnippetUpdateForm form) {

    if (form.getTitle() != null) {
      snippet.setTitle(form.getTitle());
    }
    if (form.getDescription() != null) {
      snippet.setDescription(form.getDescription());
    }
    if (form.getVisibility() != null) {
      snippet.setVisibility(form.getVisibility());
    }
  }

  private Map<String, String> applyFileUpdates(
      final Snippet snippet, final SnippetUpdateForm form, final String username) {

    final var contentByFilename = new HashMap<String, String>();
    final var files = form.getFiles();

    if (files == null || files.isEmpty()) {
      return contentByFilename;
    }

    this.fileStorage.deleteCurrentFiles(username, snippet.getId());
    snippet.getFiles().clear();

    for (int i = 0; i < files.size(); i++) {
      final var fileForm = files.get(i);
      final var file = new SnippetFile();
      file.setSnippet(snippet);
      file.setFilename(fileForm.getFilename());
      file.setPosition(i);
      snippet.getFiles().add(file);
      contentByFilename.put(fileForm.getFilename(), fileForm.getContent());
    }

    snippet.setFileCount(files.size());
    return contentByFilename;
  }

  private Map<String, String> resolveFileContents(
      final Snippet saved, final String username, final Map<String, String> contentByFilename) {

    if (!contentByFilename.isEmpty()) {
      for (final var file : saved.getFiles()) {
        this.fileStorage.writeFile(
            username,
            saved.getId(),
            file.getId(),
            contentByFilename.getOrDefault(file.getFilename(), ""));
      }
      return contentByFilename;
    }

    final var resolved = new HashMap<String, String>();
    for (final var file : saved.getFiles()) {
      resolved.put(
          file.getFilename(), this.fileStorage.readFile(username, saved.getId(), file.getId()));
    }
    return resolved;
  }

  @CacheEvict(cacheNames = CacheNames.SNIPPET_DETAIL, key = "#snippetId")
  @Transactional
  public void delete(final UUID tenantId, final UUID snippetId) {
    final var snippet = this.loadSnippet(snippetId);
    this.requireOwner(snippet, tenantId);
    final var username = snippet.getOwner().getUsername();
    final var title = snippet.getTitle();
    final var forkedFrom = snippet.getForkedFrom();
    this.snippetRepository.delete(snippet);
    if (forkedFrom != null) {
      this.snippetRepository.decrementForkCount(forkedFrom.getId());
      // The original snippet's forkCount was decremented; evict its stale detail cache entry.
      this.snippetCacheInvalidator.evictDetail(forkedFrom.getId());
    }
    this.fileStorage.deleteSnippetDir(username, snippetId);
    this.eventPublisher.publishEvent(new SnippetDeletedEvent(snippetId, username, title));
    this.snippetCacheInvalidator.evictPublicList();
  }

  @Cacheable(
      cacheNames = CacheNames.SNIPPET_DETAIL,
      key = "#snippetId",
      unless = "#result.visibility().name() == 'PRIVATE'")
  public SnippetDetail get(final UUID snippetId, final @Nullable UUID callerId) {

    final var snippet = this.loadSnippet(snippetId);
    this.checkVisibility(snippet, callerId);
    return this.buildDetail(snippet, snippet.getOwner().getUsername());
  }

  public PageResponse<SnippetInfo> listMine(final UUID tenantId, final int page, final int size) {
    final var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
    return PageResponse.from(
        this.snippetRepository
            .findAllByOwnerIdOrderByCreatedAtDesc(tenantId, pageable)
            .map(this.snippetMapper::toInfo));
  }

  @Cacheable(
      cacheNames = CacheNames.SNIPPET_LIST_PUBLIC,
      key = "#page + ':' + #size + ':' + (#q == null ? '' : #q)")
  public Page<SnippetInfo> listPublic(final int page, final int size, final @Nullable String q) {

    final var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

    if (q != null && !q.isBlank()) {
      return this.snippetRepository.searchPublic(q, pageable).map(this.snippetMapper::toInfo);
    }

    return this.snippetRepository
        .findAllByVisibility(Visibility.PUBLIC, pageable)
        .map(this.snippetMapper::toInfo);
  }

  public Page<SnippetInfo> listByOwner(
      final String ownerUsername, final @Nullable UUID callerId, final int page, final int size) {

    final var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

    final var owner =
        this.tenantRepository
            .findByUsernameOrEmail(ownerUsername)
            .orElseThrow(() -> new ItemNotFoundException(ERR_USER_NOT_FOUND));

    if (callerId != null && callerId.equals(owner.getId())) {
      return this.snippetRepository
          .findAllByOwnerIdOrderByCreatedAtDesc(owner.getId(), pageable)
          .map(this.snippetMapper::toInfo);
    }

    return this.snippetRepository
        .findPublicByOwnerUsername(ownerUsername, pageable)
        .map(this.snippetMapper::toInfo);
  }

  @Transactional
  public SnippetDetail fork(final UUID tenantId, final UUID snippetId) {

    final var original = this.loadSnippet(snippetId);
    this.checkVisibility(original, tenantId);
    final var originalUsername = original.getOwner().getUsername();

    final var forker =
        this.tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new ItemNotFoundException(ERR_USER_NOT_FOUND));

    final var fork = new Snippet();
    fork.setOwner(forker);
    fork.setTitle(original.getTitle());
    fork.setDescription(original.getDescription());
    fork.setVisibility(Visibility.PUBLIC);
    fork.setForkedFrom(original);
    fork.setFileCount(original.getFileCount());

    for (final var originalFile : original.getFiles()) {
      final var file = new SnippetFile();
      file.setSnippet(fork);
      file.setFilename(originalFile.getFilename());
      file.setPosition(originalFile.getPosition());
      fork.getFiles().add(file);
    }

    final var saved = this.snippetRepository.save(fork);
    this.snippetRepository.incrementForkCount(original.getId());

    final Map<String, String> contentByFilename = new HashMap<>();
    for (final var originalFile : original.getFiles()) {
      final var content =
          this.fileStorage.readFile(originalUsername, original.getId(), originalFile.getId());
      contentByFilename.put(originalFile.getFilename(), content);
    }
    for (final var file : saved.getFiles()) {
      this.fileStorage.writeFile(
          forker.getUsername(),
          saved.getId(),
          file.getId(),
          contentByFilename.getOrDefault(file.getFilename(), ""));
    }

    this.snapshotRevision(saved, tenantId, "Forked from " + original.getTitle(), contentByFilename);

    // The fork is always PUBLIC, so the public list cache is now stale.
    // The original snippet's forkCount was incremented, so its detail cache entry is stale too.
    this.snippetCacheInvalidator.evictPublicList();
    this.snippetCacheInvalidator.evictDetail(original.getId());

    return this.buildDetail(saved, forker.getUsername());
  }

  public PageResponse<SnippetRevisionInfo> listRevisions(
      final UUID snippetId, final @Nullable UUID callerId, final int page, final int size) {

    final var snippet = this.loadSnippet(snippetId);
    this.checkVisibility(snippet, callerId);

    final var pageable = PageRequest.of(page, size);
    return PageResponse.from(
        this.revisionRepository
            .findAllBySnippetIdOrderByCreatedAtDesc(snippetId, pageable)
            .map(this.snippetMapper::toRevisionInfo));
  }

  public SnippetRevisionDetail getRevision(
      final UUID snippetId, final UUID revisionId, final @Nullable UUID callerId) {

    final var snippet = this.loadSnippet(snippetId);
    this.checkVisibility(snippet, callerId);
    final var username = snippet.getOwner().getUsername();

    final var revision =
        this.revisionRepository
            .findByIdWithAuthor(revisionId)
            .orElseThrow(() -> new ItemNotFoundException("revisionNotFound"));

    final Map<UUID, String> contentByFileId = new HashMap<>();
    for (final var revFile : revision.getFiles()) {
      final var content =
          this.fileStorage.readRevisionFile(username, snippetId, revisionId, revFile.getId());
      contentByFileId.put(revFile.getId(), content);
    }

    return this.snippetMapper.toRevisionDetail(revision, revision.getFiles(), contentByFileId);
  }

  @CacheEvict(cacheNames = CacheNames.SNIPPET_DETAIL, key = "#snippetId")
  @Transactional
  public SnippetDetail linkRepo(final UUID tenantId, final UUID snippetId, final UUID repoId) {

    final var snippet = this.loadSnippet(snippetId);
    this.requireOwner(snippet, tenantId);
    final var repo =
        this.repoRepository
            .findById(repoId)
            .orElseThrow(() -> new ItemNotFoundException("repoNotFound"));
    snippet.getRepos().add(repo);
    final var saved = this.snippetRepository.save(snippet);
    return this.buildDetail(saved, saved.getOwner().getUsername());
  }

  @CacheEvict(cacheNames = CacheNames.SNIPPET_DETAIL, key = "#snippetId")
  @Transactional
  public SnippetDetail unlinkRepo(final UUID tenantId, final UUID snippetId, final UUID repoId) {

    final var snippet = this.loadSnippet(snippetId);
    this.requireOwner(snippet, tenantId);
    snippet.getRepos().removeIf(r -> r.getId().equals(repoId));
    final var saved = this.snippetRepository.save(snippet);
    return this.buildDetail(saved, saved.getOwner().getUsername());
  }

  public PageResponse<SnippetInfo> listByRepo(
      final String ownerUsername,
      final String repoName,
      final @Nullable UUID callerId,
      final int page,
      final int size) {

    final var repo =
        this.repoRepository
            .findByOwnerUsernameAndName(ownerUsername, repoName)
            .orElseThrow(() -> new ItemNotFoundException("repoNotFound"));

    final boolean isOwner = callerId != null && callerId.equals(repo.getOwner().getId());

    if (repo.isPrivate() && !isOwner) {
      throw new AccessNotAllowedException("repoAccessDenied");
    }

    final var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

    if (isOwner) {
      return PageResponse.from(
          this.snippetRepository
              .findAllByRepoId(repo.getId(), pageable)
              .map(this.snippetMapper::toInfo));
    }

    return PageResponse.from(
        this.snippetRepository
            .findPublicByRepoId(repo.getId(), pageable)
            .map(this.snippetMapper::toInfo));
  }

  SnippetDetail buildDetail(final Snippet snippet, final String username) {
    final Map<UUID, String> contentByFileId = new HashMap<>();
    for (final var file : snippet.getFiles()) {
      final var content = this.fileStorage.readFile(username, snippet.getId(), file.getId());
      contentByFileId.put(file.getId(), content);
    }
    return this.snippetMapper.toDetail(snippet, contentByFileId);
  }

  private void snapshotRevision(
      final Snippet snippet,
      final UUID authorId,
      final @Nullable String summary,
      final Map<String, String> contentByFilename) {

    final var author =
        this.tenantRepository
            .findById(authorId)
            .orElseThrow(() -> new ItemNotFoundException(ERR_USER_NOT_FOUND));
    final var username = snippet.getOwner().getUsername();

    final var revision = new SnippetRevision();
    revision.setSnippet(snippet);
    revision.setAuthor(author);
    revision.setTitle(snippet.getTitle());
    revision.setDescription(snippet.getDescription());
    revision.setSummary(summary);

    for (final var file : snippet.getFiles()) {
      final var revFile = new SnippetRevisionFile();
      revFile.setRevision(revision);
      revFile.setFilename(file.getFilename());
      revFile.setPosition(file.getPosition());
      revision.getFiles().add(revFile);
    }

    final var savedRevision = this.revisionRepository.save(revision);

    for (final var revFile : savedRevision.getFiles()) {
      final var content = contentByFilename.getOrDefault(revFile.getFilename(), "");
      this.fileStorage.writeRevisionFile(
          username, snippet.getId(), savedRevision.getId(), revFile.getId(), content);
    }
  }

  Snippet loadSnippet(final UUID id) {
    return this.snippetRepository
        .findByIdWithOwner(id)
        .orElseThrow(() -> new ItemNotFoundException("snippetNotFound"));
  }

  private void checkVisibility(final Snippet snippet, final @Nullable UUID callerId) {
    if (snippet.getVisibility() == Visibility.PRIVATE
        && (callerId == null || !callerId.equals(snippet.getOwner().getId()))) {
      throw new ItemNotFoundException("snippetNotFound");
    }
  }

  private void requireOwner(final Snippet snippet, final UUID callerId) {
    if (!callerId.equals(snippet.getOwner().getId())) {
      throw new AccessNotAllowedException("notSnippetOwner");
    }
  }
}
