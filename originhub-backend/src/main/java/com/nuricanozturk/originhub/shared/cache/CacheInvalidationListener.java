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
package com.nuricanozturk.originhub.shared.cache;

import com.nuricanozturk.originhub.shared.branch.events.BranchDeletedEvent;
import com.nuricanozturk.originhub.shared.pr.events.PullRequestStatusChangedEvent;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class CacheInvalidationListener {

  private final RepoCacheInvalidator invalidator;
  private final RepoRepository repoRepository;

  @EventListener
  void onPrMerged(final PullRequestStatusChangedEvent event) {
    if (!"MERGED".equals(event.newStatus())) {
      return;
    }
    this.repoRepository
        .findByIdWithOwner(event.repoId())
        .ifPresent(
            repo -> {
              final var owner = repo.getOwner().getUsername();
              final var repoName = repo.getName();
              log.debug(
                  "Cache eviction on PR merge: {}/{} target={}",
                  owner,
                  repoName,
                  event.targetBranch());
              this.invalidator.evictBranchScoped(owner, repoName, event.targetBranch());
              this.invalidator.evictRepoScoped(owner, repoName);
            });
  }

  @EventListener
  @Async
  void onBranchDeleted(final BranchDeletedEvent event) {
    this.repoRepository
        .findByIdWithOwner(event.repoId())
        .ifPresent(
            repo -> {
              final var owner = repo.getOwner().getUsername();
              final var repoName = repo.getName();
              log.debug(
                  "Cache eviction on branch delete: {}/{} branch={}",
                  owner,
                  repoName,
                  event.branchName());
              this.invalidator.evictBranchScoped(owner, repoName, event.branchName());
            });
  }
}
