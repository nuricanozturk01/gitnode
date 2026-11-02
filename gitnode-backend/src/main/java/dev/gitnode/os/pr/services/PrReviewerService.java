package dev.gitnode.os.pr.services;

import dev.gitnode.os.pr.entities.PullRequest;
import dev.gitnode.os.pr.repositories.PrRepository;
import dev.gitnode.os.shared.collaborator.dtos.CollaboratorPermission;
import dev.gitnode.os.shared.collaborator.services.CollaboratorAccessPort;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.repo.entities.Repo;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
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
  private final TenantRepository tenants;
  private final CollaboratorAccessPort access;
  private final org.springframework.context.ApplicationEventPublisher events;

  public record Reviewer(String username, String displayName, String avatarUrl, boolean canMerge) {}

  public List<Reviewer> list(final String owner, final String repoName, final int number) {
    final var repo = this.finder.findRepo(owner, repoName);
    return this.toReviewers(repo, this.finder.findPr(repo.getId(), number));
  }

  @Transactional
  public List<Reviewer> replace(
      final String owner,
      final String repoName,
      final int number,
      final UUID requester,
      final List<String> usernames) {
    final var repo = this.finder.findRepo(owner, repoName);
    final var pr = this.finder.findPrForUpdate(repo.getId(), number);
    if (!pr.getAuthor().getId().equals(requester) && !this.canMerge(repo, requester)) {
      throw new AccessNotAllowedException("notAuthorized");
    }
    if (!"OPEN".equals(pr.getStatus())) throw new BadRequestException("Pull request is not open");
    final Set<Tenant> selected = new HashSet<>();
    for (String username : usernames) {
      final var user =
          this.tenants
              .findByUsername(username.trim())
              .orElseThrow(() -> new ItemNotFoundException("Reviewer not found"));
      this.assertReviewerAllowed(repo, user);
      selected.add(user);
    }
    if (selected.stream().noneMatch(user -> this.canMerge(repo, user.getId()))) {
      throw new BadRequestException("At least one reviewer must have merge permission");
    }
    final Set<UUID> previous = new HashSet<>();
    pr.getReviewers().forEach(user -> previous.add(user.getId()));
    pr.getReviewers().clear();
    pr.getReviewers().addAll(selected);
    this.pullRequests.save(pr);
    final Set<UUID> added = new HashSet<>();
    selected.forEach(user -> added.add(user.getId()));
    added.removeAll(previous);
    added.remove(requester);
    if (!added.isEmpty()) {
      this.events.publishEvent(
          new dev.gitnode.os.events.pr.PullRequestReviewEvent(
              pr.getId(),
              pr.getNumber(),
              repo.getId(),
              requester,
              owner,
              repoName,
              "REVIEW_REQUESTED",
              added));
    }
    return this.toReviewers(repo, pr);
  }

  private void assertReviewerAllowed(final Repo repo, final Tenant user) {
    if (!user.isEnabled()
        || (!this.canMerge(repo, user.getId())
            && !this.access.hasPermission(
                repo.getId(), user.getId(), CollaboratorPermission.PULL_REQUEST_REVIEW))) {
      throw new BadRequestException("Reviewer must have review or merge permission");
    }
  }

  public void assertEligibleReviewer(final Repo repo, final PullRequest pr) {
    if (pr.getReviewers().stream()
        .noneMatch(user -> user.isEnabled() && this.canMerge(repo, user.getId()))) {
      throw new BadRequestException("At least one reviewer must have merge permission");
    }
  }

  private boolean canMerge(final Repo repo, final UUID userId) {
    return repo.getOwner().getId().equals(userId)
        || this.access.hasPermission(
            repo.getId(), userId, CollaboratorPermission.PULL_REQUEST_MERGE);
  }

  private List<Reviewer> toReviewers(final Repo repo, final PullRequest pr) {
    return pr.getReviewers().stream()
        .map(
            user ->
                new Reviewer(
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getAvatarUrl(),
                    user.isEnabled() && this.canMerge(repo, user.getId())))
        .sorted(java.util.Comparator.comparing(Reviewer::username, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }
}
