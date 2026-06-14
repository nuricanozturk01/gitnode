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

import static dev.gitnode.os.shared.util.FileDiffParser.prepareTreeParser;

import dev.gitnode.os.ai.dtos.AiCodeReviewCommentDto;
import dev.gitnode.os.ai.dtos.AiCodeReviewDto;
import dev.gitnode.os.ai.entities.AiCodeReview;
import dev.gitnode.os.ai.entities.AiCodeReviewComment;
import dev.gitnode.os.ai.entities.ReviewCategory;
import dev.gitnode.os.ai.entities.ReviewSeverity;
import dev.gitnode.os.ai.entities.ReviewStatus;
import dev.gitnode.os.ai.repositories.AiCodeReviewCommentRepository;
import dev.gitnode.os.ai.repositories.AiCodeReviewRepository;
import dev.gitnode.os.events.ai.AiCodeReviewCompletedEvent;
import dev.gitnode.os.pr.api.PrQueryPort;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.git.provider.GitProvider;
import dev.gitnode.os.shared.repo.dtos.PageResponse;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Constants;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@NullMarked
public class AiCodeReviewService {

  private static final String SKIPPED_MESSAGE =
      "AI review was skipped because no enabled AI settings were found for the PR author or"
          + " repo owner. Enable AI in User Settings and click Retry to run with your account.";

  private static final int MAX_STATUS_MESSAGE_LENGTH = 500;

  private static final int DEFAULT_PAGE_SIZE = 20;

  /** Block duplicate retries only while a review was started within this window. */
  static final Duration PENDING_ACTIVE_WINDOW = Duration.ofMinutes(2);

  private final AiCodeReviewRepository reviewRepository;
  private final AiCodeReviewCommentRepository commentRepository;
  private final UserAiSettingsService settingsService;
  private final GitProvider gitProvider;
  private final ApplicationEventPublisher eventPublisher;
  private final PrQueryPort prQueryPort;
  private final RepoRepository repoRepository;
  private final AiCodeReviewRunner reviewRunner;
  private final AiCodeReviewService self;

  AiCodeReviewService(
      final AiCodeReviewRepository reviewRepository,
      final AiCodeReviewCommentRepository commentRepository,
      final UserAiSettingsService settingsService,
      final GitProvider gitProvider,
      final ApplicationEventPublisher eventPublisher,
      final PrQueryPort prQueryPort,
      final RepoRepository repoRepository,
      @Lazy final AiCodeReviewRunner reviewRunner,
      @Lazy final AiCodeReviewService self) {
    this.reviewRepository = reviewRepository;
    this.commentRepository = commentRepository;
    this.settingsService = settingsService;
    this.gitProvider = gitProvider;
    this.eventPublisher = eventPublisher;
    this.prQueryPort = prQueryPort;
    this.repoRepository = repoRepository;
    this.reviewRunner = reviewRunner;
    this.self = self;
  }

  @Transactional(readOnly = true)
  public Optional<AiCodeReviewDto> findReview(
      final UUID repoId, final int prNumber, final int page, final int size) {
    return this.reviewRepository
        .findByRepoIdAndPrNumber(repoId, prNumber)
        .map(review -> this.toDto(review, page, size));
  }

  @Transactional
  public void runReview(
      final UUID repoId,
      final String ownerUsername,
      final String repoName,
      final int prNumber,
      final String sourceSha,
      final String targetBranch,
      final UUID prAuthorId) {

    if (this.reviewRepository.findByRepoIdAndPrNumber(repoId, prNumber).isPresent()) {
      return;
    }

    final var repoOwnerId = this.resolveRepoOwnerId(repoId);
    final var enabledSettings =
        this.settingsService.findFirstEnabledSettings(prAuthorId, repoOwnerId);

    if (enabledSettings.isEmpty()) {
      final var skipped = this.newReview(repoId, prNumber);
      skipped.setStatus(ReviewStatus.SKIPPED);
      skipped.setStatusMessage(SKIPPED_MESSAGE);
      this.reviewRepository.save(skipped);
      return;
    }

    final var review = this.newReview(repoId, prNumber);
    review.setStatus(ReviewStatus.PENDING);
    final var savedReview = this.reviewRepository.save(review);

    this.scheduleReviewExecution(
        savedReview.getId(),
        ownerUsername,
        repoName,
        sourceSha,
        targetBranch,
        prAuthorId,
        repoOwnerId,
        null);
  }

  private void scheduleReviewExecution(
      final UUID reviewId,
      final String ownerUsername,
      final String repoName,
      final String sourceSha,
      final String targetBranch,
      final UUID prAuthorId,
      final UUID repoOwnerId,
      final @Nullable UUID requesterId) {
    this.reviewRunner.executeReview(
        reviewId,
        ownerUsername,
        repoName,
        sourceSha,
        targetBranch,
        prAuthorId,
        repoOwnerId,
        requesterId);
  }

  @Transactional
  public AiCodeReviewDto retryReview(
      final UUID repoId,
      final String ownerUsername,
      final String repoName,
      final int prNumber,
      final UUID requesterId,
      final UUID repoOwnerId) {

    if (this.settingsService.findEnabledSettings(requesterId).isEmpty()) {
      throw new ErrorOccurredException(
          "Enable AI in your user settings before running the review.");
    }

    final var pr =
        this.prQueryPort
            .findByRepoIdAndNumber(repoId, prNumber)
            .orElseThrow(() -> new ItemNotFoundException("Pull request not found"));

    final var sourceSha = pr.sourceSha();
    if (sourceSha == null || sourceSha.isBlank()) {
      throw new ErrorOccurredException("Pull request head commit is not available.");
    }

    final var review =
        this.reviewRepository
            .findByRepoIdAndPrNumber(repoId, prNumber)
            .orElseGet(() -> this.newReview(repoId, prNumber));

    if (review.getStatus() == ReviewStatus.PENDING && isReviewActivelyRunning(review)) {
      throw new ErrorOccurredException(
          "AI review is still running. Wait a moment and refresh the page.");
    }

    if (review.getStatus() == ReviewStatus.PENDING) {
      log.warn(
          "Restarting stale AI review for PR #{} in repo {} (last update: {})",
          prNumber,
          repoId,
          review.getUpdatedAt());
    }

    this.resetReview(review);
    final var savedReview = this.reviewRepository.save(review);

    this.scheduleReviewExecution(
        savedReview.getId(),
        ownerUsername,
        repoName,
        sourceSha,
        pr.targetBranch(),
        pr.authorId(),
        repoOwnerId,
        requesterId);

    return this.toDto(savedReview, 0, DEFAULT_PAGE_SIZE);
  }

  public void executeReviewTask(
      final UUID reviewId,
      final String ownerUsername,
      final String repoName,
      final String sourceSha,
      final String targetBranch,
      final UUID prAuthorId,
      final UUID repoOwnerId,
      final @Nullable UUID requesterId) {

    log.info("AI code review task started for review {}", reviewId);
    this.self.markReviewRunning(reviewId);

    final var enabledSettings =
        requesterId != null
            ? this.settingsService.findFirstEnabledSettings(requesterId, prAuthorId, repoOwnerId)
            : this.settingsService.findFirstEnabledSettings(prAuthorId, repoOwnerId);

    if (enabledSettings.isEmpty()) {
      this.self.failReview(reviewId, SKIPPED_MESSAGE, ReviewStatus.SKIPPED);
      return;
    }

    try {
      final var settings = this.settingsService.decryptSettings(enabledSettings.get());
      final var providerService = this.settingsService.resolveProvider(settings);

      final var diffResult = this.buildDiffText(ownerUsername, repoName, sourceSha, targetBranch);
      log.info(
          "AI code review calling provider for review {} (diff chars={})",
          reviewId,
          diffResult.text().length());

      final var rawResponse =
          providerService.complete(
              settings,
              AiPrompts.CODE_REVIEW,
              diffResult.text(),
              AiInputBounds.REVIEW_MAX_COMPLETION_TOKENS);

      this.self.completeReview(reviewId, rawResponse);
      log.info("AI code review completed for review {}", reviewId);

    } catch (final Exception e) {
      log.error("AI code review failed for review {}: {}", reviewId, e.getMessage());
      this.self.failReview(
          reviewId, this.truncateStatusMessage(e.getMessage()), ReviewStatus.FAILED);
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markReviewRunning(final UUID reviewId) {
    final var review = this.reviewRepository.findById(reviewId).orElse(null);
    if (review == null || review.getStatus() != ReviewStatus.PENDING) {
      return;
    }
    review.setStatusMessage("AI review in progress — contacting provider…");
    this.reviewRepository.save(review);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void completeReview(final UUID reviewId, final String rawResponse) {
    final var review =
        this.reviewRepository
            .findById(reviewId)
            .orElseThrow(() -> new ErrorOccurredException("AI review not found"));

    this.commentRepository.deleteByReviewId(reviewId);
    review.getComments().clear();
    this.parseAndSaveComments(review, rawResponse);
    review.setStatus(ReviewStatus.COMPLETED);
    review.setStatusMessage(null);
    this.reviewRepository.save(review);

    this.eventPublisher.publishEvent(
        new AiCodeReviewCompletedEvent(review.getId(), review.getRepoId(), review.getPrNumber()));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failReview(final UUID reviewId, final String message, final ReviewStatus status) {
    final var review = this.reviewRepository.findById(reviewId).orElse(null);
    if (review == null) {
      return;
    }
    review.setStatus(status);
    review.setStatusMessage(message);
    this.reviewRepository.save(review);
  }

  private AiCodeReview newReview(final UUID repoId, final int prNumber) {
    final var review = new AiCodeReview();
    review.setRepoId(repoId);
    review.setPrNumber(prNumber);
    return review;
  }

  static boolean isReviewActivelyRunning(final AiCodeReview review) {
    if (review.getStatus() != ReviewStatus.PENDING) {
      return false;
    }
    final var updatedAt = review.getUpdatedAt();
    if (updatedAt == null) {
      return false;
    }
    return updatedAt.isAfter(Instant.now().minus(PENDING_ACTIVE_WINDOW));
  }

  private void resetReview(final AiCodeReview review) {
    review.getComments().clear();
    review.setStatus(ReviewStatus.PENDING);
    review.setSummary(null);
    review.setStatusMessage(null);
  }

  private @Nullable UUID resolveRepoOwnerId(final UUID repoId) {
    return this.repoRepository.findById(repoId).map(repo -> repo.getOwner().getId()).orElse(null);
  }

  private String truncateStatusMessage(final @Nullable String message) {
    if (message == null || message.isBlank()) {
      return "AI review failed. Check your AI settings and try again.";
    }
    return message.length() > MAX_STATUS_MESSAGE_LENGTH
        ? message.substring(0, MAX_STATUS_MESSAGE_LENGTH)
        : message;
  }

  private AiDiffFormatter.DiffBuildResult buildDiffText(
      final String owner, final String repoName, final String sourceSha, final String targetBranch)
      throws IOException {
    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {
      final var sourceId = gitRepo.resolve(sourceSha);
      final var targetId = gitRepo.resolve(Constants.R_HEADS + targetBranch);
      if (sourceId == null || targetId == null) {
        return new AiDiffFormatter.DiffBuildResult(
            "Could not resolve branches", new AiDiffFormatter.DiffBuildStats());
      }

      final var sourceTree = prepareTreeParser(gitRepo, sourceId);
      final var targetTree = prepareTreeParser(gitRepo, targetId);

      return AiDiffFormatter.buildBoundedDiff(
          gitRepo,
          targetTree,
          sourceTree,
          AiInputBounds.REVIEW_MAX_FILES,
          AiInputBounds.REVIEW_MAX_DIFF_CHARS);
    }
  }

  private void parseAndSaveComments(final AiCodeReview review, final String raw) {
    final var lines = raw.strip().split("\n");
    for (final var line : lines) {
      if (line.startsWith("SUMMARY:")) {
        review.setSummary(line.substring("SUMMARY:".length()).strip());
      } else if (line.startsWith("FILE:")) {
        this.parseCommentLine(review, line);
      }
    }
  }

  private void parseCommentLine(final AiCodeReview review, final String line) {
    try {
      if (!line.startsWith("FILE:")) {
        return;
      }
      final var parsed = this.extractCommentFields(line);
      if (parsed != null) {
        review.getComments().add(this.buildCommentEntity(review, parsed));
      }
    } catch (final Exception e) {
      log.debug("Failed to parse AI comment line: {}", line);
    }
  }

  private @Nullable ParsedComment extractCommentFields(final String line) {
    final var fields = new MutableCommentFields();
    for (final var part : line.split("\\|")) {
      this.applyLinePart(fields, part.strip());
    }
    if (fields.filePath == null
        || fields.filePath.isBlank()
        || fields.comment == null
        || fields.comment.isBlank()) {
      return null;
    }
    return new ParsedComment(
        fields.filePath,
        fields.lineNumber,
        fields.category,
        fields.severity,
        fields.comment,
        fields.suggestion);
  }

  private void applyLinePart(final MutableCommentFields fields, final String trimmed) {
    if (trimmed.startsWith("FILE:")) {
      fields.filePath = this.extractValue(trimmed, "FILE:");
    } else if (trimmed.startsWith("LINE:")) {
      fields.lineNumber = this.parseLineNumber(this.extractValue(trimmed, "LINE:"));
    } else if (trimmed.startsWith("CATEGORY:")) {
      fields.category = this.parseCategory(this.extractValue(trimmed, "CATEGORY:"));
    } else if (trimmed.startsWith("SEVERITY:")) {
      fields.severity = this.parseSeverity(this.extractValue(trimmed, "SEVERITY:"));
    } else if (trimmed.startsWith("COMMENT:")) {
      fields.comment = this.extractValue(trimmed, "COMMENT:");
    } else if (trimmed.startsWith("FIX:")) {
      fields.suggestion = this.extractValue(trimmed, "FIX:");
    }
  }

  private AiCodeReviewComment buildCommentEntity(
      final AiCodeReview review, final ParsedComment parsed) {
    final var entity = new AiCodeReviewComment();
    entity.setReview(review);
    entity.setFilePath(parsed.filePath());
    entity.setLineNumber(parsed.lineNumber());
    entity.setCategory(parsed.category());
    entity.setSeverity(parsed.severity());
    entity.setComment(parsed.comment());
    entity.setSuggestion(this.normalizeSuggestion(parsed.suggestion()));
    return entity;
  }

  private @Nullable String normalizeSuggestion(final @Nullable String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.strip();
  }

  private String extractValue(final String part, final String prefix) {
    return part.startsWith(prefix) ? part.substring(prefix.length()).strip() : part.strip();
  }

  private @Nullable Integer parseLineNumber(final String value) {
    try {
      final var n = Integer.parseInt(value);
      return n > 0 ? n : null;
    } catch (final NumberFormatException e) {
      return null;
    }
  }

  private ReviewCategory parseCategory(final String value) {
    try {
      return ReviewCategory.valueOf(value.toUpperCase());
    } catch (final IllegalArgumentException e) {
      return ReviewCategory.GENERAL;
    }
  }

  private ReviewSeverity parseSeverity(final String value) {
    try {
      return ReviewSeverity.valueOf(value.toUpperCase());
    } catch (final IllegalArgumentException e) {
      return ReviewSeverity.INFO;
    }
  }

  private AiCodeReviewDto toDto(final AiCodeReview review, final int page, final int size) {
    final var pageRequest = PageRequest.of(page, Math.clamp(size, 1, DEFAULT_PAGE_SIZE));
    final var commentsPage = this.commentRepository.findByReviewId(review.getId(), pageRequest);
    final var commentDtos = PageResponse.from(commentsPage.map(this::toCommentDto));

    return new AiCodeReviewDto(
        review.getId(),
        review.getStatus(),
        review.getSummary(),
        review.getStatusMessage(),
        review.getCreatedAt(),
        commentDtos);
  }

  private AiCodeReviewCommentDto toCommentDto(final AiCodeReviewComment c) {
    return new AiCodeReviewCommentDto(
        c.getId(),
        c.getFilePath(),
        c.getLineNumber(),
        c.getCategory(),
        c.getSeverity(),
        c.getComment(),
        c.getSuggestion());
  }

  private static final class MutableCommentFields {
    @Nullable String filePath;
    @Nullable Integer lineNumber;
    ReviewCategory category = ReviewCategory.GENERAL;
    ReviewSeverity severity = ReviewSeverity.INFO;
    @Nullable String comment;
    @Nullable String suggestion;
  }

  private record ParsedComment(
      String filePath,
      @Nullable Integer lineNumber,
      ReviewCategory category,
      ReviewSeverity severity,
      String comment,
      @Nullable String suggestion) {}
}
