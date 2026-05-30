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
package com.nuricanozturk.originhub.pr.listeners;

import com.nuricanozturk.originhub.pr.entities.PrStatus;
import com.nuricanozturk.originhub.shared.branch.services.BranchProtocolService;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.pr.events.PullRequestStatusChangedEvent;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrHeadBranchCleanupListener {

  private final @NonNull RepoRepository repoRepository;
  private final @NonNull BranchProtocolService branchProtocolService;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPullRequestStatusChanged(final @NonNull PullRequestStatusChangedEvent event) {

    final var status = event.newStatus();
    final var isMerged = PrStatus.MERGED.name().equals(status);
    final var isClosed = PrStatus.CLOSED.name().equals(status);
    if (!isMerged && !isClosed) {
      return;
    }

    final var repo =
        this.repoRepository
            .findByIdWithOwner(event.repoId())
            .orElseThrow(() -> new ItemNotFoundException("repoNotFound"));

    if (!this.shouldDeleteHeadBranch(isMerged, isClosed, repo)) {
      return;
    }

    final var source = event.sourceBranch();
    final var target = event.targetBranch();

    if (source.equals(target)) {
      log.debug(
          "Skip head branch delete: source equals target ({}) for repo {}", source, event.repoId());
      return;
    }

    final var ownerUsername = repo.getOwner().getUsername();
    final var repoName = repo.getName();

    this.delete(ownerUsername, repoName, source, event.prId(), status);
  }

  private void delete(
      final String ownerUsername,
      final String repoName,
      final String source,
      final @NonNull UUID prId,
      final String status) {

    try {
      this.branchProtocolService.delete(ownerUsername, repoName, source);
      log.debug("Deleted head branch {} on {} after PR {} ({})", source, repoName, prId, status);
    } catch (final ItemNotFoundException e) {
      log.debug(
          "Head branch {} not found on {} after PR {}: {}", source, repoName, prId, e.getMessage());
    } catch (final ErrorOccurredException e) {
      log.debug("Skip head branch delete on {} for {}: {}", repoName, source, e.getMessage());
    } catch (final IOException e) {
      log.warn(
          "Failed to delete head branch {} on {} after PR {}: {}",
          source,
          repoName,
          prId,
          e.getMessage());
    }
  }

  private boolean shouldDeleteHeadBranch(
      final boolean isMerged, final boolean isClosed, final Repo repo) {

    return isMerged && repo.isDeleteHeadBranchOnPrMerge()
        || isClosed && repo.isDeleteHeadBranchOnPrClose();
  }
}
