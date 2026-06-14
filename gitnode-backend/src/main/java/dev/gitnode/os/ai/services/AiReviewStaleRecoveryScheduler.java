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
package dev.gitnode.os.ai.services;

import static dev.gitnode.os.ai.entities.ReviewStatus.PENDING;

import dev.gitnode.os.ai.entities.ReviewStatus;
import dev.gitnode.os.ai.repositories.AiCodeReviewRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class AiReviewStaleRecoveryScheduler {

  static final Duration STALE_PENDING_TIMEOUT = Duration.ofMinutes(8);

  private final AiCodeReviewRepository reviewRepository;

  @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
  @Transactional
  public void recoverStalePendingReviews() {

    final var cutoff = Instant.now().minus(STALE_PENDING_TIMEOUT);
    final var stale = this.reviewRepository.findByStatusAndUpdatedAtBefore(PENDING, cutoff);

    for (final var review : stale) {
      log.warn(
          "Marking stale AI review as FAILED for PR #{} in repo {} (pending since {})",
          review.getPrNumber(),
          review.getRepoId(),
          review.getUpdatedAt());

      review.setStatus(ReviewStatus.FAILED);

      review.setStatusMessage(
          "AI review timed out after "
              + STALE_PENDING_TIMEOUT.toMinutes()
              + " minutes. Check your AI provider settings and use Restart review.");
      this.reviewRepository.save(review);
    }
  }
}
