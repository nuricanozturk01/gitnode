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
package dev.gitnode.os.snippet.mappers;

import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.snippet.dtos.SnippetCommentInfo;
import dev.gitnode.os.snippet.dtos.SnippetDetail;
import dev.gitnode.os.snippet.dtos.SnippetFileInfo;
import dev.gitnode.os.snippet.dtos.SnippetForkedFromInfo;
import dev.gitnode.os.snippet.dtos.SnippetInfo;
import dev.gitnode.os.snippet.dtos.SnippetLinkedRepoInfo;
import dev.gitnode.os.snippet.dtos.SnippetOwnerInfo;
import dev.gitnode.os.snippet.dtos.SnippetRevisionDetail;
import dev.gitnode.os.snippet.dtos.SnippetRevisionInfo;
import dev.gitnode.os.snippet.entities.Snippet;
import dev.gitnode.os.snippet.entities.SnippetComment;
import dev.gitnode.os.snippet.entities.SnippetFile;
import dev.gitnode.os.snippet.entities.SnippetRevision;
import dev.gitnode.os.snippet.entities.SnippetRevisionFile;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Builds the API views of snippets. Snippets keep owners, authors and linked repositories by id, so
 * the names are resolved here, in one batch per page, through the identity and repo ports.
 */
@Component
@RequiredArgsConstructor
@NullMarked
public class SnippetMapper {

  private final TenantQueryPort tenants;
  private final RepoLookupPort repos;

  /** Names and repositories needed to render a group of snippets. */
  private record Lookup(Map<UUID, TenantRef> accounts, Map<UUID, RepoRef> repositories) {

    SnippetOwnerInfo owner(final UUID id) {
      final var tenant = this.accounts.get(id);
      return tenant == null
          ? new SnippetOwnerInfo(id, "ghost", "", null)
          : new SnippetOwnerInfo(
              tenant.id(), tenant.username(), tenant.email(), tenant.avatarUrl());
    }
  }

  private Lookup lookup(
      final Collection<Snippet> snippets,
      final Collection<SnippetComment> comments,
      final Collection<SnippetRevision> revisions) {
    final Set<UUID> accountIds = new HashSet<>();
    final Set<UUID> repoIds = new HashSet<>();
    for (final var snippet : snippets) {
      accountIds.add(snippet.getOwnerId());
      repoIds.addAll(snippet.getRepoIds());
      if (snippet.getForkedFrom() != null) {
        accountIds.add(snippet.getForkedFrom().getOwnerId());
      }
    }
    comments.forEach(comment -> accountIds.add(comment.getAuthorId()));
    revisions.forEach(revision -> accountIds.add(revision.getAuthorId()));
    return new Lookup(this.tenants.findAllByIds(accountIds), this.repos.findAllByIds(repoIds));
  }

  public SnippetInfo toInfo(final Snippet snippet) {
    return this.toInfo(snippet, this.lookup(List.of(snippet), List.of(), List.of()));
  }

  public Page<SnippetInfo> toInfoPage(final Page<Snippet> page) {
    final var lookup = this.lookup(page.getContent(), List.of(), List.of());
    return page.map(snippet -> this.toInfo(snippet, lookup));
  }

  private SnippetInfo toInfo(final Snippet snippet, final Lookup lookup) {
    return SnippetInfo.builder()
        .id(snippet.getId())
        .title(snippet.getTitle())
        .description(snippet.getDescription())
        .visibility(snippet.getVisibility())
        .owner(lookup.owner(snippet.getOwnerId()))
        .fileCount(snippet.getFileCount())
        .commentCount(snippet.getCommentCount())
        .forkCount(snippet.getForkCount())
        .forkedFrom(this.forkedFrom(snippet.getForkedFrom(), lookup))
        .repos(this.linkedRepos(snippet, lookup))
        .createdAt(snippet.getCreatedAt())
        .updatedAt(snippet.getUpdatedAt())
        .build();
  }

  public SnippetDetail toDetail(final Snippet snippet, final Map<UUID, String> contentByFileId) {
    final var lookup = this.lookup(List.of(snippet), List.of(), List.of());
    final var files =
        snippet.getFiles().stream()
            .map(f -> this.toFileInfo(f, contentByFileId.getOrDefault(f.getId(), "")))
            .toList();

    return SnippetDetail.builder()
        .id(snippet.getId())
        .title(snippet.getTitle())
        .description(snippet.getDescription())
        .visibility(snippet.getVisibility())
        .owner(lookup.owner(snippet.getOwnerId()))
        .fileCount(snippet.getFileCount())
        .commentCount(snippet.getCommentCount())
        .forkCount(snippet.getForkCount())
        .forkedFrom(this.forkedFrom(snippet.getForkedFrom(), lookup))
        .repos(this.linkedRepos(snippet, lookup))
        .files(files)
        .createdAt(snippet.getCreatedAt())
        .updatedAt(snippet.getUpdatedAt())
        .build();
  }

  public SnippetCommentInfo toCommentInfo(final SnippetComment comment) {
    return this.toCommentInfo(comment, this.lookup(List.of(), List.of(comment), List.of()));
  }

  public Page<SnippetCommentInfo> toCommentInfoPage(final Page<SnippetComment> page) {
    final var lookup = this.lookup(List.of(), page.getContent(), List.of());
    return page.map(comment -> this.toCommentInfo(comment, lookup));
  }

  private SnippetCommentInfo toCommentInfo(final SnippetComment comment, final Lookup lookup) {
    return SnippetCommentInfo.builder()
        .id(comment.getId())
        .body(comment.getBody())
        .author(lookup.owner(comment.getAuthorId()))
        .createdAt(comment.getCreatedAt())
        .updatedAt(comment.getUpdatedAt())
        .build();
  }

  public SnippetRevisionInfo toRevisionInfo(final SnippetRevision revision) {
    return this.toRevisionInfo(revision, this.lookup(List.of(), List.of(), List.of(revision)));
  }

  public Page<SnippetRevisionInfo> toRevisionInfoPage(final Page<SnippetRevision> page) {
    final var lookup = this.lookup(List.of(), List.of(), page.getContent());
    return page.map(revision -> this.toRevisionInfo(revision, lookup));
  }

  private SnippetRevisionInfo toRevisionInfo(final SnippetRevision revision, final Lookup lookup) {
    return SnippetRevisionInfo.builder()
        .id(revision.getId())
        .summary(revision.getSummary())
        .author(lookup.owner(revision.getAuthorId()))
        .createdAt(revision.getCreatedAt())
        .build();
  }

  public SnippetRevisionDetail toRevisionDetail(
      final SnippetRevision revision,
      final List<SnippetRevisionFile> files,
      final Map<UUID, String> contentByFileId) {
    final var lookup = this.lookup(List.of(), List.of(), List.of(revision));
    return SnippetRevisionDetail.builder()
        .id(revision.getId())
        .title(revision.getTitle())
        .description(revision.getDescription())
        .summary(revision.getSummary())
        .author(lookup.owner(revision.getAuthorId()))
        .files(
            files.stream()
                .map(f -> this.toRevisionFileInfo(f, contentByFileId.getOrDefault(f.getId(), "")))
                .toList())
        .createdAt(revision.getCreatedAt())
        .build();
  }

  private SnippetFileInfo toFileInfo(final SnippetFile file, final String content) {
    return SnippetFileInfo.builder()
        .id(file.getId())
        .filename(file.getFilename())
        .content(content)
        .position(file.getPosition())
        .build();
  }

  private SnippetFileInfo toRevisionFileInfo(final SnippetRevisionFile file, final String content) {
    return SnippetFileInfo.builder()
        .id(file.getId())
        .filename(file.getFilename())
        .content(content)
        .position(file.getPosition())
        .build();
  }

  private @Nullable SnippetForkedFromInfo forkedFrom(
      final @Nullable Snippet forkedFrom, final Lookup lookup) {
    if (forkedFrom == null) {
      return null;
    }
    return SnippetForkedFromInfo.builder()
        .id(forkedFrom.getId())
        .title(forkedFrom.getTitle())
        .owner(lookup.owner(forkedFrom.getOwnerId()))
        .build();
  }

  private List<SnippetLinkedRepoInfo> linkedRepos(final Snippet snippet, final Lookup lookup) {
    return snippet.getRepoIds().stream()
        .map(lookup.repositories()::get)
        .filter(java.util.Objects::nonNull)
        .map(repo -> SnippetLinkedRepoInfo.builder().id(repo.id()).name(repo.name()).build())
        .toList();
  }
}
