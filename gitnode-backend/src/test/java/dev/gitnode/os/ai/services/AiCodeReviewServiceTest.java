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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.ai.entities.AiCodeReview;
import dev.gitnode.os.ai.entities.ReviewCategory;
import dev.gitnode.os.ai.entities.ReviewSeverity;
import dev.gitnode.os.ai.entities.ReviewStatus;
import dev.gitnode.os.ai.repositories.AiCodeReviewCommentRepository;
import dev.gitnode.os.ai.repositories.AiCodeReviewRepository;
import dev.gitnode.os.pr.api.PrQueryPort;
import dev.gitnode.os.shared.git.provider.GitProvider;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiCodeReviewService unit tests")
class AiCodeReviewServiceTest {

  @Mock private AiCodeReviewRepository reviewRepository;
  @Mock private AiCodeReviewCommentRepository commentRepository;
  @Mock private UserAiSettingsService settingsService;
  @Mock private GitProvider gitProvider;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private PrQueryPort prQueryPort;
  @Mock private RepoRepository repoRepository;
  @Mock private AiCodeReviewRunner reviewRunner;

  @InjectMocks private AiCodeReviewService service;

  @BeforeEach
  void wireSelfProxy() {
    ReflectionTestUtils.setField(this.service, "self", this.service);
  }

  @Test
  @DisplayName("runReview skips when AI not enabled for author or repo owner")
  void runReview_skips_whenAiNotEnabled() {
    final var repoId = UUID.randomUUID();
    final var authorId = UUID.randomUUID();

    when(reviewRepository.findByRepoIdAndPrNumber(repoId, 1)).thenReturn(Optional.empty());
    when(repoRepository.findById(repoId)).thenReturn(Optional.empty());
    when(settingsService.findFirstEnabledSettings(any(UUID.class), any()))
        .thenReturn(Optional.empty());
    when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.runReview(repoId, "owner", "repo", 1, "abc123", "main", authorId);

    final var captor = ArgumentCaptor.forClass(AiCodeReview.class);
    verify(reviewRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ReviewStatus.SKIPPED);
    assertThat(captor.getValue().getStatusMessage()).isNotBlank();
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("runReview is idempotent — skips if review already exists")
  void runReview_isIdempotent() {
    final var repoId = UUID.randomUUID();
    final var existing = new AiCodeReview();
    existing.setStatus(ReviewStatus.COMPLETED);

    when(reviewRepository.findByRepoIdAndPrNumber(repoId, 1)).thenReturn(Optional.of(existing));

    service.runReview(repoId, "owner", "repo", 1, "abc123", "main", UUID.randomUUID());

    verify(reviewRepository, never()).save(any());
  }

  @Test
  @DisplayName("findReview returns empty when no review exists")
  void findReview_returnsEmpty_whenNotFound() {
    final var repoId = UUID.randomUUID();
    when(reviewRepository.findByRepoIdAndPrNumber(repoId, 1)).thenReturn(Optional.empty());

    assertThat(service.findReview(repoId, 1, 0, 20)).isEmpty();
  }

  @Test
  @DisplayName("parseCommentLine extracts COMMENT and FIX fields")
  void parseCommentLine_extractsCommentAndFix() throws Exception {
    final var review = new AiCodeReview();
    final var line =
        "FILE:src/App.java|LINE:42|CATEGORY:SECURITY|SEVERITY:HIGH|COMMENT:Missing auth check on admin route|FIX:Add role guard before controller entry and add test for forbidden access";

    final var method =
        AiCodeReviewService.class.getDeclaredMethod(
            "parseCommentLine", AiCodeReview.class, String.class);
    method.setAccessible(true);
    method.invoke(
        new AiCodeReviewService(null, null, null, null, null, null, null, null, null),
        review,
        line);

    assertThat(review.getComments()).hasSize(1);
    final var comment = review.getComments().getFirst();
    assertThat(comment.getFilePath()).isEqualTo("src/App.java");
    assertThat(comment.getLineNumber()).isEqualTo(42);
    assertThat(comment.getCategory()).isEqualTo(ReviewCategory.SECURITY);
    assertThat(comment.getSeverity()).isEqualTo(ReviewSeverity.HIGH);
    assertThat(comment.getComment()).contains("Missing auth check");
    assertThat(comment.getSuggestion()).contains("role guard");
  }

  @Test
  @DisplayName("isReviewActivelyRunning is false for stale pending reviews")
  void isReviewActivelyRunning_falseWhenStale() {
    final var review = new AiCodeReview();
    review.setStatus(ReviewStatus.PENDING);
    review.setUpdatedAt(Instant.now().minus(10, ChronoUnit.MINUTES));

    assertThat(AiCodeReviewService.isReviewActivelyRunning(review)).isFalse();
  }

  @Test
  @DisplayName("isReviewActivelyRunning is true for recent pending reviews")
  void isReviewActivelyRunning_trueWhenRecent() {
    final var review = new AiCodeReview();
    review.setStatus(ReviewStatus.PENDING);
    review.setUpdatedAt(Instant.now().minus(30, ChronoUnit.SECONDS));

    assertThat(AiCodeReviewService.isReviewActivelyRunning(review)).isTrue();
  }
}
