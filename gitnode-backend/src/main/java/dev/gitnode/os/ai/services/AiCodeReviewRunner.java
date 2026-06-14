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

import dev.gitnode.os.ai.entities.ReviewStatus;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@NullMarked
public class AiCodeReviewRunner {

  private final AiCodeReviewService reviewService;

  @Async("aiReviewExecutor")
  public void executeReview(
      final UUID reviewId,
      final String ownerUsername,
      final String repoName,
      final String sourceSha,
      final String targetBranch,
      final UUID prAuthorId,
      final UUID repoOwnerId,
      final @Nullable UUID requesterId) {
    try {
      this.reviewService.executeReviewTask(
          reviewId,
          ownerUsername,
          repoName,
          sourceSha,
          targetBranch,
          prAuthorId,
          repoOwnerId,
          requesterId);
    } catch (final Exception e) {
      this.reviewService.failReview(
          reviewId,
          "AI review failed unexpectedly. Use Restart review to try again.",
          ReviewStatus.FAILED);
    }
  }
}
