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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.shared.issue.events.IssueStatusChangedEvent;
import com.nuricanozturk.originhub.shared.pr.events.PullRequestStatusChangedEvent;
import com.nuricanozturk.originhub.shared.project.events.ProjectCreatedEvent;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.events.RepoCreatedEvent;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.snippet.events.SnippetCreatedEvent;
import com.nuricanozturk.originhub.shared.task.events.TaskCreatedEvent;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import com.nuricanozturk.originhub.webhook.entities.ProjectWebhook;
import com.nuricanozturk.originhub.webhook.entities.UserWebhook;
import com.nuricanozturk.originhub.webhook.entities.Webhook;
import com.nuricanozturk.originhub.webhook.entities.WebhookEventType;
import com.nuricanozturk.originhub.webhook.repositories.ProjectWebhookRepository;
import com.nuricanozturk.originhub.webhook.repositories.UserWebhookRepository;
import com.nuricanozturk.originhub.webhook.repositories.WebhookRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookDispatcher unit tests")
class WebhookDispatcherTest {

  @Mock private WebhookRepository webhookRepository;
  @Mock private UserWebhookRepository userWebhookRepository;
  @Mock private ProjectWebhookRepository projectWebhookRepository;
  @Mock private RepoRepository repoRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private WebhookDeliveryService deliveryService;

  @InjectMocks private WebhookDispatcher webhookDispatcher;

  @Test
  @DisplayName("onPrStatusChanged delivers PULL_REQUEST_CLOSED for CLOSED status")
  void onPrStatusChanged_deliversClosed_whenStatusClosed() {
    UUID repoId = UUID.randomUUID();
    UUID prId = UUID.randomUUID();
    Webhook webhook = repoWebhook(repoId, WebhookEventType.PULL_REQUEST_CLOSED);
    when(webhookRepository.findAllByRepoIdAndEnabledTrue(repoId)).thenReturn(List.of(webhook));

    webhookDispatcher.onPrStatusChanged(
        new PullRequestStatusChangedEvent(prId, repoId, "feature", "main", "CLOSED"));

    verify(deliveryService)
        .deliver(
            eq(webhook.getId()),
            eq(webhook.getUrl()),
            eq(webhook.getSecret()),
            eq(repoId),
            eq("Webhook"),
            eq(WebhookEventType.PULL_REQUEST_CLOSED),
            eq(
                Map.of(
                    "prId", prId,
                    "sourceBranch", "feature",
                    "targetBranch", "main",
                    "status", "CLOSED")));
  }

  @Test
  @DisplayName("onPrStatusChanged skips delivery for non-terminal status")
  void onPrStatusChanged_skips_whenStatusNotClosedOrMerged() {
    webhookDispatcher.onPrStatusChanged(
        new PullRequestStatusChangedEvent(
            UUID.randomUUID(), UUID.randomUUID(), "feature", "main", "OPEN"));

    verify(deliveryService, never()).deliver(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("onRepoCreated skips delivery when repository row is missing")
  void onRepoCreated_skips_whenRepoNotInDatabase() {
    when(repoRepository.findByOwnerUsernameAndName("alice", "demo")).thenReturn(Optional.empty());

    webhookDispatcher.onRepoCreated(new RepoCreatedEvent("alice", "demo"));

    verify(deliveryService, never()).deliver(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("onRepoCreated delivers REPO_CREATED when repository exists")
  void onRepoCreated_delivers_whenRepoFound() {
    UUID repoId = UUID.randomUUID();
    Repo repo = new Repo();
    repo.setId(repoId);
    Webhook webhook = repoWebhook(repoId, WebhookEventType.REPO_CREATED);
    when(repoRepository.findByOwnerUsernameAndName("alice", "demo")).thenReturn(Optional.of(repo));
    when(webhookRepository.findAllByRepoIdAndEnabledTrue(repoId)).thenReturn(List.of(webhook));

    webhookDispatcher.onRepoCreated(new RepoCreatedEvent("alice", "demo"));

    verify(deliveryService)
        .deliver(
            eq(webhook.getId()),
            eq(webhook.getUrl()),
            any(),
            eq(repoId),
            eq("Webhook"),
            eq(WebhookEventType.REPO_CREATED),
            eq(Map.of("owner", "alice", "name", "demo")));
  }

  @Test
  @DisplayName("onIssueStatusChanged maps OPEN to ISSUE_REOPENED")
  void onIssueStatusChanged_deliversReopened_whenStatusOpen() {
    UUID repoId = UUID.randomUUID();
    Webhook webhook = repoWebhook(repoId, WebhookEventType.ISSUE_REOPENED);
    when(webhookRepository.findAllByRepoIdAndEnabledTrue(repoId)).thenReturn(List.of(webhook));
    UUID issueId = UUID.randomUUID();

    webhookDispatcher.onIssueStatusChanged(new IssueStatusChangedEvent(issueId, repoId, 5, "OPEN"));

    verify(deliveryService)
        .deliver(
            any(),
            any(),
            any(),
            eq(repoId),
            eq("Webhook"),
            eq(WebhookEventType.ISSUE_REOPENED),
            eq(Map.of("issueId", issueId, "number", 5, "status", "OPEN")));
  }

  @Test
  @DisplayName("onIssueStatusChanged maps unknown status to ISSUE_UPDATED")
  void onIssueStatusChanged_deliversUpdated_whenStatusNeitherOpenNorClosed() {
    UUID repoId = UUID.randomUUID();
    Webhook webhook = repoWebhook(repoId, WebhookEventType.ISSUE_UPDATED);
    when(webhookRepository.findAllByRepoIdAndEnabledTrue(repoId)).thenReturn(List.of(webhook));

    webhookDispatcher.onIssueStatusChanged(
        new IssueStatusChangedEvent(UUID.randomUUID(), repoId, 1, "IN_PROGRESS"));

    verify(deliveryService)
        .deliver(
            any(),
            any(),
            any(),
            eq(repoId),
            eq("Webhook"),
            eq(WebhookEventType.ISSUE_UPDATED),
            any());
  }

  @Test
  @DisplayName("repo dispatch skips webhooks not subscribed to the event")
  void dispatch_skips_whenEventNotSubscribed() {
    UUID repoId = UUID.randomUUID();
    Webhook webhook = repoWebhook(repoId, WebhookEventType.REPO_CREATED);
    when(webhookRepository.findAllByRepoIdAndEnabledTrue(repoId)).thenReturn(List.of(webhook));

    webhookDispatcher.onRepoPushed(
        new com.nuricanozturk.originhub.shared.repo.events.RepoPushedEvent(
            repoId, "main", "alice"));

    verify(deliveryService, never()).deliver(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("onProjectCreated delivers to subscribed user webhooks")
  void onProjectCreated_deliversUserWebhook_whenTenantExists() {
    UUID userId = UUID.randomUUID();
    Tenant tenant = new Tenant();
    tenant.setId(userId);
    tenant.setUsername("alice");
    UserWebhook webhook = userWebhook(userId, WebhookEventType.PROJECT_CREATED);
    when(tenantRepository.findByUsername("alice")).thenReturn(Optional.of(tenant));
    when(userWebhookRepository.findAllByUserIdAndEnabledTrue(userId)).thenReturn(List.of(webhook));
    UUID projectId = UUID.randomUUID();

    webhookDispatcher.onProjectCreated(new ProjectCreatedEvent(projectId, "alice", "My App"));

    verify(deliveryService)
        .deliver(
            eq(webhook.getId()),
            eq(webhook.getUrl()),
            any(),
            isNull(),
            eq("User webhook"),
            eq(WebhookEventType.PROJECT_CREATED),
            eq(Map.of("projectId", projectId, "name", "My App", "owner", "alice")));
  }

  @Test
  @DisplayName("onTaskCreated delivers to subscribed project webhooks")
  void onTaskCreated_deliversProjectWebhook_whenSubscribed() {
    UUID projectId = UUID.randomUUID();
    ProjectWebhook webhook = projectWebhook(projectId, WebhookEventType.TASK_CREATED);
    when(projectWebhookRepository.findAllByProjectIdAndEnabledTrue(projectId))
        .thenReturn(List.of(webhook));
    UUID taskId = UUID.randomUUID();

    webhookDispatcher.onTaskCreated(new TaskCreatedEvent(taskId, projectId, "APP-1", "alice"));

    verify(deliveryService)
        .deliver(
            eq(webhook.getId()),
            eq(webhook.getUrl()),
            any(),
            isNull(),
            eq("Project webhook"),
            eq(WebhookEventType.TASK_CREATED),
            eq(
                Map.of(
                    "taskId", taskId, "code", "APP-1", "projectId", projectId, "owner", "alice")));
  }

  @Test
  @DisplayName("onSnippetCreated includes repoId in payload when linked to a repository")
  void onSnippetCreated_includesRepoId_whenSnippetLinkedToRepo() {
    UUID userId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    UUID snippetId = UUID.randomUUID();
    Tenant tenant = new Tenant();
    tenant.setId(userId);
    tenant.setUsername("alice");
    UserWebhook webhook = userWebhook(userId, WebhookEventType.SNIPPET_CREATED);
    when(tenantRepository.findByUsername("alice")).thenReturn(Optional.of(tenant));
    when(userWebhookRepository.findAllByUserIdAndEnabledTrue(userId)).thenReturn(List.of(webhook));

    webhookDispatcher.onSnippetCreated(
        new SnippetCreatedEvent(snippetId, "alice", "Helper", repoId));

    verify(deliveryService)
        .deliver(
            eq(webhook.getId()),
            eq(webhook.getUrl()),
            any(),
            isNull(),
            eq("User webhook"),
            eq(WebhookEventType.SNIPPET_CREATED),
            eq(
                Map.of(
                    "snippetId",
                    snippetId,
                    "title",
                    "Helper",
                    "owner",
                    "alice",
                    "repoId",
                    repoId)));
  }

  private static Webhook repoWebhook(UUID repoId, WebhookEventType event) {
    Webhook webhook = new Webhook();
    webhook.setId(UUID.randomUUID());
    webhook.setRepoId(repoId);
    webhook.setUrl("https://hook.test/repo");
    webhook.setSecret("secret");
    webhook.setEnabled(true);
    webhook.setSubscribedEvents(Set.of(event.name()));
    return webhook;
  }

  private static UserWebhook userWebhook(UUID userId, WebhookEventType event) {
    UserWebhook webhook = new UserWebhook();
    webhook.setId(UUID.randomUUID());
    webhook.setUserId(userId);
    webhook.setUrl("https://hook.test/user");
    webhook.setEnabled(true);
    webhook.setSubscribedEvents(Set.of(event.name()));
    return webhook;
  }

  private static ProjectWebhook projectWebhook(UUID projectId, WebhookEventType event) {
    ProjectWebhook webhook = new ProjectWebhook();
    webhook.setId(UUID.randomUUID());
    webhook.setProjectId(projectId);
    webhook.setUrl("https://hook.test/project");
    webhook.setEnabled(true);
    webhook.setSubscribedEvents(Set.of(event.name()));
    return webhook;
  }
}
