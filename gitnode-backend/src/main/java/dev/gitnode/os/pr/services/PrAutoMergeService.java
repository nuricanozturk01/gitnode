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

import dev.gitnode.os.pr.dtos.MergeStrategy;
import dev.gitnode.os.pr.dtos.PrMergeForm;
import dev.gitnode.os.pr.dtos.PrMergeRequirements;
import dev.gitnode.os.pr.entities.PrStatus;
import dev.gitnode.os.pr.entities.PullRequest;
import dev.gitnode.os.pr.repositories.PrRepository;
import dev.gitnode.os.repo.api.CollaboratorAccessPort;
import dev.gitnode.os.repo.api.CollaboratorPermission;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.shared.audit.annotations.Audited;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auto-merge: a pull request that cannot merge yet is marked to merge by itself as soon as its
 * target branch rules pass. The merge runs as the account that enabled it and, unlike a manual
 * merge by the repository owner, never bypasses branch protection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class PrAutoMergeService {

  private final PrFinder finder;
  private final PrRepository pullRequests;
  private final PrReviewerService reviewers;
  private final PullRequestService prService;
  private final CollaboratorAccessPort access;
  private final RepoLookupPort repos;

  @Audited(
      action = "ENABLE_AUTO_MERGE",
      entityType = "PULL_REQUEST",
      detailsSpEL =
          "'repo=' + #owner + '/' + #repoName + ', number=' + #number + ', strategy=' + #strategy")
  @Transactional
  public PrMergeRequirements enable(
      final String owner,
      final String repoName,
      final int number,
      final UUID requesterId,
      final MergeStrategy strategy)
      throws IOException {
    final var repo = this.finder.findRepo(owner, repoName);
    final var pr = this.finder.findPrForUpdate(repo.id(), number);
    if (!PrStatus.OPEN.name().equals(pr.getStatus())) {
      throw new BadRequestException("Pull request is not open");
    }
    if (pr.isDraft()) {
      throw new BadRequestException("Draft pull request cannot be merged");
    }
    this.reviewers.assertEligibleReviewer(repo, pr);
    final var current = this.prService.mergeRequirements(owner, repoName, number, requesterId);
    if (!current.protectedBranch()) {
      throw new BadRequestException(
          "Auto-merge needs a protected target branch; merge the pull request directly");
    }
    if (current.satisfied()) {
      throw new BadRequestException("Pull request is already mergeable; merge it directly");
    }
    pr.setAutoMergeStrategy(strategy.name());
    pr.setAutoMergeById(requesterId);
    this.pullRequests.save(pr);
    return this.prService.mergeRequirements(owner, repoName, number, requesterId);
  }

  @Audited(
      action = "DISABLE_AUTO_MERGE",
      entityType = "PULL_REQUEST",
      detailsSpEL = "'repo=' + #owner + '/' + #repoName + ', number=' + #number")
  @Transactional
  public PrMergeRequirements disable(
      final String owner, final String repoName, final int number, final UUID requesterId)
      throws IOException {
    final var repo = this.finder.findRepo(owner, repoName);
    final var pr = this.finder.findPrForUpdate(repo.id(), number);
    pr.clearAutoMerge();
    this.pullRequests.save(pr);
    return this.prService.mergeRequirements(owner, repoName, number, requesterId);
  }

  /**
   * Merges the pull request if auto-merge is on and its rules now pass. Not transactional on
   * purpose: a failed merge rolls back only its own transaction, and the follow-up that switches
   * auto-merge off still commits.
   */
  public void attempt(final UUID prId) {
    final var pr = this.pullRequests.findById(prId).orElse(null);
    if (pr == null || !isWaiting(pr)) {
      return;
    }
    final var repo = this.repos.findById(pr.getRepoId()).orElse(null);
    if (repo == null || !this.actorMayMerge(repo, pr)) {
      this.pullRequests.clearAutoMerge(prId);
      return;
    }
    try {
      this.mergeIfReady(repo, pr, Objects.requireNonNull(pr.getAutoMergeById()));
    } catch (final ErrorOccurredException ex) {
      // Conflicts and closed pull requests will not fix themselves; stop retrying.
      this.pullRequests.clearAutoMerge(prId);
      log.info(
          "Auto-merge of {}/{}#{} switched off: {}",
          repo.ownerUsername(),
          repo.name(),
          pr.getNumber(),
          ex.getMessage());
    } catch (final IOException | RuntimeException ex) {
      log.debug(
          "Auto-merge of {}/{}#{} will retry: {}",
          repo.ownerUsername(),
          repo.name(),
          pr.getNumber(),
          ex.getMessage());
    }
  }

  private void mergeIfReady(final RepoRef repo, final PullRequest pr, final UUID actor)
      throws IOException {
    final var requirements =
        this.prService.mergeRequirements(repo.ownerUsername(), repo.name(), pr.getNumber(), null);
    if (!requirements.satisfied()) {
      return;
    }
    final var form = new PrMergeForm(MergeStrategy.valueOf(pr.getAutoMergeStrategy()), null);
    this.prService.merge(repo.ownerUsername(), repo.name(), pr.getNumber(), actor, form, false);
    log.info("Auto-merged {}/{}#{}", repo.ownerUsername(), repo.name(), pr.getNumber());
  }

  private static boolean isWaiting(final PullRequest pr) {
    return pr.getAutoMergeStrategy() != null
        && PrStatus.OPEN.name().equals(pr.getStatus())
        && !pr.isDraft();
  }

  private boolean actorMayMerge(final RepoRef repo, final PullRequest pr) {
    final var actor = pr.getAutoMergeById();
    return actor != null && this.mayMerge(repo, actor);
  }

  private boolean mayMerge(final RepoRef repo, final UUID userId) {
    final var account = this.finder.findTenants(Set.of(userId)).get(userId);
    return account != null
        && account.enabled()
        && (repo.ownerId().equals(userId)
            || this.access.hasPermission(
                repo.id(), userId, CollaboratorPermission.PULL_REQUEST_MERGE));
  }
}
