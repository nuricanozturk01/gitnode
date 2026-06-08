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
package dev.gitnode.os.webhook.services;

import dev.gitnode.os.events.actions.WorkflowRunCompletedEvent;
import dev.gitnode.os.events.branch.BranchCreatedEvent;
import dev.gitnode.os.events.branch.BranchDeletedEvent;
import dev.gitnode.os.events.issue.IssueCommentedEvent;
import dev.gitnode.os.events.issue.IssueCreatedEvent;
import dev.gitnode.os.events.issue.IssueStatusChangedEvent;
import dev.gitnode.os.events.issue.IssueUpdatedEvent;
import dev.gitnode.os.events.pr.PullRequestCreatedEvent;
import dev.gitnode.os.events.pr.PullRequestStatusChangedEvent;
import dev.gitnode.os.events.project.ProjectCreatedEvent;
import dev.gitnode.os.events.project.ProjectDeletedEvent;
import dev.gitnode.os.events.project.ProjectUpdatedEvent;
import dev.gitnode.os.events.repo.RepoCreatedEvent;
import dev.gitnode.os.events.repo.RepoPushedEvent;
import dev.gitnode.os.events.snippet.SnippetCreatedEvent;
import dev.gitnode.os.events.snippet.SnippetDeletedEvent;
import dev.gitnode.os.events.snippet.SnippetUpdatedEvent;
import dev.gitnode.os.events.task.TaskCreatedEvent;
import dev.gitnode.os.events.task.TaskDeletedEvent;
import dev.gitnode.os.events.task.TaskUpdatedEvent;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import dev.gitnode.os.webhook.entities.WebhookEventType;
import dev.gitnode.os.webhook.repositories.ProjectWebhookRepository;
import dev.gitnode.os.webhook.repositories.UserWebhookRepository;
import dev.gitnode.os.webhook.repositories.WebhookRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@NullMarked
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WebhookDispatcher {

  private final WebhookRepository webhookRepository;
  private final UserWebhookRepository userWebhookRepository;
  private final ProjectWebhookRepository projectWebhookRepository;
  private final RepoRepository repoRepository;
  private final TenantRepository tenantRepository;
  private final WebhookDeliveryService deliveryService;

  // ── PR events ──────────────────────────────────────────────────────────

  @ApplicationModuleListener
  void onPrCreated(final PullRequestCreatedEvent event) {
    this.dispatch(
        event.repoId(),
        WebhookEventType.PULL_REQUEST_OPENED,
        Map.of("prId", event.prId(), "sourceBranch", event.sourceBranch()));
  }

  @ApplicationModuleListener
  void onPrStatusChanged(final PullRequestStatusChangedEvent event) {
    final var type =
        switch (event.newStatus()) {
          case "CLOSED" -> WebhookEventType.PULL_REQUEST_CLOSED;
          case "MERGED" -> WebhookEventType.PULL_REQUEST_MERGED;
          default -> null;
        };
    if (type != null) {
      this.dispatch(
          event.repoId(),
          type,
          Map.of(
              "prId",
              event.prId(),
              "sourceBranch",
              event.sourceBranch(),
              "targetBranch",
              event.targetBranch(),
              "status",
              event.newStatus()));
    }
  }

  // ── Repo events ────────────────────────────────────────────────────────

  @ApplicationModuleListener
  void onRepoCreated(final RepoCreatedEvent event) {
    this.repoRepository
        .findByOwnerUsernameAndName(event.repoOwner(), event.repoName())
        .ifPresent(
            repo ->
                this.dispatch(
                    repo.getId(),
                    WebhookEventType.REPO_CREATED,
                    Map.of("owner", event.repoOwner(), "name", event.repoName())));
  }

  @ApplicationModuleListener
  void onRepoPushed(final RepoPushedEvent event) {
    this.dispatch(
        event.repoId(),
        WebhookEventType.REPO_PUSHED,
        Map.of("branchName", event.branchName(), "pusher", event.pusherUsername()));
  }

  // ── Branch events (non-transactional — use @EventListener) ────────────

  @EventListener
  @Async
  void onBranchCreated(final BranchCreatedEvent event) {
    this.dispatch(
        event.repoId(), WebhookEventType.BRANCH_CREATED, Map.of("branchName", event.branchName()));
  }

  @EventListener
  @Async
  void onBranchDeleted(final BranchDeletedEvent event) {
    this.dispatch(
        event.repoId(), WebhookEventType.BRANCH_DELETED, Map.of("branchName", event.branchName()));
  }

  // ── Issue events ───────────────────────────────────────────────────────

  @ApplicationModuleListener
  void onIssueCreated(final IssueCreatedEvent event) {
    this.dispatch(
        event.repoId(),
        WebhookEventType.ISSUE_OPENED,
        Map.of("issueId", event.issueId(), "number", event.number(), "title", event.title()));
  }

  @ApplicationModuleListener
  void onIssueStatusChanged(final IssueStatusChangedEvent event) {
    final var type =
        switch (event.newStatus()) {
          case "CLOSED" -> WebhookEventType.ISSUE_CLOSED;
          case "OPEN" -> WebhookEventType.ISSUE_REOPENED;
          default -> WebhookEventType.ISSUE_UPDATED;
        };
    this.dispatch(
        event.repoId(),
        type,
        Map.of("issueId", event.issueId(), "number", event.number(), "status", event.newStatus()));
  }

  @ApplicationModuleListener
  void onIssueUpdated(final IssueUpdatedEvent event) {
    this.dispatch(
        event.repoId(),
        WebhookEventType.ISSUE_UPDATED,
        Map.of("issueId", event.issueId(), "number", event.number()));
  }

  @ApplicationModuleListener
  void onIssueCommented(final IssueCommentedEvent event) {
    this.dispatch(
        event.repoId(),
        WebhookEventType.ISSUE_COMMENTED,
        Map.of(
            "commentId", event.commentId(),
            "issueId", event.issueId(),
            "number", event.issueNumber(),
            "body", event.body()));
  }

  // ── Project events ─────────────────────────────────────────────────────

  @ApplicationModuleListener
  void onProjectCreated(final ProjectCreatedEvent event) {
    this.dispatchToUserWebhooks(
        event.ownerUsername(),
        WebhookEventType.PROJECT_CREATED,
        Map.of(
            "projectId", event.projectId(), "name", event.name(), "owner", event.ownerUsername()));
  }

  @ApplicationModuleListener
  void onProjectDeleted(final ProjectDeletedEvent event) {
    this.dispatchToUserWebhooks(
        event.ownerUsername(),
        WebhookEventType.PROJECT_DELETED,
        Map.of(
            "projectId", event.projectId(), "name", event.name(), "owner", event.ownerUsername()));
  }

  @ApplicationModuleListener
  void onProjectUpdated(final ProjectUpdatedEvent event) {
    this.dispatchToUserWebhooks(
        event.ownerUsername(),
        WebhookEventType.PROJECT_UPDATED,
        Map.of(
            "projectId", event.projectId(), "name", event.name(), "owner", event.ownerUsername()));
  }

  // ── Task events ────────────────────────────────────────────────────────

  @ApplicationModuleListener
  void onTaskCreated(final TaskCreatedEvent event) {
    this.dispatchToProjectWebhooks(
        event.projectId(),
        WebhookEventType.TASK_CREATED,
        Map.of(
            "taskId", event.taskId(),
            "code", event.code(),
            "projectId", event.projectId(),
            "owner", event.ownerUsername()));
  }

  @ApplicationModuleListener
  void onTaskDeleted(final TaskDeletedEvent event) {
    this.dispatchToProjectWebhooks(
        event.projectId(),
        WebhookEventType.TASK_DELETED,
        Map.of(
            "taskId", event.taskId(),
            "code", event.code(),
            "projectId", event.projectId(),
            "owner", event.ownerUsername()));
  }

  @ApplicationModuleListener
  void onTaskUpdated(final TaskUpdatedEvent event) {
    this.dispatchToProjectWebhooks(
        event.projectId(),
        WebhookEventType.TASK_UPDATED,
        Map.of(
            "taskId", event.taskId(),
            "code", event.code(),
            "projectId", event.projectId(),
            "owner", event.ownerUsername()));
  }

  // ── Snippet events ─────────────────────────────────────────────────────

  @ApplicationModuleListener
  void onSnippetCreated(final SnippetCreatedEvent event) {
    this.dispatchSnippetEvent(
        event.snippetId(),
        event.title(),
        event.ownerUsername(),
        event.repoId(),
        WebhookEventType.SNIPPET_CREATED);
  }

  @ApplicationModuleListener
  void onSnippetDeleted(final SnippetDeletedEvent event) {
    this.dispatchToUserWebhooks(
        event.ownerUsername(),
        WebhookEventType.SNIPPET_DELETED,
        Map.of(
            "snippetId", event.snippetId(),
            "title", event.title(),
            "owner", event.ownerUsername()));
  }

  @ApplicationModuleListener
  void onSnippetUpdated(final SnippetUpdatedEvent event) {
    this.dispatchSnippetEvent(
        event.snippetId(),
        event.title(),
        event.ownerUsername(),
        event.repoId(),
        WebhookEventType.SNIPPET_UPDATED);
  }

  private void dispatchSnippetEvent(
      final UUID snippetId,
      final String title,
      final String ownerUsername,
      final @Nullable UUID repoId,
      final WebhookEventType type) {
    final var data = new LinkedHashMap<String, Object>();
    data.put("snippetId", snippetId);
    data.put("title", title);
    data.put("owner", ownerUsername);
    if (repoId != null) {
      data.put("repoId", repoId);
    }
    this.dispatchToUserWebhooks(ownerUsername, type, data);
  }

  // ── Actions events ─────────────────────────────────────────────────────

  @ApplicationModuleListener
  void onWorkflowRunCompleted(final WorkflowRunCompletedEvent event) {
    this.dispatch(
        event.repoId(),
        WebhookEventType.WORKFLOW_RUN_COMPLETED,
        Map.of(
            "runId", event.runId(),
            "workflowName", event.workflowName(),
            "conclusion", event.conclusion()));
  }

  // ── Core dispatch logic ────────────────────────────────────────────────

  private void dispatchToProjectWebhooks(
      final UUID projectId, final WebhookEventType type, final Map<String, Object> data) {
    final var webhooks = this.projectWebhookRepository.findAllByProjectIdAndEnabledTrue(projectId);
    for (var webhook : webhooks) {
      if (webhook.getSubscribedEvents().contains(type.name())) {
        this.deliveryService.deliver(
            webhook.getId(),
            webhook.getUrl(),
            webhook.getSecret(),
            null,
            "Project webhook",
            type,
            data);
      }
    }
  }

  private void dispatchToUserWebhooks(
      final String ownerUsername, final WebhookEventType type, final Map<String, Object> data) {
    this.tenantRepository
        .findByUsername(ownerUsername)
        .ifPresent(
            tenant -> {
              final var webhooks =
                  this.userWebhookRepository.findAllByUserIdAndEnabledTrue(tenant.getId());
              for (var webhook : webhooks) {
                if (webhook.getSubscribedEvents().contains(type.name())) {
                  this.deliveryService.deliver(
                      webhook.getId(),
                      webhook.getUrl(),
                      webhook.getSecret(),
                      null,
                      "User webhook",
                      type,
                      data);
                }
              }
            });
  }

  private void dispatch(
      final UUID repoId, final WebhookEventType type, final Map<String, Object> data) {
    final var webhooks = this.webhookRepository.findAllByRepoIdAndEnabledTrue(repoId);
    for (var webhook : webhooks) {
      if (webhook.getSubscribedEvents().contains(type.name())) {
        this.deliveryService.deliver(
            webhook.getId(), webhook.getUrl(), webhook.getSecret(), repoId, "Webhook", type, data);
      }
    }
  }
}
