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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommitNonTxService {

  private static final int MAX_FILES_PER_COMMIT = 50;
  private static final int DEFAULT_SHORT_SHA_LENGTH = 7;

  private final @NonNull GitProvider gitProvider;
  private final @NonNull TenantRepository tenantRepository;

  public @NonNull PagedResult<@NonNull CommitInfo> getCommits(
      final @NonNull String owner,
      final @NonNull String repoName,
      final @NonNull String branch,
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

  public @NonNull CommitDetail getCommit(
      final @NonNull String owner, final @NonNull String repoName, final @NonNull String sha)
      throws IOException {

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {

      final var objectId = gitRepo.resolve(sha);

      if (objectId == null) {
        throw new ItemNotFoundException("commitNotFound: " + sha);
      }

      return this.getCommitDetail(gitRepo, objectId);
    }
  }

  public @NonNull List<@NonNull FileDiff> getCommitDiff(
      final @NonNull String owner, final @NonNull String repoName, final @NonNull String sha)
      throws IOException {

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {

      final var objectId = gitRepo.resolve(sha);

      if (objectId == null) {
        throw new ItemNotFoundException("commitNotFound: " + sha);
      }

      return this.getFileDiffs(gitRepo, objectId);
    }
  }

  private @NonNull PagedResult<@NonNull CommitInfo> getCommits(
      final Repository gitRepo, final Ref branchRef, final int page, final int size)
      throws IOException {

    try (final var walk = new RevWalk(gitRepo)) {
      walk.markStart(walk.parseCommit(branchRef.getObjectId()));
      walk.sort(RevSort.COMMIT_TIME_DESC);

      int toSkip = page * size;
      int collected = 0;
      int totalCount = 0;
      final var pageCommits = new ArrayList<CommitInfo>();

      for (final RevCommit cmt : walk) {
        totalCount++;

        if (toSkip > 0) {
          toSkip--;
          continue;
        }

        if (collected < size) {
          pageCommits.add(this.toCommitInfo(gitRepo, cmt));
          collected++;
        }
      }

      final var totalPages = (int) Math.ceil((double) totalCount / size);
      return new PagedResult<>(
          pageCommits, page, size, totalCount, totalPages, page < totalPages - 1, page > 0);
    }
  }

  private @NonNull List<@NonNull FileDiff> getFileDiffs(
      final @NonNull Repository gitRepo, final @NonNull ObjectId objectId) throws IOException {

    try (final var walk = new RevWalk(gitRepo)) {
      return this.getFileDiffs(gitRepo, walk.parseCommit(objectId));
    }
  }

  private @NonNull List<@NonNull FileDiff> getFileDiffs(
      final @NonNull Repository gitRepo, final @NonNull RevCommit commit) throws IOException {

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

  private @NonNull AbstractTreeIterator getParent(
      final @NonNull Repository gitRepo, final @NonNull RevCommit commit) throws IOException {

    if (commit.getParentCount() == 0) { // No Commit
      return new EmptyTreeIterator();
    }

    try (final var parentWalk = new RevWalk(gitRepo)) {
      final var commitId = parentWalk.parseCommit(commit.getParent(0).getId()).getId();

      return prepareTreeParser(gitRepo, commitId);
    }
  }

  private @NonNull CommitDetail getCommitDetail(
      final @NonNull Repository gitRepo, final @NonNull ObjectId objectId) throws IOException {

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

  private @NonNull CommitInfo toCommitInfo(
      final @NonNull Repository gitRepo, final @NonNull RevCommit commit) {

    try {
      final var stats = this.computeCommitStatsLightweight(gitRepo, commit);
      final var description = this.extractDescription(commit.getFullMessage());
      final var author = this.resolveAuthor(commit);
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

  private @NonNull AuthorInfo resolveAuthor(final @NonNull RevCommit commit) {

    final var ident = commit.getAuthorIdent();

    return this.tenantRepository
        .findByUsernameOrEmail(ident.getEmailAddress())
        .map(t -> this.toAuthorInfo(t, ident))
        .orElse(new AuthorInfo(ident.getName(), ident.getEmailAddress(), null, null));
  }

  private @NonNull AuthorInfo toAuthorInfo(
      final @NonNull Tenant tenant, final @NonNull PersonIdent ident) {

    return new AuthorInfo(
        ident.getName(), ident.getEmailAddress(), tenant.getUsername(), tenant.getAvatarUrl());
  }

  private @NonNull <T> PagedResult<T> getEmptyPage(final int page, final int size) {

    return new PagedResult<>(List.of(), page, size, 0, 0, false, false);
  }

  private @NonNull CommitStats computeCommitStatsLightweight(
      final @NonNull Repository gitRepo, final @NonNull RevCommit commit) throws IOException {

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
