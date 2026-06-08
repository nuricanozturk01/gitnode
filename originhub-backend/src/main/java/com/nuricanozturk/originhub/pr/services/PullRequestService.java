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
package com.nuricanozturk.originhub.pr.services;

import static com.nuricanozturk.originhub.shared.util.FileDiffParser.parseFileDiff;
import static com.nuricanozturk.originhub.shared.util.FileDiffParser.prepareTreeParser;

import com.nuricanozturk.originhub.events.pr.PullRequestCreatedEvent;
import com.nuricanozturk.originhub.events.pr.PullRequestStatusChangedEvent;
import com.nuricanozturk.originhub.pr.dtos.PrDetail;
import com.nuricanozturk.originhub.pr.dtos.PrForm;
import com.nuricanozturk.originhub.pr.dtos.PrInfo;
import com.nuricanozturk.originhub.pr.dtos.PrMergeForm;
import com.nuricanozturk.originhub.pr.dtos.PrUpdateForm;
import com.nuricanozturk.originhub.pr.entities.PrStatus;
import com.nuricanozturk.originhub.pr.entities.PullRequest;
import com.nuricanozturk.originhub.pr.mappers.PrMapper;
import com.nuricanozturk.originhub.pr.repositories.PrCommentRepository;
import com.nuricanozturk.originhub.pr.repositories.PrRepository;
import com.nuricanozturk.originhub.shared.audit.annotations.Audited;
import com.nuricanozturk.originhub.shared.cache.CacheNames;
import com.nuricanozturk.originhub.shared.commit.dtos.AuthorInfo;
import com.nuricanozturk.originhub.shared.commit.dtos.CommitInfo;
import com.nuricanozturk.originhub.shared.commit.dtos.CommitStats;
import com.nuricanozturk.originhub.shared.commit.dtos.FileDiff;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.git.provider.GitProvider;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.RecursiveMerger;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
@NullMarked
public class PullRequestService {

  private static final int DEFAULT_SHORT_SHA_LENGTH = 7;

  private final PrRepository prRepository;
  private final PrCommentRepository commentRepository;
  private final PrFinder prFinder;
  private final GitProvider gitProvider;
  private final PrMapper prMapper;
  private final ApplicationEventPublisher eventPublisher;

  @CacheEvict(cacheNames = CacheNames.REPO_PR_OPEN_COUNT, key = "#owner + ':' + #repoName")
  @Audited(
      action = "CREATE_PR",
      entityType = "PULL_REQUEST",
      entityIdSpEL = "#result.id().toString()",
      detailsSpEL =
          "'repo=' + #owner + '/' + #repoName + ', title=' + #form.title"
              + " + ', ' + #form.sourceBranch + ' -> ' + #form.targetBranch")
  @Transactional
  public PrDetail create(
      final String owner, final String repoName, final UUID authorId, final PrForm form)
      throws IOException {

    final var repo = this.findRepo(owner, repoName);
    final var author = this.findTenant(authorId);

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {

      this.checkOpenPrRules(gitRepo, form, repo);

      final var sourceRef =
          Objects.requireNonNull(gitRepo.findRef(Constants.R_HEADS + form.getSourceBranch()));
      final var sourceSha = Objects.requireNonNull(sourceRef.getObjectId()).getName();
      final var nextNumber = this.prRepository.findMaxNumberByRepoId(repo.getId()) + 1;

      final var pr = this.prMapper.buildPr(form, repo, author, sourceSha, nextNumber);

      final var saved = this.prRepository.save(pr);
      this.eventPublisher.publishEvent(
          new PullRequestCreatedEvent(saved.getId(), repo.getId(), form.getSourceBranch()));
      return this.toDetail(saved);
    }
  }

  @Transactional
  public PrDetail update(
      final String owner, final String repoName, final int number, final PrUpdateForm form) {

    final var repo = this.findRepo(owner, repoName);
    final var pr = this.findPr(repo.getId(), number);

    if (!pr.getStatus().equals(PrStatus.OPEN.name())) {
      throw new ErrorOccurredException("Cannot update a closed or merged pull request");
    }

    if (form.getTitle() != null) {
      pr.setTitle(form.getTitle());
    }

    if (form.getDescription() != null) {
      pr.setDescription(form.getDescription());
    }

    if (form.getIsDraft() != null) {
      pr.setDraft(form.getIsDraft());
    }

    return this.toDetail(this.prRepository.save(pr));
  }

  @CacheEvict(cacheNames = CacheNames.REPO_PR_OPEN_COUNT, key = "#owner + ':' + #repoName")
  @Audited(
      action = "CLOSE_PR",
      entityType = "PULL_REQUEST",
      detailsSpEL = "'repo=' + #owner + '/' + #repoName + ', number=' + #number")
  @Transactional
  public void close(final String owner, final String repoName, final int number) {

    final var repo = this.findRepo(owner, repoName);
    final var pr = this.findPr(repo.getId(), number);

    if (!pr.getStatus().equals(PrStatus.OPEN.name())) {
      throw new ErrorOccurredException("Pull request is already closed or merged");
    }

    pr.setStatus(PrStatus.CLOSED.name());
    pr.setClosedAt(Instant.now());

    this.prRepository.save(pr);
    this.eventPublisher.publishEvent(
        new PullRequestStatusChangedEvent(
            pr.getId(),
            repo.getId(),
            pr.getSourceBranch(),
            pr.getTargetBranch(),
            PrStatus.CLOSED.name()));
  }

  @CacheEvict(cacheNames = CacheNames.REPO_PR_OPEN_COUNT, key = "#owner + ':' + #repoName")
  @Audited(
      action = "MERGE_PR",
      entityType = "PULL_REQUEST",
      entityIdSpEL = "#result.id().toString()",
      detailsSpEL = "'repo=' + #owner + '/' + #repoName + ', number=' + #number")
  @Transactional
  public PrDetail merge(
      final String owner,
      final String repoName,
      final int number,
      final UUID mergedById,
      final PrMergeForm form)
      throws IOException {

    final var repo = this.findRepo(owner, repoName);
    final var pr = this.findPr(repo.getId(), number);
    final var mergedBy = this.findTenant(mergedById);

    if (!pr.getStatus().equals(PrStatus.OPEN.name())) {
      throw new ErrorOccurredException("Pull request is not open");
    }

    final var mergeSha =
        switch (form.getStrategy()) {
          case MERGE_COMMIT -> this.mergeCommit(owner, repoName, pr, form, mergedBy);
          case SQUASH -> this.squashCommit(owner, repoName, pr, form, mergedBy);
          case REBASE -> this.rebaseCommit(owner, repoName, pr, mergedBy);
        };

    pr.setStatus(PrStatus.MERGED.name());
    pr.setMergedBy(mergedBy);
    pr.setMergeSha(mergeSha);
    pr.setMergedAt(Instant.now());

    final var saved = this.prRepository.save(pr);
    this.eventPublisher.publishEvent(
        new PullRequestStatusChangedEvent(
            saved.getId(),
            repo.getId(),
            saved.getSourceBranch(),
            saved.getTargetBranch(),
            PrStatus.MERGED.name()));
    return this.toDetail(saved);
  }

  public Page<PrInfo> getAll(
      final String owner,
      final String repoName,
      final String status,
      final int page,
      final int size) {

    final var repo = this.findRepo(owner, repoName);
    final var pageable = PageRequest.of(page, size);

    return this.prRepository
        .findAllByRepoIdAndStatusOrderByCreatedAtDesc(repo.getId(), status, pageable)
        .map(this::toInfo);
  }

  public PrDetail get(final String owner, final String repoName, final int number) {

    final var repo = this.findRepo(owner, repoName);

    final var pr = this.findPr(repo.getId(), number);

    return this.toDetail(pr);
  }

  public List<CommitInfo> getPrCommits(final String owner, final String repoName, final int number)
      throws IOException {

    final var repo = this.findRepo(owner, repoName);
    final var pr = this.findPr(repo.getId(), number);

    try (final var gitRepo = this.gitProvider.open(owner, repoName);
        final var walk = new RevWalk(gitRepo)) {
      final var sourceRef = gitRepo.findRef(Constants.R_HEADS + pr.getSourceBranch());
      final var targetRef = gitRepo.findRef(Constants.R_HEADS + pr.getTargetBranch());

      if (sourceRef == null || targetRef == null) {
        return List.of();
      }

      walk.markStart(walk.parseCommit(sourceRef.getObjectId()));
      walk.markUninteresting(walk.parseCommit(targetRef.getObjectId()));
      walk.sort(RevSort.COMMIT_TIME_DESC);

      return StreamSupport.stream(
              Spliterators.spliteratorUnknownSize(walk.iterator(), Spliterator.ORDERED), false)
          .map(this::toCommitInfo)
          .toList();
    }
  }

  public List<FileDiff> getPrDiff(final String owner, final String repoName, final int number)
      throws IOException {

    final var repo = this.findRepo(owner, repoName);
    final var pr = this.findPr(repo.getId(), number);

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {
      try (final var walk = new RevWalk(gitRepo)) {

        final var sourceRef = gitRepo.findRef(Constants.R_HEADS + pr.getSourceBranch());
        final var targetRef = gitRepo.findRef(Constants.R_HEADS + pr.getTargetBranch());

        if (sourceRef == null || targetRef == null) {
          return List.of();
        }

        final var sourceCommit = walk.parseCommit(sourceRef.getObjectId());
        final var targetCommit = walk.parseCommit(targetRef.getObjectId());
        final var mergeBase = this.findMergeBase(gitRepo, sourceCommit, targetCommit);

        return this.getDiffBetween(gitRepo, mergeBase, sourceCommit);
      }
    }
  }

  private String mergeCommit(
      final String owner,
      final String repoName,
      final PullRequest pr,
      final PrMergeForm form,
      final Tenant mergedBy)
      throws IOException {

    final var msg =
        form.getCommitMessage() != null
            ? form.getCommitMessage()
            : "Merge pull request #" + pr.getNumber() + " from " + pr.getSourceBranch();

    final BiFunction<RevCommit, RevCommit, CommitBuilder> commitBuilder =
        (target, source) -> {
          final var commit = new CommitBuilder();
          commit.setParentIds(target.getId(), source.getId());
          commit.setMessage(msg);
          return commit;
        };

    return this.doMergeCommit(owner, repoName, pr, mergedBy, commitBuilder);
  }

  private String squashCommit(
      final String owner,
      final String repoName,
      final PullRequest pr,
      final PrMergeForm form,
      final Tenant mergedBy)
      throws IOException {

    final var msg =
        form.getCommitMessage() != null
            ? form.getCommitMessage()
            : pr.getTitle() + " (#" + pr.getNumber() + ")";

    final BiFunction<RevCommit, RevCommit, CommitBuilder> commitBuilder =
        (target, _) -> {
          final var commit = new CommitBuilder();
          commit.setParentId(target.getId());
          commit.setMessage(msg);
          return commit;
        };

    return this.doMergeCommit(owner, repoName, pr, mergedBy, commitBuilder);
  }

  private String rebaseCommit(
      final String owner, final String repoName, final PullRequest pr, final Tenant mergedBy)
      throws IOException {

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {

      final var sourceRef =
          Objects.requireNonNull(gitRepo.findRef(Constants.R_HEADS + pr.getSourceBranch()));
      final var targetRef =
          Objects.requireNonNull(gitRepo.findRef(Constants.R_HEADS + pr.getTargetBranch()));
      final var sourceObjectId = sourceRef.getObjectId();
      final var targetObjectId = targetRef.getObjectId();

      final var commitIds = this.collectRebaseCommits(gitRepo, sourceObjectId, targetObjectId);

      final var authorName =
          StringUtils.isNotBlank(mergedBy.getDisplayName())
              ? mergedBy.getDisplayName()
              : mergedBy.getUsername();

      try (final var walk = new RevWalk(gitRepo)) {
        final var lastCommitId =
            this.applyRebaseCommits(gitRepo, walk, commitIds, targetObjectId, authorName, mergedBy);

        final var refUpdate = gitRepo.updateRef(Constants.R_HEADS + pr.getTargetBranch());
        refUpdate.setNewObjectId(lastCommitId);
        refUpdate.update(walk);

        return lastCommitId.getName();
      }
    }
  }

  private List<ObjectId> collectRebaseCommits(
      final Repository gitRepo, final ObjectId sourceObjectId, final ObjectId targetObjectId)
      throws IOException {

    try (final var walk = new RevWalk(gitRepo)) {
      final var sourceCommit = walk.parseCommit(sourceObjectId);
      final var targetCommit = walk.parseCommit(targetObjectId);
      final var mergeBase = this.findMergeBase(gitRepo, sourceCommit, targetCommit);

      walk.markStart(sourceCommit);
      walk.markUninteresting(targetCommit);

      if (mergeBase != null) {
        walk.markUninteresting(walk.parseCommit(mergeBase.getId()));
      }

      walk.sort(RevSort.TOPO);
      walk.sort(RevSort.REVERSE, true);

      return StreamSupport.stream(
              Spliterators.spliteratorUnknownSize(walk.iterator(), Spliterator.ORDERED), false)
          .map(RevCommit::getId)
          .map(ObjectId::copy)
          .toList();
    }
  }

  private ObjectId applyRebaseCommits(
      final Repository gitRepo,
      final RevWalk walk,
      final List<ObjectId> commitIds,
      final ObjectId targetObjectId,
      final String authorName,
      final Tenant mergedBy)
      throws IOException {

    var currentParent = walk.parseCommit(targetObjectId);
    var lastCommitId = targetObjectId;

    for (final var commitId : commitIds) {
      final var parsedCommit = walk.parseCommit(commitId);
      final var cherryPickMerger = (ResolveMerger) MergeStrategy.RESOLVE.newMerger(gitRepo, true);
      cherryPickMerger.setBase(parsedCommit.getParent(0));

      if (!cherryPickMerger.merge(currentParent, parsedCommit)) {
        log.warn(
            "Rebase conflict - sha: {}, message: '{}'",
            parsedCommit.getName(),
            parsedCommit.getShortMessage());
        throw new ErrorOccurredException("Rebase conflict at: " + parsedCommit.getShortMessage());
      }

      final var committerIdent =
          new PersonIdent(authorName, mergedBy.getEmail(), Instant.now(), ZoneOffset.UTC);

      final var rebasedCommit = new CommitBuilder();
      rebasedCommit.setTreeId(cherryPickMerger.getResultTreeId());
      rebasedCommit.setParentId(currentParent.getId());
      rebasedCommit.setAuthor(parsedCommit.getAuthorIdent());
      rebasedCommit.setCommitter(committerIdent);
      rebasedCommit.setMessage(parsedCommit.getFullMessage());

      try (final var inserter = gitRepo.newObjectInserter()) {
        lastCommitId = inserter.insert(rebasedCommit);
        inserter.flush();
      }

      currentParent = walk.parseCommit(lastCommitId);
    }

    return lastCommitId;
  }

  private String doMergeCommit(
      final String owner,
      final String repoName,
      final PullRequest pr,
      final Tenant mergedBy,
      final BiFunction<RevCommit, RevCommit, CommitBuilder> commitBuilderFactory)
      throws IOException {

    try (final var gitRepo = this.gitProvider.open(owner, repoName);
        final var walk = new RevWalk(gitRepo)) {

      final var sourceRef =
          Objects.requireNonNull(gitRepo.findRef(Constants.R_HEADS + pr.getSourceBranch()));
      final var targetRef =
          Objects.requireNonNull(gitRepo.findRef(Constants.R_HEADS + pr.getTargetBranch()));
      final var sourceCommit = walk.parseCommit(sourceRef.getObjectId());
      final var targetCommit = walk.parseCommit(targetRef.getObjectId());
      final var merger = (RecursiveMerger) MergeStrategy.RECURSIVE.newMerger(gitRepo, true);

      if (!merger.merge(targetCommit, sourceCommit)) {
        throw new ErrorOccurredException("Merge conflict detected");
      }

      final var authorName =
          mergedBy.getDisplayName() != null ? mergedBy.getDisplayName() : mergedBy.getUsername();
      final var ident =
          new PersonIdent(authorName, mergedBy.getEmail(), Instant.now(), ZoneOffset.UTC);

      final var commit = commitBuilderFactory.apply(targetCommit, sourceCommit);
      commit.setTreeId(merger.getResultTreeId());
      commit.setAuthor(ident);
      commit.setCommitter(ident);

      try (final var inserter = gitRepo.newObjectInserter()) {
        final var commitId = inserter.insert(commit);
        inserter.flush();

        final var refUpdate = gitRepo.updateRef(Constants.R_HEADS + pr.getTargetBranch());
        refUpdate.setNewObjectId(commitId);
        refUpdate.setRefLogMessage("merge: " + pr.getSourceBranch(), false);
        refUpdate.update(walk);

        return commitId.getName();
      }
    }
  }

  private @Nullable RevCommit findMergeBase(
      final Repository gitRepo, final RevCommit a, final RevCommit b) throws IOException {

    try (final var walk = new RevWalk(gitRepo)) {
      walk.setRevFilter(RevFilter.MERGE_BASE);
      walk.markStart(walk.parseCommit(a.getId()));
      walk.markStart(walk.parseCommit(b.getId()));
      return walk.next();
    }
  }

  private List<FileDiff> getDiffBetween(
      final Repository gitRepo, final @Nullable RevCommit base, final RevCommit head)
      throws IOException {

    try (final var formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
      formatter.setRepository(gitRepo);
      formatter.setDiffComparator(RawTextComparator.DEFAULT);
      formatter.setDetectRenames(true);

      final AbstractTreeIterator oldTree;

      if (base == null) {
        oldTree = new EmptyTreeIterator();
      } else {
        oldTree = prepareTreeParser(gitRepo, base.getId());
      }

      final var newTree = prepareTreeParser(gitRepo, head.getId());
      final var diffs = formatter.scan(oldTree, newTree);

      return diffs.stream().map(entry -> parseFileDiff(gitRepo, formatter, entry)).toList();
    }
  }

  private CommitInfo toCommitInfo(final RevCommit commit) {

    final var shortSha = commit.getName().substring(0, DEFAULT_SHORT_SHA_LENGTH);
    final var author =
        new AuthorInfo(
            commit.getAuthorIdent().getName(),
            commit.getAuthorIdent().getEmailAddress(),
            null,
            null);

    final var commitStats = new CommitStats(0, 0, 0);
    final var parentShas = Arrays.stream(commit.getParents()).map(RevCommit::getName).toList();

    return new CommitInfo(
        commit.getName(),
        shortSha,
        commit.getShortMessage(),
        null,
        author,
        commit.getAuthorIdent().getWhenAsInstant(),
        parentShas,
        commitStats);
  }

  private PrInfo toInfo(final PullRequest pr) {

    final var commentCount = (int) this.commentRepository.countByPrId(pr.getId());
    final var author = this.toAuthorInfo(pr.getAuthor());
    final var mergedBy = pr.getMergedBy() != null ? this.toAuthorInfo(pr.getMergedBy()) : null;

    return this.prMapper.toInfo(pr, commentCount, author, mergedBy);
  }

  private PrDetail toDetail(final PullRequest pr) {
    final var commentCount = (int) this.commentRepository.countByPrId(pr.getId());

    final var authorInfo = this.toAuthorInfo(pr.getAuthor());
    final var mergedBy = pr.getMergedBy() != null ? this.toAuthorInfo(pr.getMergedBy()) : null;

    return this.prMapper.toDetail(pr, commentCount, authorInfo, mergedBy);
  }

  private AuthorInfo toAuthorInfo(final Tenant tenant) {
    return this.prMapper.toAuthorInfo(tenant);
  }

  private Repo findRepo(final String owner, final String repoName) {
    return this.prFinder.findRepo(owner, repoName);
  }

  private PullRequest findPr(final UUID repoId, final int number) {
    return this.prFinder.findPr(repoId, number);
  }

  private Tenant findTenant(final UUID id) {
    return this.prFinder.findTenant(id);
  }

  private void checkOpenPrRules(final Repository gitRepo, final PrForm form, final Repo repo)
      throws IOException {

    if (gitRepo.findRef(Constants.R_HEADS + form.getSourceBranch()) == null) {
      throw new ItemNotFoundException("Source branch not found: " + form.getSourceBranch());
    }

    if (gitRepo.findRef(Constants.R_HEADS + form.getTargetBranch()) == null) {
      throw new ItemNotFoundException("Target branch not found: " + form.getTargetBranch());
    }

    if (this.prRepository.existsByRepoIdAndSourceBranchAndTargetBranchAndStatus(
        repo.getId(), form.getSourceBranch(), form.getTargetBranch(), "OPEN")) {
      throw new ErrorOccurredException("A pull request already exists for this branch pair");
    }
  }
}
