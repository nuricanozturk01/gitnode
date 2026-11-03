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

import dev.gitnode.os.pr.repositories.PrRepository;
import dev.gitnode.os.shared.lock.DistributedLockService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically retries every pull request that has auto-merge switched on. */
@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class PrAutoMergeScheduler {

  private static final String LOCK_KEY = "lock:pr:auto-merge";
  private static final Duration LOCK_TTL = Duration.ofMinutes(2);

  private final PrRepository pullRequests;
  private final PrAutoMergeService autoMerge;
  private final DistributedLockService lockService;

  @Scheduled(fixedDelayString = "${gitnode.pr.auto-merge.interval:PT15S}")
  public void run() {
    final var owner = this.lockService.generateOwner();
    if (!this.lockService.tryLock(LOCK_KEY, owner, LOCK_TTL)) {
      return;
    }
    try {
      for (final var id : this.pullRequests.findAutoMergeCandidateIds()) {
        try {
          this.autoMerge.attempt(id);
        } catch (final RuntimeException ex) {
          log.warn("Auto-merge attempt for pull request {} failed: {}", id, ex.getMessage());
        }
      }
    } finally {
      this.lockService.unlock(LOCK_KEY, owner);
    }
  }
}
