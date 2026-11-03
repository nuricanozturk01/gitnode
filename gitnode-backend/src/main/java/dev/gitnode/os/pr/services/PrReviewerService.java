package dev.gitnode.os.pr.services;

import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.pr.entities.PullRequest;
import dev.gitnode.os.pr.repositories.PrRepository;
import dev.gitnode.os.repo.api.CollaboratorAccessPort;
import dev.gitnode.os.repo.api.CollaboratorPermission;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrReviewerService {
  private final PrFinder finder;
  private final PrRepository pullRequests;
  private final TenantQueryPort tenants;
  private final CollaboratorAccessPort access;
  private final org.springframework.context.ApplicationEventPublisher events;

  public record Reviewer(String username, String displayName, String avatarUrl, boolean canMerge) {}

  public List<Reviewer> list(final String owner, final String repoName, final int number) {
    final var repo = this.finder.findRepo(owner, repoName);
    return this.toReviewers(repo, this.finder.findPr(repo.id(), number));
  }

  @Transactional
  public List<Reviewer> replace(
      final String owner,
      final String repoName,
      final int number,
      final UUID requester,
      final List<String> usernames) {
    final var repo = this.finder.findRepo(owner, repoName);
    final var pr = this.finder.findPrForUpdate(repo.id(), number);
    if (!pr.getAuthorId().equals(requester) && !this.canMerge(repo, requester)) {
      throw new AccessNotAllowedException("notAuthorized");
    }
    if (!"OPEN".equals(pr.getStatus())) throw new BadRequestException("Pull request is not open");
    final Set<TenantRef> selected = new HashSet<>();
    for (String username : usernames) {
      final var user =
          this.tenants
              .findByUsername(username.trim())
              .orElseThrow(() -> new ItemNotFoundException("Reviewer not found"));
      this.assertReviewerAllowed(repo, user);
      selected.add(user);
    }
    if (selected.stream().noneMatch(user -> this.canMerge(repo, user.id()))) {
      throw new BadRequestException("At least one reviewer must have merge permission");
    }
    final Set<UUID> previous = new HashSet<>();
    previous.addAll(pr.getReviewerIds());
    pr.getReviewerIds().clear();
    selected.forEach(user -> pr.getReviewerIds().add(user.id()));
    this.pullRequests.save(pr);
    final Set<UUID> added = new HashSet<>();
    selected.forEach(user -> added.add(user.id()));
    added.removeAll(previous);
    added.remove(requester);
    if (!added.isEmpty()) {
      this.events.publishEvent(
          new dev.gitnode.os.events.pr.PullRequestReviewEvent(
              pr.getId(),
              pr.getNumber(),
              repo.id(),
              requester,
              owner,
              repoName,
              "REVIEW_REQUESTED",
              added));
    }
    return this.toReviewers(repo, pr);
  }

  private void assertReviewerAllowed(final RepoRef repo, final TenantRef user) {
    if (!user.enabled()
        || (!this.canMerge(repo, user.id())
            && !this.access.hasPermission(
                repo.id(), user.id(), CollaboratorPermission.PULL_REQUEST_REVIEW))) {
      throw new BadRequestException("Reviewer must have review or merge permission");
    }
  }

  public void assertEligibleReviewer(final RepoRef repo, final PullRequest pr) {
    if (this.tenants.findAllByIds(pr.getReviewerIds()).values().stream()
        .noneMatch(user -> user.enabled() && this.canMerge(repo, user.id()))) {
      throw new BadRequestException("At least one reviewer must have merge permission");
    }
  }

  private boolean canMerge(final RepoRef repo, final UUID userId) {
    return repo.ownerId().equals(userId)
        || this.access.hasPermission(repo.id(), userId, CollaboratorPermission.PULL_REQUEST_MERGE);
  }

  private List<Reviewer> toReviewers(final RepoRef repo, final PullRequest pr) {
    return this.tenants.findAllByIds(pr.getReviewerIds()).values().stream()
        .map(
            user ->
                new Reviewer(
                    user.username(),
                    user.displayName(),
                    user.avatarUrl(),
                    user.enabled() && this.canMerge(repo, user.id())))
        .sorted(java.util.Comparator.comparing(Reviewer::username, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }
}
