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
package dev.gitnode.os.ai.listeners;

import dev.gitnode.os.ai.services.AiCodeReviewService;
import dev.gitnode.os.events.pr.PullRequestCreatedEvent;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class AiCodeReviewListener {

  private final AiCodeReviewService reviewService;
  private final RepoRepository repoRepository;

  @ApplicationModuleListener
  public void onPullRequestCreated(final PullRequestCreatedEvent event) {
    final var repo = this.repoRepository.findById(event.repoId()).orElse(null);

    if (repo == null || !repo.isAiPrReviewEnabled()) {
      log.debug(
          "AI code review skipped for PR #{} in repo {} — feature disabled in repository settings",
          event.prNumber(),
          event.repoId());
      return;
    }

    log.debug("AI code review triggered for PR #{} in repo {}", event.prNumber(), event.repoId());

    this.reviewService.runReview(
        event.repoId(),
        event.ownerUsername(),
        event.repoName(),
        event.prNumber(),
        event.sourceSha(),
        event.targetBranch(),
        event.authorId());
  }
}
