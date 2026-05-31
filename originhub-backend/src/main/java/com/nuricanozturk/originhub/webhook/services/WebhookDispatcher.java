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
package com.nuricanozturk.originhub.webhook.services;

import com.nuricanozturk.originhub.shared.branch.events.BranchCreatedEvent;
import com.nuricanozturk.originhub.shared.branch.events.BranchDeletedEvent;
import com.nuricanozturk.originhub.shared.issue.events.IssueCommentedEvent;
import com.nuricanozturk.originhub.shared.issue.events.IssueCreatedEvent;
import com.nuricanozturk.originhub.shared.issue.events.IssueStatusChangedEvent;
import com.nuricanozturk.originhub.shared.issue.events.IssueUpdatedEvent;
import com.nuricanozturk.originhub.shared.pr.events.PullRequestCreatedEvent;
import com.nuricanozturk.originhub.shared.pr.events.PullRequestStatusChangedEvent;
import com.nuricanozturk.originhub.shared.project.events.ProjectCreatedEvent;
import com.nuricanozturk.originhub.shared.project.events.ProjectDeletedEvent;
import com.nuricanozturk.originhub.shared.project.events.ProjectUpdatedEvent;
import com.nuricanozturk.originhub.shared.repo.events.RepoCreatedEvent;
import com.nuricanozturk.originhub.shared.repo.events.RepoPushedEvent;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.snippet.events.SnippetCreatedEvent;
import com.nuricanozturk.originhub.shared.snippet.events.SnippetDeletedEvent;
import com.nuricanozturk.originhub.shared.snippet.events.SnippetUpdatedEvent;
import com.nuricanozturk.originhub.shared.task.events.TaskCreatedEvent;
import com.nuricanozturk.originhub.shared.task.events.TaskDeletedEvent;
import com.nuricanozturk.originhub.shared.task.events.TaskUpdatedEvent;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import com.nuricanozturk.originhub.webhook.entities.ProjectWebhook;
import com.nuricanozturk.originhub.webhook.entities.UserWebhook;
import com.nuricanozturk.originhub.webhook.entities.Webhook;
import com.nuricanozturk.originhub.webhook.entities.WebhookEventType;
import com.nuricanozturk.originhub.webhook.repositories.ProjectWebhookRepository;
import com.nuricanozturk.originhub.webhook.repositories.UserWebhookRepository;
import com.nuricanozturk.originhub.webhook.repositories.WebhookRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@NullMarked
public class WebhookDispatcher {

  private final WebhookRepository webhookRepository;
  private final UserWebhookRepository userWebhookRepository;
  private final ProjectWebhookRepository projectWebhookRepository;
  private final RepoRepository repoRepository;
  private final TenantRepository tenantRepository;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  public WebhookDispatcher(
      final WebhookRepository webhookRepository,
      final UserWebhookRepository userWebhookRepository,
      final ProjectWebhookRepository projectWebhookRepository,
      final RepoRepository repoRepository,
      final TenantRepository tenantRepository,
      final ObjectMapper objectMapper,
      @Qualifier("webhookRestClient") final RestClient restClient) {
    this.webhookRepository = webhookRepository;
    this.userWebhookRepository = userWebhookRepository;
    this.projectWebhookRepository = projectWebhookRepository;
    this.repoRepository = repoRepository;
    this.tenantRepository = tenantRepository;
    this.objectMapper = objectMapper;
    this.restClient = restClient;
  }

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
    final var data = new LinkedHashMap<String, Object>();
    data.put("snippetId", event.snippetId());
    data.put("title", event.title());
    data.put("owner", event.ownerUsername());
    if (event.repoId() != null) {
      data.put("repoId", event.repoId());
    }
    this.dispatchToUserWebhooks(event.ownerUsername(), WebhookEventType.SNIPPET_CREATED, data);
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
    final var data = new LinkedHashMap<String, Object>();
    data.put("snippetId", event.snippetId());
    data.put("title", event.title());
    data.put("owner", event.ownerUsername());
    if (event.repoId() != null) {
      data.put("repoId", event.repoId());
    }
    this.dispatchToUserWebhooks(event.ownerUsername(), WebhookEventType.SNIPPET_UPDATED, data);
  }

  // ── Core dispatch logic ────────────────────────────────────────────────

  private void dispatchToProjectWebhooks(
      final UUID projectId, final WebhookEventType type, final Map<String, Object> data) {
    final var webhooks = this.projectWebhookRepository.findAllByProjectIdAndEnabledTrue(projectId);
    for (var webhook : webhooks) {
      if (webhook.getSubscribedEvents().contains(type.name())) {
        this.deliverProjectWebhookAsync(webhook, type, data);
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
                  this.deliverUserWebhookAsync(webhook, type, data);
                }
              }
            });
  }

  private void dispatch(
      final UUID repoId, final WebhookEventType type, final Map<String, Object> data) {
    final var webhooks = this.webhookRepository.findAllByRepoIdAndEnabledTrue(repoId);
    for (var webhook : webhooks) {
      if (webhook.getSubscribedEvents().contains(type.name())) {
        this.deliverAsync(webhook, type, repoId, data);
      }
    }
  }

  private void deliverAsync(
      final Webhook webhook,
      final WebhookEventType type,
      final UUID repoId,
      final Map<String, Object> data) {
    try {
      final var payload = new LinkedHashMap<String, Object>();
      payload.put("event", type.getValue());
      payload.put("timestamp", Instant.now().toString());
      payload.put("repoId", repoId.toString());
      payload.put("data", data);

      final var body = this.objectMapper.writeValueAsString(payload);

      var spec =
          this.restClient
              .post()
              .uri(webhook.getUrl())
              .contentType(MediaType.APPLICATION_JSON)
              .body(body);

      if (webhook.getSecret() != null && !webhook.getSecret().isBlank()) {
        spec =
            spec.header("X-Hub-Signature-256", this.computeHmacSha256(body, webhook.getSecret()));
      }

      spec.retrieve().toBodilessEntity();

    } catch (final Exception ex) {
      log.warn(
          "Webhook delivery failed id={} url={}: {}",
          webhook.getId(),
          webhook.getUrl(),
          ex.getMessage());
    }
  }

  private void deliverProjectWebhookAsync(
      final ProjectWebhook webhook, final WebhookEventType type, final Map<String, Object> data) {
    this.deliverWebhookCore(
        webhook.getId(), webhook.getUrl(), webhook.getSecret(), "Project webhook", type, data);
  }

  private void deliverUserWebhookAsync(
      final UserWebhook webhook, final WebhookEventType type, final Map<String, Object> data) {
    this.deliverWebhookCore(
        webhook.getId(), webhook.getUrl(), webhook.getSecret(), "User webhook", type, data);
  }

  private void deliverWebhookCore(
      final UUID id,
      final String url,
      final @Nullable String secret,
      final String logLabel,
      final WebhookEventType type,
      final Map<String, Object> data) {
    try {
      final var payload = new LinkedHashMap<String, Object>();
      payload.put("event", type.getValue());
      payload.put("timestamp", Instant.now().toString());
      payload.put("data", data);

      final var body = this.objectMapper.writeValueAsString(payload);

      var spec = this.restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body);

      if (secret != null && !secret.isBlank()) {
        spec = spec.header("X-Hub-Signature-256", this.computeHmacSha256(body, secret));
      }

      spec.retrieve().toBodilessEntity();

    } catch (final Exception ex) {
      log.warn("{} delivery failed id={} url={}: {}", logLabel, id, url, ex.getMessage());
    }
  }

  private String computeHmacSha256(final String body, final String secret) {
    try {
      final var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      final var bytes = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
      return "sha256=" + HexFormat.of().formatHex(bytes);
    } catch (final Exception ex) {
      throw new RuntimeException("HMAC-SHA256 computation failed", ex);
    }
  }
}
