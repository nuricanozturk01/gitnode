package dev.gitnode.os.pr.services;

import dev.gitnode.os.pr.entities.PrReview;
import dev.gitnode.os.pr.entities.PullRequest;
import dev.gitnode.os.pr.repositories.PrReviewRepository;
import dev.gitnode.os.shared.collaborator.dtos.CollaboratorPermission;
import dev.gitnode.os.shared.collaborator.services.CollaboratorAccessPort;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.git.provider.GitProvider;
import dev.gitnode.os.shared.repo.entities.Repo;
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
    final var pr = this.finder.findPr(repo.getId(), number);
    BranchHeads heads;
    try {
      heads = this.branchHeads(owner, repoName, pr);
    } catch (BadRequestException missingBranch) {
      heads = new BranchHeads(null, null);
    }
    return new ReviewsInfo(
        heads.source(),
        heads.target(),
        this.reviews.findAllByPrId(pr.getId()).stream().map(this::toInfo).toList());
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
    final Repo repo = this.finder.findRepo(owner, repoName);
    final PullRequest pr = this.finder.findPrForUpdate(repo.getId(), number);
    final var author = this.finder.findTenant(requester);
    this.assertCanReview(repo, pr, author);
    this.validateReview(pr, requester, state, body);
    final var heads = this.branchHeads(owner, repoName, pr);
    if (!heads.source().equals(expectedSha) || !heads.target().equals(expectedTargetSha))
      throw new BadRequestException(
          "Pull request branches changed; reload before submitting a review");
    final var review = new PrReview();
    review.setPullRequest(pr);
    review.setAuthor(author);
    review.setState(state);
    review.setBody(body == null || body.isBlank() ? null : body.trim());
    review.setCommitSha(heads.source());
    review.setTargetSha(heads.target());
    final var saved = this.reviews.save(review);
    if (!pr.getAuthor().getId().equals(requester)) {
      this.events.publishEvent(
          new dev.gitnode.os.events.pr.PullRequestReviewEvent(
              pr.getId(),
              pr.getNumber(),
              repo.getId(),
              requester,
              owner,
              repoName,
              state,
              java.util.Set.of(pr.getAuthor().getId())));
    }
    return this.toInfo(saved);
  }

  private void assertCanReview(
      final Repo repo,
      final PullRequest pr,
      final dev.gitnode.os.shared.tenant.entities.Tenant author) {
    final var requester = author.getId();
    if (!author.isEnabled()
        || (!repo.getOwner().getId().equals(requester)
            && !this.access.hasPermission(
                repo.getId(), requester, CollaboratorPermission.PULL_REQUEST_REVIEW)
            && !this.access.hasPermission(
                repo.getId(), requester, CollaboratorPermission.PULL_REQUEST_MERGE))) {
      throw new AccessNotAllowedException("notAuthorized");
    }
  }

  private void validateReview(
      final PullRequest pr, final UUID requester, final String state, final String body) {
    if (!"OPEN".equals(pr.getStatus())) throw new BadRequestException("Pull request is not open");
    if ("APPROVED".equals(state) && pr.getAuthor().getId().equals(requester)) {
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
  public ApprovalSummary summarize(final Repo repo, final PullRequest pr, final BranchHeads heads) {
    final var latest = new HashMap<UUID, PrReview>();
    for (final var review : this.reviews.findAllByPrId(pr.getId())) {
      if (!"COMMENTED".equals(review.getState())) latest.put(review.getAuthor().getId(), review);
    }
    final var blockers =
        latest.values().stream()
            .filter(review -> "CHANGES_REQUESTED".equals(review.getState()))
            .filter(review -> review.getAuthor().isEnabled())
            .filter(review -> this.canApprove(repo, review.getAuthor().getId()))
            .map(review -> review.getAuthor().getUsername())
            .sorted()
            .toList();
    final var count =
        latest.values().stream()
            .filter(review -> this.isCurrentApproval(repo, pr, heads, review))
            .count();
    return new ApprovalSummary((int) count, blockers);
  }

  public void assertApprovals(
      final Repo repo, final PullRequest pr, final BranchHeads heads, final int requiredApprovals) {
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
      final Repo repo, final PullRequest pr, final BranchHeads heads, final PrReview review) {
    final var author = review.getAuthor();
    return "APPROVED".equals(review.getState())
        && heads.source().equals(review.getCommitSha())
        && heads.target().equals(review.getTargetSha())
        && !author.getId().equals(pr.getAuthor().getId())
        && author.isEnabled()
        && this.canApprove(repo, author.getId());
  }

  private boolean canApprove(final Repo repo, final UUID userId) {
    return repo.getOwner().getId().equals(userId)
        || this.access.hasPermission(
            repo.getId(), userId, CollaboratorPermission.PULL_REQUEST_REVIEW)
        || this.access.hasPermission(
            repo.getId(), userId, CollaboratorPermission.PULL_REQUEST_MERGE);
  }

  private ReviewInfo toInfo(final PrReview review) {
    return new ReviewInfo(
        review.getId(),
        review.getAuthor().getUsername(),
        review.getState(),
        review.getBody(),
        review.getCommitSha(),
        review.getTargetSha(),
        review.getCreatedAt());
  }
}
