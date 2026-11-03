package dev.gitnode.os.pr.services;

import dev.gitnode.os.pr.entities.PrReview;
import dev.gitnode.os.pr.entities.PullRequest;
import dev.gitnode.os.pr.repositories.PrReviewRepository;
import dev.gitnode.os.repo.api.CollaboratorAccessPort;
import dev.gitnode.os.repo.api.CollaboratorPermission;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.git.provider.GitProvider;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.lib.Constants;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrReviewService {
  private final PrFinder finder;
  private final PrReviewRepository reviews;
  private final CollaboratorAccessPort access;
  private final GitProvider git;
  private final org.springframework.context.ApplicationEventPublisher events;

  public record ReviewInfo(
      UUID id,
      String username,
      String state,
      String body,
      String commitSha,
      String targetSha,
      Instant createdAt) {}

  public record ReviewsInfo(String headSha, String targetSha, List<ReviewInfo> reviews) {}

  public record BranchHeads(String source, String target) {}

  public ReviewsInfo list(final String owner, final String repoName, final int number)
      throws IOException {
    final var repo = this.finder.findRepo(owner, repoName);
    final var pr = this.finder.findPr(repo.id(), number);
    BranchHeads heads;
    try {
      heads = this.branchHeads(owner, repoName, pr);
    } catch (BadRequestException missingBranch) {
      heads = new BranchHeads(null, null);
    }
    final var found = this.reviews.findAllByPrId(pr.getId());
    final var accounts = this.accounts(found);
    return new ReviewsInfo(
        heads.source(),
        heads.target(),
        found.stream().map(review -> this.toInfo(review, accounts)).toList());
  }

  @Transactional
  public ReviewInfo submit(
      final String owner,
      final String repoName,
      final int number,
      final UUID requester,
      final String state,
      final String body,
      final String expectedSha,
      final String expectedTargetSha)
      throws IOException {
    final RepoRef repo = this.finder.findRepo(owner, repoName);
    final PullRequest pr = this.finder.findPrForUpdate(repo.id(), number);
    final var author = this.finder.findTenant(requester);
    this.assertCanReview(repo, pr, author);
    this.validateReview(pr, requester, state, body);
    final var heads = this.branchHeads(owner, repoName, pr);
    if (!heads.source().equals(expectedSha) || !heads.target().equals(expectedTargetSha))
      throw new BadRequestException(
          "Pull request branches changed; reload before submitting a review");
    final var review = new PrReview();
    review.setPullRequest(pr);
    review.setAuthorId(author.id());
    review.setState(state);
    review.setBody(body == null || body.isBlank() ? null : body.trim());
    review.setCommitSha(heads.source());
    review.setTargetSha(heads.target());
    final var saved = this.reviews.save(review);
    if (!pr.getAuthorId().equals(requester)) {
      this.events.publishEvent(
          new dev.gitnode.os.events.pr.PullRequestReviewEvent(
              pr.getId(),
              pr.getNumber(),
              repo.id(),
              requester,
              owner,
              repoName,
              state,
              java.util.Set.of(pr.getAuthorId())));
    }
    return this.toInfo(saved, java.util.Map.of(author.id(), author));
  }

  private void assertCanReview(
      final RepoRef repo,
      final PullRequest pr,
      final dev.gitnode.os.identity.api.TenantQueryPort.TenantRef author) {
    final var requester = author.id();
    if (!author.enabled()
        || (!repo.ownerId().equals(requester)
            && !this.access.hasPermission(
                repo.id(), requester, CollaboratorPermission.PULL_REQUEST_REVIEW)
            && !this.access.hasPermission(
                repo.id(), requester, CollaboratorPermission.PULL_REQUEST_MERGE))) {
      throw new AccessNotAllowedException("notAuthorized");
    }
  }

  private void validateReview(
      final PullRequest pr, final UUID requester, final String state, final String body) {
    if (!"OPEN".equals(pr.getStatus())) throw new BadRequestException("Pull request is not open");
    if ("APPROVED".equals(state) && pr.getAuthorId().equals(requester)) {
      throw new BadRequestException("Pull request author cannot approve their own pull request");
    }
    if (!"APPROVED".equals(state) && (body == null || body.isBlank())) {
      throw new BadRequestException("A review comment is required");
    }
  }

  public BranchHeads branchHeads(final String owner, final String repoName, final PullRequest pr)
      throws IOException {
    try (final var repo = this.git.open(owner, repoName)) {
      final var source = repo.exactRef(Constants.R_HEADS + pr.getSourceBranch());
      final var target = repo.exactRef(Constants.R_HEADS + pr.getTargetBranch());
      if (source == null
          || source.getObjectId() == null
          || target == null
          || target.getObjectId() == null)
        throw new BadRequestException("Pull request branch not found");
      return new BranchHeads(source.getObjectId().name(), target.getObjectId().name());
    }
  }

  public record ApprovalSummary(int current, List<String> changesRequestedBy) {}

  /**
   * Counts approvals of the current heads and lists reviewers whose change request still stands.
   */
  public ApprovalSummary summarize(
      final RepoRef repo, final PullRequest pr, final BranchHeads heads) {
    final var latest = new HashMap<UUID, PrReview>();
    final var found = this.reviews.findAllByPrId(pr.getId());
    for (final var review : found) {
      if (!"COMMENTED".equals(review.getState())) latest.put(review.getAuthorId(), review);
    }
    final var accounts = this.accounts(latest.values());
    final var blockers =
        latest.values().stream()
            .filter(review -> "CHANGES_REQUESTED".equals(review.getState()))
            .filter(review -> accounts.containsKey(review.getAuthorId()))
            .filter(review -> accounts.get(review.getAuthorId()).enabled())
            .filter(review -> this.canApprove(repo, review.getAuthorId()))
            .map(review -> accounts.get(review.getAuthorId()).username())
            .sorted()
            .toList();
    final var count =
        latest.values().stream()
            .filter(review -> this.isCurrentApproval(repo, pr, heads, review, accounts))
            .count();
    return new ApprovalSummary((int) count, blockers);
  }

  public void assertApprovals(
      final RepoRef repo,
      final PullRequest pr,
      final BranchHeads heads,
      final int requiredApprovals) {
    if (requiredApprovals == 0) return;
    final var summary = this.summarize(repo, pr, heads);
    if (!summary.changesRequestedBy().isEmpty()) {
      throw new BadRequestException(
          "Changes requested by " + summary.changesRequestedBy().get(0) + " must be resolved");
    }
    if (summary.current() < requiredApprovals)
      throw new BadRequestException("Not enough current approvals for the protected branch");
  }

  private boolean isCurrentApproval(
      final RepoRef repo,
      final PullRequest pr,
      final BranchHeads heads,
      final PrReview review,
      final java.util.Map<UUID, dev.gitnode.os.identity.api.TenantQueryPort.TenantRef> accounts) {
    final var author = accounts.get(review.getAuthorId());
    return "APPROVED".equals(review.getState())
        && heads.source().equals(review.getCommitSha())
        && heads.target().equals(review.getTargetSha())
        && !review.getAuthorId().equals(pr.getAuthorId())
        && author != null
        && author.enabled()
        && this.canApprove(repo, review.getAuthorId());
  }

  private boolean canApprove(final RepoRef repo, final UUID userId) {
    return repo.ownerId().equals(userId)
        || this.access.hasPermission(repo.id(), userId, CollaboratorPermission.PULL_REQUEST_REVIEW)
        || this.access.hasPermission(repo.id(), userId, CollaboratorPermission.PULL_REQUEST_MERGE);
  }

  private java.util.Map<UUID, dev.gitnode.os.identity.api.TenantQueryPort.TenantRef> accounts(
      final java.util.Collection<PrReview> found) {
    return this.finder.findTenants(
        found.stream().map(PrReview::getAuthorId).collect(java.util.stream.Collectors.toSet()));
  }

  private ReviewInfo toInfo(
      final PrReview review,
      final java.util.Map<UUID, dev.gitnode.os.identity.api.TenantQueryPort.TenantRef> accounts) {
    final var author = accounts.get(review.getAuthorId());
    return new ReviewInfo(
        review.getId(),
        author == null ? "ghost" : author.username(),
        review.getState(),
        review.getBody(),
        review.getCommitSha(),
        review.getTargetSha(),
        review.getCreatedAt());
  }
}
