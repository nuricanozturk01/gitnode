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
package dev.gitnode.os.pr.services;

import static dev.gitnode.os.commit.api.FileDiffParser.parseFileDiff;
import static dev.gitnode.os.commit.api.FileDiffParser.prepareTreeParser;

import dev.gitnode.os.branch.api.BranchProtectionPort;
import dev.gitnode.os.branch.api.RequiredChecksPort;
import dev.gitnode.os.commit.api.AuthorInfo;
import dev.gitnode.os.commit.api.CommitInfo;
import dev.gitnode.os.commit.api.CommitStats;
import dev.gitnode.os.commit.api.FileDiff;
import dev.gitnode.os.events.pr.PullRequestCreatedEvent;
import dev.gitnode.os.events.pr.PullRequestReopenedEvent;
import dev.gitnode.os.events.pr.PullRequestStatusChangedEvent;
import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.issue.api.RepoMetadataQueryPort;
import dev.gitnode.os.pr.dtos.PrDetail;
import dev.gitnode.os.pr.dtos.PrFilter;
import dev.gitnode.os.pr.dtos.PrForm;
import dev.gitnode.os.pr.dtos.PrInfo;
import dev.gitnode.os.pr.dtos.PrMergeForm;
import dev.gitnode.os.pr.dtos.PrMergeRequirements;
import dev.gitnode.os.pr.dtos.PrMetadata;
import dev.gitnode.os.pr.dtos.PrUpdateForm;
import dev.gitnode.os.pr.entities.PrStatus;
import dev.gitnode.os.pr.entities.PullRequest;
import dev.gitnode.os.pr.mappers.PrMapper;
import dev.gitnode.os.pr.repositories.PrCommentRepository;
import dev.gitnode.os.pr.repositories.PrRepository;
import dev.gitnode.os.repo.api.CollaboratorAccessPort;
import dev.gitnode.os.repo.api.CollaboratorPermission;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.shared.audit.annotations.Audited;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.git.provider.GitProvider;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.eclipse.jgit.lib.RefUpdate;
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
  private static final UUID NO_ID = new UUID(0L, 0L);

  private final PrRepository prRepository;
  private final PrCommentRepository commentRepository;
  private final PrFinder prFinder;
  private final GitProvider gitProvider;
  private final PrMapper prMapper;
  private final ApplicationEventPublisher eventPublisher;
  private final CollaboratorAccessPort collaboratorAccessPort;
  private final PrReviewerService reviewerService;
  private final PrReviewService reviewService;
  private final BranchProtectionPort branchProtection;
  private final RequiredChecksPort requiredChecks;
  private final RepoMetadataQueryPort repoMetadata;
  private final CodeOwnersService codeOwners;

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

    final var repo = this.prFinder.findRepoForUpdate(owner, repoName);
    final var author = this.findTenant(authorId);

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {

      this.checkOpenPrRules(gitRepo, form, repo);

      final var sourceRef =
          Objects.requireNonNull(gitRepo.findRef(Constants.R_HEADS + form.getSourceBranch()));
      final var sourceSha = Objects.requireNonNull(sourceRef.getObjectId()).getName();
      final var nextNumber = this.prRepository.findMaxNumberByRepoId(repo.id()) + 1;

      final var pr = this.prMapper.buildPr(form, repo, author, sourceSha, nextNumber);

      final var saved = this.prRepository.save(pr);
      this.requestCodeOwners(owner, repoName, repo, saved);
      this.eventPublisher.publishEvent(
          new PullRequestCreatedEvent(
              saved.getId(),
              repo.id(),
              saved.getNumber(),
              repo.ownerUsername(),
              repo.name(),
              form.getSourceBranch(),
              form.getTargetBranch(),
              sourceSha,
              authorId));
      return this.toDetail(saved);
    }
  }

  @Transactional
  public PrDetail update(
      final String owner,
      final String repoName,
      final int number,
      final PrUpdateForm form,
      final UUID requesterId) {

    final var repo = this.findRepo(owner, repoName);
    final var pr = this.findPr(repo.id(), number);
    this.assertCanModify(repo, pr, requesterId);

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

  @Audited(
      action = "CLOSE_PR",
      entityType = "PULL_REQUEST",
      detailsSpEL = "'repo=' + #owner + '/' + #repoName + ', number=' + #number")
  @Transactional
  public void close(
      final String owner, final String repoName, final int number, final UUID closedById) {

    final var repo = this.findRepo(owner, repoName);
    final var pr = this.findPr(repo.id(), number);
    this.assertCanModify(repo, pr, closedById);

    if (!pr.getStatus().equals(PrStatus.OPEN.name())) {
      throw new ErrorOccurredException("Pull request is already closed or merged");
    }

    pr.setStatus(PrStatus.CLOSED.name());
    pr.setClosedAt(Instant.now());
    pr.clearAutoMerge();

    this.prRepository.save(pr);

    final Set<UUID> participants =
        new HashSet<>(this.commentRepository.findDistinctCommenterIdsByPrId(pr.getId()));
    participants.add(pr.getAuthorId());
    participants.remove(closedById);

    this.eventPublisher.publishEvent(
        new PullRequestStatusChangedEvent(
            pr.getId(),
            repo.id(),
            pr.getSourceBranch(),
            pr.getTargetBranch(),
            PrStatus.CLOSED.name(),
            pr.getAuthorId(),
            pr.getNumber(),
            owner,
            repoName,
            Set.copyOf(participants),
            closedById));
  }

  @Audited(
      action = "REOPEN_PR",
      entityType = "PULL_REQUEST",
      entityIdSpEL = "#result.id().toString()",
      detailsSpEL = "'repo=' + #owner + '/' + #repoName + ', number=' + #number")
  @Transactional
  public PrDetail reopen(
      final String owner, final String repoName, final int number, final UUID requesterId)
      throws IOException {

    final var repo = this.prFinder.findRepoForUpdate(owner, repoName);
    final var pr = this.prFinder.findPrForUpdate(repo.id(), number);
    this.assertCanModify(repo, pr, requesterId);

    if (!PrStatus.CLOSED.name().equals(pr.getStatus())) {
      throw new BadRequestException("Only closed pull requests can be reopened");
    }
    if (this.prRepository.existsByRepoIdAndSourceBranchAndTargetBranchAndStatus(
        repo.id(), pr.getSourceBranch(), pr.getTargetBranch(), PrStatus.OPEN.name())) {
      throw new BadRequestException("Another open pull request already exists for these branches");
    }
    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {
      final var source = gitRepo.exactRef(Constants.R_HEADS + pr.getSourceBranch());
      if (source == null || gitRepo.exactRef(Constants.R_HEADS + pr.getTargetBranch()) == null) {
        throw new BadRequestException("A branch of this pull request no longer exists");
      }
      pr.setSourceSha(Objects.requireNonNull(source.getObjectId()).getName());
    }
    pr.setStatus(PrStatus.OPEN.name());
    pr.setClosedAt(null);
    final var reopened = this.prRepository.save(pr);
    this.eventPublisher.publishEvent(
        new PullRequestReopenedEvent(
            reopened.getId(),
            repo.id(),
            reopened.getNumber(),
            reopened.getSourceBranch(),
            reopened.getTargetBranch(),
            reopened.getSourceSha(),
            requesterId));
    return this.toDetail(reopened);
  }

  private void assertCanModify(final RepoRef repo, final PullRequest pr, final UUID requesterId) {
    if (requesterId.equals(pr.getAuthorId())
        || requesterId.equals(repo.ownerId())
        || this.collaboratorAccessPort.hasPermission(
            repo.id(), requesterId, CollaboratorPermission.PULL_REQUEST_MERGE)) {
      return;
    }
    throw new AccessNotAllowedException("notAuthorized");
  }

  public PrMergeRequirements mergeRequirements(
      final String owner, final String repoName, final int number, final @Nullable UUID requesterId)
      throws IOException {
    final var repo = this.prFinder.findRepo(owner, repoName);
    final var pr = this.prFinder.findPr(repo.id(), number);
    final var rule = this.branchProtection.findProtection(repo.id(), pr.getTargetBranch());
    if (rule.isEmpty()) {
      return new PrMergeRequirements(
          false, 0, 0, List.of(), List.of(), false, 0, true, false, false, List.of(), null, null);
    }
    final var heads = this.reviewService.branchHeads(owner, repoName, pr);
    final var summary = this.reviewService.summarize(repo, pr, heads);
    final var required = rule.get().requiredApprovals();
    final var states =
        this.requiredChecks.states(repo.id(), heads.source(), rule.get().requiredChecks());
    final var checks =
        states.entrySet().stream()
            .map(entry -> new PrMergeRequirements.Check(entry.getKey(), entry.getValue()))
            .toList();
    final var conversations = rule.get().requireConversationResolution();
    final var unresolved =
        conversations ? (int) this.commentRepository.countUnresolvedConversations(pr.getId()) : 0;
    final var missingOwners = this.missingCodeOwners(owner, repoName, repo, pr, heads, rule.get());
    final var satisfied =
        approvalsSatisfied(required, summary)
            && checks.stream().allMatch(check -> "SUCCESS".equals(check.state()))
            && unresolved == 0
            && missingOwners.isEmpty();
    return new PrMergeRequirements(
        true,
        required,
        summary.current(),
        summary.changesRequestedBy(),
        checks,
        conversations,
        unresolved,
        satisfied,
        repo.ownerId().equals(requesterId),
        rule.get().requireCodeOwnerReview(),
        missingOwners,
        pr.getAutoMergeStrategy(),
        this.autoMergeUsername(pr));
  }

  private void assertOpenAndReady(final PullRequest pr) {
    if (!pr.getStatus().equals(PrStatus.OPEN.name())) {
      throw new ErrorOccurredException("Pull request is not open");
    }
    if (pr.isDraft()) {
      throw new BadRequestException("Draft pull request cannot be merged");
    }
  }

  private List<PrMergeRequirements.CodeOwner> missingCodeOwners(
      final String owner,
      final String repoName,
      final RepoRef repo,
      final PullRequest pr,
      final PrReviewService.BranchHeads heads,
      final BranchProtectionPort.BranchProtectionInfo rule)
      throws IOException {
    if (!rule.requireCodeOwnerReview()) {
      return List.of();
    }
    return this.codeOwners.unmet(owner, repoName, repo, pr, heads).stream()
        .map(req -> new PrMergeRequirements.CodeOwner(req.pattern(), req.owners()))
        .toList();
  }

  private @Nullable String autoMergeUsername(final PullRequest pr) {
    final var enabledBy = pr.getAutoMergeById();
    if (enabledBy == null) {
      return null;
    }
    return this.prFinder.findTenants(Set.of(enabledBy)).values().stream()
        .findFirst()
        .map(TenantRef::username)
        .orElse(null);
  }

  /** Asks the code owners of the changed files to review; never fails the pull request itself. */
  private void requestCodeOwners(
      final String owner, final String repoName, final RepoRef repo, final PullRequest pr) {
    try {
      final var owners = new HashSet<>(this.codeOwners.ownersToRequest(owner, repoName, repo, pr));
      owners.removeAll(pr.getReviewerIds());
      if (owners.isEmpty()) {
        return;
      }
      pr.getReviewerIds().addAll(owners);
      this.prRepository.save(pr);
      this.eventPublisher.publishEvent(
          new dev.gitnode.os.events.pr.PullRequestReviewEvent(
              pr.getId(),
              pr.getNumber(),
              repo.id(),
              pr.getAuthorId(),
              owner,
              repoName,
              "REVIEW_REQUESTED",
              Set.copyOf(owners)));
    } catch (final IOException | RuntimeException ex) {
      log.warn(
          "Could not request code owner reviews for {}/{}: {}", owner, repoName, ex.getMessage());
    }
  }

  private static boolean approvalsSatisfied(
      final int required, final PrReviewService.ApprovalSummary summary) {
    return required == 0
        || (summary.current() >= required && summary.changesRequestedBy().isEmpty());
  }

  private PrReviewService.@Nullable BranchHeads checkProtection(
      final String owner, final String repoName, final RepoRef repo, final PullRequest pr)
      throws IOException {
    final var protection = this.branchProtection.findProtection(repo.id(), pr.getTargetBranch());
    if (protection.isEmpty()) {
      return null;
    }
    final var rule = protection.get();
    final var heads = this.reviewService.branchHeads(owner, repoName, pr);
    this.reviewService.assertApprovals(repo, pr, heads, rule.requiredApprovals());
    final var failing =
        this.requiredChecks.notPassing(repo.id(), heads.source(), rule.requiredChecks());
    if (!failing.isEmpty()) {
      throw new BadRequestException(
          "Required checks have not passed: " + String.join(", ", failing));
    }
    if (rule.requireConversationResolution()) {
      final var unresolved = this.commentRepository.countUnresolvedConversations(pr.getId());
      if (unresolved > 0) {
        throw new BadRequestException(
            "All review conversations must be resolved before merging (" + unresolved + " open)");
      }
    }
    if (rule.requireCodeOwnerReview()) {
      final var missing = this.codeOwners.unmet(owner, repoName, repo, pr, heads);
      if (!missing.isEmpty()) {
        throw new BadRequestException(
            "Code owner review required: "
                + missing.stream()
                    .map(req -> req.pattern() + " (" + String.join(", ", req.owners()) + ")")
                    .collect(Collectors.joining("; ")));
      }
    }
    return heads;
  }

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
      final PrMergeForm form,
      final boolean allowOwnerBypass)
      throws IOException {

    final var repo = this.prFinder.findRepoForUpdate(owner, repoName);
    final var pr = this.prFinder.findPrForUpdate(repo.id(), number);
    final var mergedBy = this.findTenant(mergedById);

    this.assertOpenAndReady(pr);
    this.reviewerService.assertEligibleReviewer(repo, pr);

    // Repository owner may bypass branch protection, like an admin bypass on GitHub. Auto-merge
    // never bypasses: it only merges once the rules pass.
    final var bypass = allowOwnerBypass && mergedById.equals(repo.ownerId());
    final var checkedHeads = bypass ? null : this.checkProtection(owner, repoName, repo, pr);

    final var mergeSha =
        switch (form.getStrategy()) {
          case MERGE_COMMIT -> this.mergeCommit(owner, repoName, pr, form, mergedBy, checkedHeads);
          case SQUASH -> this.squashCommit(owner, repoName, pr, form, mergedBy, checkedHeads);
          case REBASE -> this.rebaseCommit(owner, repoName, pr, mergedBy, checkedHeads);
        };

    pr.setStatus(PrStatus.MERGED.name());
    pr.setMergedById(mergedBy.id());
    pr.setMergeSha(mergeSha);
    pr.setMergedAt(Instant.now());
    pr.clearAutoMerge();

    final var saved = this.prRepository.save(pr);

    final Set<UUID> participants =
        new HashSet<>(this.commentRepository.findDistinctCommenterIdsByPrId(saved.getId()));
    participants.add(saved.getAuthorId());
    participants.remove(mergedById);

    this.eventPublisher.publishEvent(
        new PullRequestStatusChangedEvent(
            saved.getId(),
            repo.id(),
            saved.getSourceBranch(),
            saved.getTargetBranch(),
            PrStatus.MERGED.name(),
            saved.getAuthorId(),
            saved.getNumber(),
            owner,
            repoName,
            Set.copyOf(participants),
            mergedById));
    return this.toDetail(saved);
  }

  public Page<PrInfo> getAll(
      final String owner,
      final String repoName,
      final String status,
      final PrFilter filter,
      final int page,
      final int size) {

    final var repo = this.findRepo(owner, repoName);
    final var pageable = PageRequest.of(page, size);
    final var assignee =
        filter.assignee() == null || filter.assignee().isBlank()
            ? null
            : filter.assignee().strip().toLowerCase(Locale.ROOT);

    final var prs =
        this.prRepository.search(
            repo.id(),
            status,
            filter.labelId() != null,
            filter.labelId() != null ? filter.labelId() : NO_ID,
            filter.milestoneId() != null,
            filter.milestoneId() != null ? filter.milestoneId() : NO_ID,
            assignee != null,
            assignee != null ? this.prFinder.findTenantIdIgnoreCase(assignee).orElse(NO_ID) : NO_ID,
            pageable);

    final Set<UUID> labelIds = new HashSet<>();
    prs.forEach(pr -> labelIds.addAll(pr.getLabelIds()));
    final Map<UUID, PrMetadata.Label> labelsById = new HashMap<>();
    if (!labelIds.isEmpty()) {
      this.repoMetadata
          .findLabels(repo.id(), labelIds)
          .forEach(
              l ->
                  labelsById.put(
                      l.id(), new PrMetadata.Label(l.id(), l.name(), l.color(), l.description())));
    }
    final Map<UUID, PrMetadata.Milestone> milestonesById = new HashMap<>();

    final var accounts =
        this.prFinder.findTenants(
            prs.stream()
                .flatMap(
                    pr ->
                        java.util.stream.Stream.concat(
                            java.util.stream.Stream.of(pr.getAuthorId(), pr.getMergedById())
                                .filter(Objects::nonNull),
                            pr.getAssigneeIds().stream()))
                .collect(java.util.stream.Collectors.toSet()));

    final var commentCounts = this.commentCountsOf(prs.getContent());

    return prs.map(
        pr -> this.toInfo(repo, pr, labelsById, milestonesById, accounts, commentCounts));
  }

  public PrDetail get(final String owner, final String repoName, final int number) {

    final var repo = this.findRepo(owner, repoName);

    final var pr = this.findPr(repo.id(), number);

    return this.toDetail(pr);
  }

  public List<CommitInfo> getPrCommits(final String owner, final String repoName, final int number)
      throws IOException {
    return this.getPrCommits(owner, repoName, number, 0, Integer.MAX_VALUE);
  }

  public List<CommitInfo> getPrCommits(
      final String owner,
      final String repoName,
      final int number,
      final int offset,
      final int limit)
      throws IOException {

    final var repo = this.findRepo(owner, repoName);
    final var pr = this.findPr(repo.id(), number);

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
          .skip(offset)
          .limit(limit)
          .map(this::toCommitInfo)
          .toList();
    }
  }

  public List<FileDiff> getPrDiff(final String owner, final String repoName, final int number)
      throws IOException {

    final var repo = this.findRepo(owner, repoName);
    final var pr = this.findPr(repo.id(), number);

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
      final TenantRef mergedBy,
      final PrReviewService.@Nullable BranchHeads checkedHeads)
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

    return this.doMergeCommit(owner, repoName, pr, mergedBy, commitBuilder, checkedHeads);
  }

  private String squashCommit(
      final String owner,
      final String repoName,
      final PullRequest pr,
      final PrMergeForm form,
      final TenantRef mergedBy,
      final PrReviewService.@Nullable BranchHeads checkedHeads)
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

    return this.doMergeCommit(owner, repoName, pr, mergedBy, commitBuilder, checkedHeads);
  }

  private String rebaseCommit(
      final String owner,
      final String repoName,
      final PullRequest pr,
      final TenantRef mergedBy,
      final PrReviewService.@Nullable BranchHeads checkedHeads)
      throws IOException {

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {

      final var sourceRef =
          Objects.requireNonNull(gitRepo.findRef(Constants.R_HEADS + pr.getSourceBranch()));
      final var targetRef =
          Objects.requireNonNull(gitRepo.findRef(Constants.R_HEADS + pr.getTargetBranch()));
      final var sourceObjectId = sourceRef.getObjectId();
      final var targetObjectId = targetRef.getObjectId();
      this.assertCheckedHeads(checkedHeads, sourceObjectId, targetObjectId);

      final var commitIds = this.collectRebaseCommits(gitRepo, sourceObjectId, targetObjectId);

      final var authorName =
          StringUtils.isNotBlank(mergedBy.displayName())
              ? mergedBy.displayName()
              : mergedBy.username();

      try (final var walk = new RevWalk(gitRepo)) {
        final var lastCommitId =
            this.applyRebaseCommits(gitRepo, walk, commitIds, targetObjectId, authorName, mergedBy);

        final var refUpdate = gitRepo.updateRef(Constants.R_HEADS + pr.getTargetBranch());
        refUpdate.setNewObjectId(lastCommitId);
        refUpdate.setExpectedOldObjectId(targetObjectId);
        this.assertMergeRefUpdated(refUpdate.update(walk));

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
      final TenantRef mergedBy)
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
          new PersonIdent(authorName, mergedBy.email(), Instant.now(), ZoneOffset.UTC);

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
      final TenantRef mergedBy,
      final BiFunction<RevCommit, RevCommit, CommitBuilder> commitBuilderFactory,
      final PrReviewService.@Nullable BranchHeads checkedHeads)
      throws IOException {

    try (final var gitRepo = this.gitProvider.open(owner, repoName);
        final var walk = new RevWalk(gitRepo)) {

      final var sourceRef =
          Objects.requireNonNull(gitRepo.findRef(Constants.R_HEADS + pr.getSourceBranch()));
      final var targetRef =
          Objects.requireNonNull(gitRepo.findRef(Constants.R_HEADS + pr.getTargetBranch()));
      this.assertCheckedHeads(checkedHeads, sourceRef.getObjectId(), targetRef.getObjectId());
      final var sourceCommit = walk.parseCommit(sourceRef.getObjectId());
      final var targetCommit = walk.parseCommit(targetRef.getObjectId());
      final var merger = (RecursiveMerger) MergeStrategy.RECURSIVE.newMerger(gitRepo, true);

      if (!merger.merge(targetCommit, sourceCommit)) {
        throw new ErrorOccurredException("Merge conflict detected");
      }

      final var authorName =
          mergedBy.displayName() != null ? mergedBy.displayName() : mergedBy.username();
      final var ident =
          new PersonIdent(authorName, mergedBy.email(), Instant.now(), ZoneOffset.UTC);

      final var commit = commitBuilderFactory.apply(targetCommit, sourceCommit);
      commit.setTreeId(merger.getResultTreeId());
      commit.setAuthor(ident);
      commit.setCommitter(ident);

      try (final var inserter = gitRepo.newObjectInserter()) {
        final var commitId = inserter.insert(commit);
        inserter.flush();

        final var refUpdate = gitRepo.updateRef(Constants.R_HEADS + pr.getTargetBranch());
        refUpdate.setNewObjectId(commitId);
        refUpdate.setExpectedOldObjectId(targetCommit.getId());
        refUpdate.setRefLogMessage("merge: " + pr.getSourceBranch(), false);
        this.assertMergeRefUpdated(refUpdate.update(walk));

        return commitId.getName();
      }
    }
  }

  private void assertMergeRefUpdated(final RefUpdate.Result result) {
    if (result != RefUpdate.Result.FAST_FORWARD && result != RefUpdate.Result.NO_CHANGE) {
      throw new BadRequestException(
          "Target branch changed or could not be updated. Retry the merge.");
    }
  }

  private void assertCheckedHeads(
      final PrReviewService.@Nullable BranchHeads checked,
      final ObjectId source,
      final ObjectId target) {
    if (checked != null
        && (!checked.source().equals(source.name()) || !checked.target().equals(target.name()))) {
      throw new BadRequestException("Pull request branches changed; retry the merge");
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

  /** Comment counts of the given pull requests in one query. */
  private Map<UUID, Long> commentCountsOf(final List<PullRequest> prs) {
    final Map<UUID, Long> counts = new HashMap<>();
    if (!prs.isEmpty()) {
      this.commentRepository
          .countByPrIds(prs.stream().map(PullRequest::getId).toList())
          .forEach(row -> counts.put((UUID) row[0], (Long) row[1]));
    }
    return counts;
  }

  private PrInfo toInfo(
      final RepoRef repo,
      final PullRequest pr,
      final Map<UUID, PrMetadata.Label> labelsById,
      final Map<UUID, PrMetadata.Milestone> milestonesById,
      final Map<UUID, TenantRef> accounts,
      final Map<UUID, Long> commentCounts) {

    final var commentCount = commentCounts.getOrDefault(pr.getId(), 0L).intValue();
    final var author = this.toAuthorInfo(accounts, pr.getAuthorId());
    final var mergedBy =
        pr.getMergedById() != null ? this.toAuthorInfoOrNull(accounts, pr.getMergedById()) : null;
    final var labels =
        pr.getLabelIds().stream()
            .map(labelsById::get)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(PrMetadata.Label::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    final var assignees =
        pr.getAssigneeIds().stream()
            .map(accounts::get)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(TenantRef::username, String.CASE_INSENSITIVE_ORDER))
            .map(this::toAuthorInfo)
            .toList();
    final var milestoneId = pr.getMilestoneId();
    final var milestone =
        milestoneId == null
            ? null
            : milestonesById.computeIfAbsent(
                milestoneId,
                id ->
                    this.repoMetadata
                        .findMilestone(repo.id(), id)
                        .map(m -> new PrMetadata.Milestone(m.id(), m.title(), m.status()))
                        .orElse(null));

    return this.prMapper
        .toInfo(pr, commentCount, author, mergedBy)
        .withMetadata(labels, assignees, milestone);
  }

  private PrDetail toDetail(final PullRequest pr) {
    final var commentCount = (int) this.commentRepository.countByPrId(pr.getId());

    final var accounts =
        this.prFinder.findTenants(
            Stream.of(pr.getAuthorId(), pr.getMergedById())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
    final var authorInfo = this.toAuthorInfo(accounts, pr.getAuthorId());
    final var mergedBy =
        pr.getMergedById() != null ? this.toAuthorInfoOrNull(accounts, pr.getMergedById()) : null;

    return this.prMapper.toDetail(pr, commentCount, authorInfo, mergedBy);
  }

  private AuthorInfo toAuthorInfo(final TenantRef tenant) {
    return this.prMapper.toAuthorInfo(tenant);
  }

  /** The author view of an account id; a vanished account (race with deletion) shows as "ghost". */
  private AuthorInfo toAuthorInfo(final Map<UUID, TenantRef> accounts, final UUID id) {
    final var tenant = accounts.get(id);
    return tenant == null ? new AuthorInfo("ghost", "", "ghost", "") : this.toAuthorInfo(tenant);
  }

  private @Nullable AuthorInfo toAuthorInfoOrNull(
      final Map<UUID, TenantRef> accounts, final UUID id) {
    final var tenant = accounts.get(id);
    return tenant == null ? null : this.toAuthorInfo(tenant);
  }

  private RepoRef findRepo(final String owner, final String repoName) {
    return this.prFinder.findRepo(owner, repoName);
  }

  private PullRequest findPr(final UUID repoId, final int number) {
    return this.prFinder.findPr(repoId, number);
  }

  private TenantRef findTenant(final UUID id) {
    return this.prFinder.findTenant(id);
  }

  private void checkOpenPrRules(final Repository gitRepo, final PrForm form, final RepoRef repo)
      throws IOException {

    if (gitRepo.findRef(Constants.R_HEADS + form.getSourceBranch()) == null) {
      throw new ItemNotFoundException("Source branch not found: " + form.getSourceBranch());
    }

    if (gitRepo.findRef(Constants.R_HEADS + form.getTargetBranch()) == null) {
      throw new ItemNotFoundException("Target branch not found: " + form.getTargetBranch());
    }

    if (this.prRepository.existsByRepoIdAndSourceBranchAndTargetBranchAndStatus(
        repo.id(), form.getSourceBranch(), form.getTargetBranch(), "OPEN")) {
      throw new BadRequestException("A pull request already exists for this branch pair");
    }
  }
}
