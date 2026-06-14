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
package dev.gitnode.os.notification.listeners;

import dev.gitnode.os.events.ai.AiCodeReviewCompletedEvent;
import dev.gitnode.os.events.ai.AiCodebaseAnalysisCompletedEvent;
import dev.gitnode.os.events.collaborator.CollaboratorInvitedEvent;
import dev.gitnode.os.events.issue.IssueCommentedEvent;
import dev.gitnode.os.events.pr.PullRequestCommentedEvent;
import dev.gitnode.os.events.pr.PullRequestStatusChangedEvent;
import dev.gitnode.os.notification.entities.NotificationType;
import dev.gitnode.os.notification.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@NullMarked
public class NotificationEventListener {

  private final NotificationService notificationService;

  @ApplicationModuleListener
  public void onIssueComment(final IssueCommentedEvent event) {
    final var link =
        "/" + event.ownerUsername() + "/" + event.repoName() + "/issues/" + event.issueNumber();
    final var body = truncate(event.body(), 200);
    for (final var recipientId : event.participantIds()) {
      this.notificationService.send(
          recipientId,
          event.commenterId(),
          NotificationType.ISSUE_COMMENT,
          "New comment on issue #" + event.issueNumber(),
          body,
          link,
          event.issueId());
    }
  }

  @ApplicationModuleListener
  public void onPrComment(final PullRequestCommentedEvent event) {
    final var link =
        "/" + event.ownerUsername() + "/" + event.repoName() + "/pulls/" + event.prNumber();
    for (final var recipientId : event.participantIds()) {
      this.notificationService.send(
          recipientId,
          event.commenterId(),
          NotificationType.PR_COMMENT,
          "New comment on PR #" + event.prNumber(),
          null,
          link,
          event.prId());
    }
  }

  @ApplicationModuleListener
  public void onPrStatusChanged(final PullRequestStatusChangedEvent event) {
    final var type =
        switch (event.newStatus()) {
          case "MERGED" -> NotificationType.PR_MERGED;
          case "CLOSED" -> NotificationType.PR_CLOSED;
          default -> null;
        };
    if (type == null) {
      return;
    }
    final var title =
        type == NotificationType.PR_MERGED
            ? "PR #" + event.prNumber() + " was merged"
            : "PR #" + event.prNumber() + " was closed";
    final var link =
        "/" + event.ownerUsername() + "/" + event.repoName() + "/pulls/" + event.prNumber();
    for (final var recipientId : event.participantIds()) {
      this.notificationService.send(
          recipientId, event.actorId(), type, title, null, link, event.prId());
    }
  }

  @ApplicationModuleListener
  public void onAiCodeReviewCompleted(final AiCodeReviewCompletedEvent event) {
    final var recipientId = event.prAuthorId();
    if (recipientId == null) {
      return;
    }
    this.notificationService.send(
        recipientId,
        null,
        NotificationType.AI_CODE_REVIEW_COMPLETED,
        "AI code review ready for PR #" + event.prNumber(),
        null,
        "/" + event.ownerUsername() + "/" + event.repoName() + "/pulls/" + event.prNumber(),
        event.reviewId());
  }

  @ApplicationModuleListener
  public void onAiAnalysisCompleted(final AiCodebaseAnalysisCompletedEvent event) {
    final var type =
        "COMPLETED".equals(event.status())
            ? NotificationType.AI_ANALYSIS_COMPLETED
            : NotificationType.AI_ANALYSIS_FAILED;
    final var title =
        type == NotificationType.AI_ANALYSIS_COMPLETED
            ? "AI codebase analysis completed for " + event.repoName()
            : "AI codebase analysis failed for " + event.repoName();
    this.notificationService.send(
        event.triggeredBy(),
        null,
        type,
        title,
        "Branch: " + event.branch(),
        "/" + event.ownerUsername() + "/" + event.repoName() + "/settings#ai-analysis",
        event.analysisId());
  }

  @ApplicationModuleListener
  public void onCollaboratorInvited(final CollaboratorInvitedEvent event) {
    this.notificationService.send(
        event.tenantId(),
        event.invitedById(),
        NotificationType.COLLABORATOR_INVITED,
        "You were invited to " + event.ownerUsername() + "/" + event.repoName(),
        "Click to accept or reject the collaboration invite.",
        "/accept-invite/" + event.inviteToken(),
        event.repoId());
  }

  private static String truncate(final String text, final int max) {
    if (text.length() <= max) {
      return text;
    }
    return text.substring(0, max - 1) + "…";
  }
}
