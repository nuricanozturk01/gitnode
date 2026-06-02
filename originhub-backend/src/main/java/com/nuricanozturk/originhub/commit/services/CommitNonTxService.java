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
package com.nuricanozturk.originhub.commit.services;

import static com.nuricanozturk.originhub.shared.util.FileDiffParser.parseFileDiff;
import static com.nuricanozturk.originhub.shared.util.FileDiffParser.prepareTreeParser;

import com.nuricanozturk.originhub.shared.cache.CacheNames;
import com.nuricanozturk.originhub.shared.commit.dtos.AuthorInfo;
import com.nuricanozturk.originhub.shared.commit.dtos.CommitDetail;
import com.nuricanozturk.originhub.shared.commit.dtos.CommitInfo;
import com.nuricanozturk.originhub.shared.commit.dtos.CommitStats;
import com.nuricanozturk.originhub.shared.commit.dtos.FileDiff;
import com.nuricanozturk.originhub.shared.commit.dtos.PagedResult;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.git.provider.GitProvider;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class CommitNonTxService {

  private static final int MAX_FILES_PER_COMMIT = 50;
  private static final int DEFAULT_SHORT_SHA_LENGTH = 7;

  private final GitProvider gitProvider;
  private final TenantRepository tenantRepository;

  @Cacheable(
      cacheNames = CacheNames.COMMITS,
      key = "#owner + ':' + #repoName + ':' + #branch + ':' + #page + ':' + #size")
  public PagedResult<CommitInfo> getCommits(
      final String owner,
      final String repoName,
      final String branch,
      final int page,
      final int size)
      throws IOException {

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {

      final var branchRef = gitRepo.findRef(Constants.R_HEADS + branch);

      if (branchRef == null) {
        return this.getEmptyPage(page, size);
      }

      return this.getCommits(gitRepo, branchRef, page, size);
    }
  }

  public CommitDetail getCommit(final String owner, final String repoName, final String sha)
      throws IOException {

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {

      final var objectId = gitRepo.resolve(sha);

      if (objectId == null) {
        throw new ItemNotFoundException("commitNotFound: " + sha);
      }

      return this.getCommitDetail(gitRepo, objectId);
    }
  }

  public List<FileDiff> getCommitDiff(final String owner, final String repoName, final String sha)
      throws IOException {

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {

      final var objectId = gitRepo.resolve(sha);

      if (objectId == null) {
        throw new ItemNotFoundException("commitNotFound: " + sha);
      }

      return this.getFileDiffs(gitRepo, objectId);
    }
  }

  private PagedResult<CommitInfo> getCommits(
      final Repository gitRepo, final Ref branchRef, final int page, final int size)
      throws IOException {

    try (final var walk = new RevWalk(gitRepo)) {
      walk.markStart(walk.parseCommit(branchRef.getObjectId()));
      walk.sort(RevSort.COMMIT_TIME_DESC);

      int toSkip = page * size;
      final var pageRevCommits = new ArrayList<RevCommit>(size);
      RevCommit cmt;

      while ((cmt = walk.next()) != null) {
        if (toSkip > 0) {
          toSkip--;
          continue;
        }
        pageRevCommits.add(cmt);
        if (pageRevCommits.size() == size) break;
      }

      final boolean hasNext = walk.next() != null;

      if (pageRevCommits.isEmpty()) {
        return this.buildPageResult(List.of(), page, size, hasNext);
      }

      final var emails =
          pageRevCommits.stream()
              .map(c -> c.getAuthorIdent().getEmailAddress())
              .collect(Collectors.toSet());
      final var tenantsByEmail =
          this.tenantRepository.findAllByEmailIn(emails).stream()
              .collect(Collectors.toMap(Tenant::getEmail, t -> t));

      final var pageCommits = new ArrayList<CommitInfo>(pageRevCommits.size());
      for (final var rc : pageRevCommits) {
        pageCommits.add(this.toCommitInfo(gitRepo, rc, tenantsByEmail));
      }

      return this.buildPageResult(pageCommits, page, size, hasNext);
    }
  }

  private PagedResult<CommitInfo> buildPageResult(
      final List<CommitInfo> items, final int page, final int size, final boolean hasNext) {

    final boolean hasPrevious = page > 0;
    final int totalPages = hasNext ? page + 2 : (hasPrevious ? page + 1 : 1);
    final long totalItems = (long) page * size + items.size() + (hasNext ? 1 : 0);
    return new PagedResult<>(items, page, size, totalItems, totalPages, hasNext, hasPrevious);
  }

  private List<FileDiff> getFileDiffs(final Repository gitRepo, final ObjectId objectId)
      throws IOException {

    try (final var walk = new RevWalk(gitRepo)) {
      return this.getFileDiffs(gitRepo, walk.parseCommit(objectId));
    }
  }

  private List<FileDiff> getFileDiffs(final Repository gitRepo, final RevCommit commit)
      throws IOException {

    try (final var formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
      formatter.setRepository(gitRepo);
      formatter.setDiffComparator(RawTextComparator.DEFAULT);
      formatter.setDetectRenames(true);

      final var oldTree = this.getParent(gitRepo, commit);
      final var newTree = prepareTreeParser(gitRepo, commit.getId());
      final var diffs = formatter.scan(oldTree, newTree);

      if (diffs.size() > MAX_FILES_PER_COMMIT) {

        return diffs.stream()
            .map(
                entry ->
                    new FileDiff(
                        entry.getOldPath(),
                        entry.getNewPath(),
                        entry.getChangeType(),
                        0,
                        0,
                        List.of(),
                        true))
            .toList();
      }

      return diffs.stream().map(entry -> parseFileDiff(gitRepo, formatter, entry)).toList();
    }
  }

  private AbstractTreeIterator getParent(final Repository gitRepo, final RevCommit commit)
      throws IOException {

    if (commit.getParentCount() == 0) { // No Commit
      return new EmptyTreeIterator();
    }

    try (final var parentWalk = new RevWalk(gitRepo)) {
      final var commitId = parentWalk.parseCommit(commit.getParent(0).getId()).getId();

      return prepareTreeParser(gitRepo, commitId);
    }
  }

  private CommitDetail getCommitDetail(final Repository gitRepo, final ObjectId objectId)
      throws IOException {

    try (final var walk = new RevWalk(gitRepo)) {

      final var commit = walk.parseCommit(objectId);
      final var diffs = this.getFileDiffs(gitRepo, commit);

      final var totalStats =
          CommitStats.builder()
              .additions(diffs.stream().mapToInt(FileDiff::additions).sum())
              .deletions(diffs.stream().mapToInt(FileDiff::deletions).sum())
              .filesChanged(diffs.size())
              .build();

      final var description = this.extractDescription(commit.getFullMessage());
      final var author = this.resolveAuthor(commit);
      final var parentShas = Arrays.stream(commit.getParents()).map(RevCommit::getName).toList();

      return CommitDetail.builder()
          .sha(commit.getName())
          .shortSha(commit.getName().substring(0, DEFAULT_SHORT_SHA_LENGTH))
          .message(commit.getShortMessage())
          .description(description)
          .author(author)
          .committedAt(commit.getAuthorIdent().getWhenAsInstant())
          .parentShas(parentShas)
          .stats(totalStats)
          .files(diffs)
          .build();
    }
  }

  private CommitInfo toCommitInfo(
      final Repository gitRepo, final RevCommit commit, final Map<String, Tenant> tenantsByEmail) {

    try {
      final var stats = this.computeCommitStatsLightweight(gitRepo, commit);
      final var description = this.extractDescription(commit.getFullMessage());
      final var author = this.resolveAuthor(commit, tenantsByEmail);
      final var parentShas = Arrays.stream(commit.getParents()).map(RevCommit::getName).toList();

      return CommitInfo.builder()
          .sha(commit.getName())
          .shortSha(commit.getName().substring(0, DEFAULT_SHORT_SHA_LENGTH))
          .message(commit.getShortMessage())
          .description(description)
          .author(author)
          .committedAt(commit.getAuthorIdent().getWhenAsInstant())
          .parentShas(parentShas)
          .stats(stats)
          .build();

    } catch (final IOException _) {
      throw new ErrorOccurredException("");
    }
  }

  private @Nullable String extractDescription(final @Nullable String fullMessage) {

    if (fullMessage == null) {
      return null;
    }

    final var lines = fullMessage.strip().split("\n", 2);

    if (lines.length < 2) {
      return null;
    }

    final var desc = lines[1].strip();

    return desc.isEmpty() ? null : desc;
  }

  private AuthorInfo resolveAuthor(final RevCommit commit) {

    final var ident = commit.getAuthorIdent();

    return this.tenantRepository
        .findByUsernameOrEmail(ident.getEmailAddress())
        .map(t -> this.toAuthorInfo(t, ident))
        .orElse(new AuthorInfo(ident.getName(), ident.getEmailAddress(), null, null));
  }

  private AuthorInfo resolveAuthor(
      final RevCommit commit, final Map<String, Tenant> tenantsByEmail) {

    final var ident = commit.getAuthorIdent();
    final var tenant = tenantsByEmail.get(ident.getEmailAddress());

    if (tenant != null) {
      return this.toAuthorInfo(tenant, ident);
    }

    return new AuthorInfo(ident.getName(), ident.getEmailAddress(), null, null);
  }

  private AuthorInfo toAuthorInfo(final Tenant tenant, final PersonIdent ident) {

    return new AuthorInfo(
        ident.getName(), ident.getEmailAddress(), tenant.getUsername(), tenant.getAvatarUrl());
  }

  private <T> PagedResult<T> getEmptyPage(final int page, final int size) {

    return new PagedResult<>(List.of(), page, size, 0, 0, false, false);
  }

  private CommitStats computeCommitStatsLightweight(
      final Repository gitRepo, final RevCommit commit) throws IOException {

    try (final var formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
      formatter.setRepository(gitRepo);
      formatter.setDiffComparator(RawTextComparator.DEFAULT);
      formatter.setDetectRenames(false);

      final var oldTree = this.getParent(gitRepo, commit);
      final var newTree = prepareTreeParser(gitRepo, commit.getId());
      final var diffs = formatter.scan(oldTree, newTree);

      if (diffs.size() > MAX_FILES_PER_COMMIT) {
        return new CommitStats(0, 0, diffs.size());
      }

      int additions = 0;
      int deletions = 0;

      for (final var entry : diffs) {
        final var editList = formatter.toFileHeader(entry).toEditList();
        for (final var edit : editList) {
          additions += edit.getLengthB();
          deletions += edit.getLengthA();
        }
      }

      return new CommitStats(additions, deletions, diffs.size());
    }
  }
}
