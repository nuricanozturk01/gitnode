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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.shared.errorhandling.exceptions.AccessNotAllowedException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.repo.services.RepoService;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.webhook.dtos.WebhookForm;
import com.nuricanozturk.originhub.webhook.dtos.WebhookInfo;
import com.nuricanozturk.originhub.webhook.dtos.WebhookUpdateForm;
import com.nuricanozturk.originhub.webhook.entities.Webhook;
import com.nuricanozturk.originhub.webhook.entities.WebhookEventType;
import com.nuricanozturk.originhub.webhook.mappers.WebhookMapper;
import com.nuricanozturk.originhub.webhook.repositories.WebhookRepository;
import com.nuricanozturk.originhub.webhook.utils.WebhookValidator;
import java.time.Instant;
import java.util.HashSet;
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
@DisplayName("WebhookService unit tests")
class WebhookServiceTest {

  private static final String OWNER = "alice";
  private static final String REPO_NAME = "demo";
  private static final UUID REQUESTER_ID = UUID.randomUUID();
  private static final Set<String> VALID_EVENTS = Set.of(WebhookEventType.REPO_CREATED.name());

  @Mock private WebhookRepository webhookRepository;
  @Mock private RepoRepository repoRepository;
  @Mock private RepoService repoService;
  @Mock private WebhookMapper webhookMapper;

  @InjectMocks private WebhookService webhookService;

  @Test
  @DisplayName("create throws ItemNotFoundException when repository does not exist")
  void create_throws_whenRepoMissing() {
    WebhookForm form = new WebhookForm("https://hook.test", null, true, VALID_EVENTS);
    when(repoRepository.findByOwnerUsernameAndName(OWNER, "missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> webhookService.create(REQUESTER_ID, OWNER, "missing", form))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("Repository not found");
  }

  @Test
  @DisplayName("create throws ErrorOccurredException when max webhooks reached")
  void create_throws_whenMaxWebhooksReached() {
    UUID repoId = UUID.randomUUID();
    stubRepo(repoId);
    when(webhookRepository.countByRepoId(repoId)).thenReturn(WebhookValidator.MAX_WEBHOOKS);
    WebhookForm form = new WebhookForm("https://hook.test", null, true, VALID_EVENTS);

    assertThatThrownBy(() -> webhookService.create(REQUESTER_ID, OWNER, REPO_NAME, form))
        .isInstanceOf(ErrorOccurredException.class)
        .hasMessageContaining("Maximum of");
  }

  @Test
  @DisplayName("create throws ErrorOccurredException when URL already exists")
  void create_throws_whenUrlExists() {
    UUID repoId = UUID.randomUUID();
    stubRepo(repoId);
    when(webhookRepository.countByRepoId(repoId)).thenReturn(0);
    when(webhookRepository.existsByRepoIdAndUrl(repoId, "https://hook.test")).thenReturn(true);
    WebhookForm form = new WebhookForm("https://hook.test", null, true, VALID_EVENTS);

    assertThatThrownBy(() -> webhookService.create(REQUESTER_ID, OWNER, REPO_NAME, form))
        .isInstanceOf(ErrorOccurredException.class)
        .hasMessageContaining(WebhookValidator.ERR_URL_EXISTS);
  }

  @Test
  @DisplayName("create throws ErrorOccurredException for invalid event types")
  void create_throws_whenInvalidEvents() {
    UUID repoId = UUID.randomUUID();
    stubRepo(repoId);
    when(webhookRepository.countByRepoId(repoId)).thenReturn(0);
    WebhookForm form = new WebhookForm("https://hook.test", null, true, Set.of("NOT_A_REAL_EVENT"));

    assertThatThrownBy(() -> webhookService.create(REQUESTER_ID, OWNER, REPO_NAME, form))
        .isInstanceOf(ErrorOccurredException.class)
        .hasMessageContaining("Invalid event types");
  }

  @Test
  @DisplayName("create saves webhook and returns mapped info")
  void create_returnsInfo_whenValid() {
    UUID repoId = UUID.randomUUID();
    stubRepo(repoId);
    when(webhookRepository.countByRepoId(repoId)).thenReturn(0);
    when(webhookRepository.existsByRepoIdAndUrl(repoId, "https://hook.test")).thenReturn(false);
    Webhook saved = new Webhook();
    saved.setId(UUID.randomUUID());
    when(webhookRepository.save(any(Webhook.class))).thenReturn(saved);
    WebhookInfo info =
        WebhookInfo.builder()
            .id(saved.getId())
            .url("https://hook.test")
            .enabled(true)
            .events(VALID_EVENTS)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    when(webhookMapper.toInfo(saved)).thenReturn(info);
    WebhookForm form = new WebhookForm("https://hook.test", "secret", true, VALID_EVENTS);

    WebhookInfo result = webhookService.create(REQUESTER_ID, OWNER, REPO_NAME, form);

    assertThat(result.url()).isEqualTo("https://hook.test");
  }

  @Test
  @DisplayName("update throws AccessNotAllowedException when webhook belongs to another repo")
  void update_throws_whenWebhookWrongRepo() {
    UUID repoId = UUID.randomUUID();
    UUID otherRepoId = UUID.randomUUID();
    stubRepo(repoId);
    Webhook webhook = new Webhook();
    webhook.setId(UUID.randomUUID());
    webhook.setRepoId(otherRepoId);
    when(webhookRepository.findById(webhook.getId())).thenReturn(Optional.of(webhook));
    WebhookUpdateForm form = new WebhookUpdateForm(null, null, null, null);

    assertThatThrownBy(
            () -> webhookService.update(REQUESTER_ID, OWNER, REPO_NAME, webhook.getId(), form))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining(WebhookValidator.ERR_NOT_AUTHORIZED);
  }

  @Test
  @DisplayName("delete removes webhook when scoped correctly")
  void delete_removesWebhook_whenFound() {
    UUID repoId = UUID.randomUUID();
    stubRepo(repoId);
    Webhook webhook = new Webhook();
    webhook.setId(UUID.randomUUID());
    webhook.setRepoId(repoId);
    when(webhookRepository.findById(webhook.getId())).thenReturn(Optional.of(webhook));

    webhookService.delete(REQUESTER_ID, OWNER, REPO_NAME, webhook.getId());

    verify(webhookRepository).delete(webhook);
  }

  @Test
  @DisplayName("list returns mapped webhooks for repository")
  void list_returnsWebhooks_whenRepoExists() {
    UUID repoId = UUID.randomUUID();
    stubRepo(repoId);
    Webhook webhook = new Webhook();
    webhook.setSubscribedEvents(new HashSet<>(VALID_EVENTS));
    when(webhookRepository.findAllByRepoId(repoId)).thenReturn(java.util.List.of(webhook));
    WebhookInfo info =
        WebhookInfo.builder()
            .id(UUID.randomUUID())
            .url("https://hook.test")
            .enabled(true)
            .events(VALID_EVENTS)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    when(webhookMapper.toInfo(webhook)).thenReturn(info);

    var result = webhookService.list(REQUESTER_ID, OWNER, REPO_NAME);

    assertThat(result).hasSize(1);
    verify(webhookRepository, never()).save(any());
  }

  private void stubRepo(UUID repoId) {
    Repo repo = new Repo();
    repo.setId(repoId);
    Tenant owner = new Tenant();
    owner.setUsername(OWNER);
    repo.setOwner(owner);
    when(repoRepository.findByOwnerUsernameAndName(OWNER, REPO_NAME)).thenReturn(Optional.of(repo));
  }
}
