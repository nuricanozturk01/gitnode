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
import dev.gitnode.os.events.filecomment.FileCommentedEvent;
import dev.gitnode.os.events.issue.IssueCommentedEvent;
import dev.gitnode.os.events.pr.PullRequestCommentedEvent;
import dev.gitnode.os.events.pr.PullRequestMetadataChangedEvent;
import dev.gitnode.os.events.pr.PullRequestReviewEvent;
import dev.gitnode.os.events.pr.PullRequestStatusChangedEvent;
import dev.gitnode.os.events.profile.TenantDeletingEvent;
import dev.gitnode.os.events.team.TeamInvitationClosedEvent;
import dev.gitnode.os.events.team.TeamInvitedEvent;
import dev.gitnode.os.notification.entities.NotificationType;
import dev.gitnode.os.notification.services.NotificationService;
import dev.gitnode.os.notification.services.NotificationSseRegistry;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@NullMarked
public class NotificationEventListener {

  private final NotificationService notificationService;
  private final NotificationSseRegistry sseRegistry;

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
  public void onTenantDisabled(final dev.gitnode.os.events.profile.TenantDisabledEvent event) {
    this.sseRegistry.closeAll(event.tenantId());
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
  public void onFileComment(final FileCommentedEvent event) {

    final var link =
        "/"
            + event.ownerUsername()
            + "/"
            + event.repoName()
            + "/blob/"
            + event.branch()
            + "/"
            + event.path();
    final var title =
        (event.reply() ? "New reply on a comment in " : "New comment on ")
            + event.repoName()
            + "/"
            + event.path();
    for (final var recipientId : event.recipientIds()) {
      this.notificationService.send(
          recipientId,
          event.actorId(),
          NotificationType.FILE_COMMENT,
          title,
          null,
          link,
          event.commentId());
    }
  }

  @ApplicationModuleListener
  public void onPrMetadataChanged(final PullRequestMetadataChangedEvent event) {

    final var link =
        "/" + event.ownerUsername() + "/" + event.repoName() + "/pulls/" + event.prNumber();
    final var assigned = "ASSIGNED".equals(event.kind());
    final var title =
        assigned
            ? "You were assigned to PR #" + event.prNumber()
            : "Labels or milestone changed on PR #" + event.prNumber();
    for (final var recipientId : event.recipientIds()) {
      this.notificationService.send(
          recipientId,
          event.actorId(),
          assigned ? NotificationType.PR_ASSIGNED : NotificationType.PR_METADATA_CHANGED,
          title,
          null,
          link,
          event.prId());
    }
  }

  @ApplicationModuleListener
  public void onPrReview(final PullRequestReviewEvent event) {

    final var link =
        "/" + event.ownerUsername() + "/" + event.repoName() + "/pulls/" + event.prNumber();
    final var requested = "REVIEW_REQUESTED".equals(event.kind());
    final var title =
        switch (event.kind()) {
          case "REVIEW_REQUESTED" -> "Review requested on PR #" + event.prNumber();
          case "APPROVED" -> "PR #" + event.prNumber() + " was approved";
          case "CHANGES_REQUESTED" -> "Changes requested on PR #" + event.prNumber();
          default -> "New review on PR #" + event.prNumber();
        };
    for (final var recipientId : event.recipientIds()) {
      this.notificationService.send(
          recipientId,
          event.actorId(),
          requested ? NotificationType.PR_REVIEW_REQUESTED : NotificationType.PR_REVIEW_SUBMITTED,
          title,
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

  @ApplicationModuleListener
  public void onTeamInvited(final TeamInvitedEvent event) {

    this.notificationService.send(
        event.memberId(),
        event.ownerId(),
        NotificationType.TEAM_INVITED,
        "You were invited to a team",
        "Role: " + event.role() + ". Open Team to accept or decline.",
        "/team",
        event.invitationId());
  }

  @ApplicationModuleListener
  public void onTeamInvitationClosed(final TeamInvitationClosedEvent event) {

    this.notificationService.deleteByEntity(
        NotificationType.TEAM_INVITED, event.memberId(), event.invitationId());
  }

  @EventListener
  public void onTenantDeleting(final TenantDeletingEvent event) {

    this.notificationService.deleteByActor(NotificationType.TEAM_INVITED, event.tenantId());
  }

  private static String truncate(final String text, final int max) {

    if (text.length() <= max) {
      return text;
    }

    return text.substring(0, max - 1) + "…";
  }
}
