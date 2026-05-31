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
import com.nuricanozturk.originhub.shared.project.dtos.ProjectSummary;
import com.nuricanozturk.originhub.shared.project.services.ProjectAccessService;
import com.nuricanozturk.originhub.webhook.dtos.WebhookForm;
import com.nuricanozturk.originhub.webhook.dtos.WebhookInfo;
import com.nuricanozturk.originhub.webhook.dtos.WebhookUpdateForm;
import com.nuricanozturk.originhub.webhook.entities.ProjectWebhook;
import com.nuricanozturk.originhub.webhook.mappers.WebhookMapper;
import com.nuricanozturk.originhub.webhook.repositories.ProjectWebhookRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectWebhookService unit tests")
class ProjectWebhookServiceTest {

  private static final String OWNER = "alice";
  private static final String PROJECT_CODE = "PROJ";
  private static final Set<String> VALID_EVENTS = Set.of("TASK_CREATED");

  @Mock private ProjectWebhookRepository projectWebhookRepository;
  @Mock private ProjectAccessService projectAccessService;
  @Mock private WebhookMapper webhookMapper;

  @InjectMocks private ProjectWebhookService service;

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private static ProjectSummary summary(UUID id, String ownerUsername) {
    return new ProjectSummary(id, ownerUsername);
  }

  private static ProjectWebhook webhook(UUID id, UUID projectId) {
    var w = new ProjectWebhook();
    w.setId(id);
    w.setProjectId(projectId);
    w.setUrl("https://example.com/hook");
    w.setEnabled(true);
    w.setSubscribedEvents(new HashSet<>(VALID_EVENTS));
    return w;
  }

  private static WebhookInfo info(UUID id) {
    return WebhookInfo.builder()
        .id(id)
        .url("https://example.com/hook")
        .enabled(true)
        .events(VALID_EVENTS)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
  }

  // ─── list ─────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("list")
  class ListTests {

    @Test
    @DisplayName("returns mapped webhook infos when project exists")
    void returnsWebhookInfos_whenProjectExists() {
      UUID projectId = UUID.randomUUID();
      UUID webhookId = UUID.randomUUID();
      var webhook = webhook(webhookId, projectId);
      var expected = info(webhookId);

      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.of(summary(projectId, OWNER)));
      when(projectWebhookRepository.findAllByProjectId(projectId)).thenReturn(List.of(webhook));
      when(webhookMapper.toInfoFromProject(webhook)).thenReturn(expected);

      var result = service.list(OWNER, PROJECT_CODE);

      assertThat(result).hasSize(1).containsExactly(expected);
    }

    @Test
    @DisplayName("returns empty list when project has no webhooks")
    void returnsEmpty_whenNoWebhooks() {
      UUID projectId = UUID.randomUUID();
      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.of(summary(projectId, OWNER)));
      when(projectWebhookRepository.findAllByProjectId(projectId)).thenReturn(List.of());

      assertThat(service.list(OWNER, PROJECT_CODE)).isEmpty();
    }

    @Test
    @DisplayName("throws ItemNotFoundException when project not found")
    void throws_whenProjectNotFound() {
      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.list(OWNER, PROJECT_CODE))
          .isInstanceOf(ItemNotFoundException.class);
    }
  }

  // ─── create ───────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("create")
  class CreateTests {

    private WebhookForm form(String url, Set<String> events) {
      return new WebhookForm(url, null, true, events);
    }

    @Test
    @DisplayName("creates and returns webhook when all conditions met")
    void creates_whenValid() {
      UUID projectId = UUID.randomUUID();
      UUID webhookId = UUID.randomUUID();
      var saved = webhook(webhookId, projectId);
      var expected = info(webhookId);

      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.of(summary(projectId, OWNER)));
      when(projectWebhookRepository.countByProjectId(projectId)).thenReturn(0);
      when(projectWebhookRepository.existsByProjectIdAndUrl(projectId, "https://example.com/hook"))
          .thenReturn(false);
      when(projectWebhookRepository.save(any(ProjectWebhook.class))).thenReturn(saved);
      when(webhookMapper.toInfoFromProject(saved)).thenReturn(expected);

      var result =
          service.create(OWNER, PROJECT_CODE, form("https://example.com/hook", VALID_EVENTS));

      assertThat(result).isEqualTo(expected);

      var captor = ArgumentCaptor.forClass(ProjectWebhook.class);
      verify(projectWebhookRepository).save(captor.capture());
      var persisted = captor.getValue();
      assertThat(persisted.getProjectId()).isEqualTo(projectId);
      assertThat(persisted.getUrl()).isEqualTo("https://example.com/hook");
      assertThat(persisted.isEnabled()).isTrue();
      assertThat(persisted.getSubscribedEvents()).containsExactlyInAnyOrderElementsOf(VALID_EVENTS);
    }

    @Test
    @DisplayName("throws AccessNotAllowedException when caller is not the project owner")
    void throws_whenCallerNotOwner() {
      UUID projectId = UUID.randomUUID();
      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.of(summary(projectId, "bob")));

      assertThatThrownBy(
              () -> service.create(OWNER, PROJECT_CODE, form("https://x.com", VALID_EVENTS)))
          .isInstanceOf(AccessNotAllowedException.class);
      verify(projectWebhookRepository, never()).save(any());
    }

    @Test
    @DisplayName("throws ErrorOccurredException when webhook limit is reached")
    void throws_whenMaxLimitReached() {
      UUID projectId = UUID.randomUUID();
      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.of(summary(projectId, OWNER)));
      when(projectWebhookRepository.countByProjectId(projectId)).thenReturn(3);

      assertThatThrownBy(
              () -> service.create(OWNER, PROJECT_CODE, form("https://x.com", VALID_EVENTS)))
          .isInstanceOf(ErrorOccurredException.class)
          .hasMessageContaining("Maximum");
      verify(projectWebhookRepository, never()).save(any());
    }

    @Test
    @DisplayName("throws ErrorOccurredException when URL already registered for project")
    void throws_whenDuplicateUrl() {
      UUID projectId = UUID.randomUUID();
      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.of(summary(projectId, OWNER)));
      when(projectWebhookRepository.countByProjectId(projectId)).thenReturn(1);
      when(projectWebhookRepository.existsByProjectIdAndUrl(projectId, "https://dup.com"))
          .thenReturn(true);

      assertThatThrownBy(
              () -> service.create(OWNER, PROJECT_CODE, form("https://dup.com", VALID_EVENTS)))
          .isInstanceOf(ErrorOccurredException.class)
          .hasMessageContaining("already exists");
      verify(projectWebhookRepository, never()).save(any());
    }

    @Test
    @DisplayName("throws ErrorOccurredException when events contain invalid type")
    void throws_whenInvalidEvents() {
      UUID projectId = UUID.randomUUID();
      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.of(summary(projectId, OWNER)));
      when(projectWebhookRepository.countByProjectId(projectId)).thenReturn(0);
      when(projectWebhookRepository.existsByProjectIdAndUrl(any(), any())).thenReturn(false);

      assertThatThrownBy(
              () ->
                  service.create(
                      OWNER,
                      PROJECT_CODE,
                      form("https://x.com", Set.of("REPO_PUSHED", "TASK_CREATED"))))
          .isInstanceOf(ErrorOccurredException.class)
          .hasMessageContaining("Invalid event");
      verify(projectWebhookRepository, never()).save(any());
    }

    @Test
    @DisplayName("throws ErrorOccurredException when all events are invalid")
    void throws_whenAllEventsInvalid() {
      UUID projectId = UUID.randomUUID();
      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.of(summary(projectId, OWNER)));
      when(projectWebhookRepository.countByProjectId(projectId)).thenReturn(0);
      when(projectWebhookRepository.existsByProjectIdAndUrl(any(), any())).thenReturn(false);

      assertThatThrownBy(
              () ->
                  service.create(
                      OWNER,
                      PROJECT_CODE,
                      form("https://x.com", Set.of("SNIPPET_CREATED", "ISSUE_OPENED"))))
          .isInstanceOf(ErrorOccurredException.class);
    }

    @Test
    @DisplayName("throws ItemNotFoundException when project not found")
    void throws_whenProjectNotFound() {
      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.empty());

      assertThatThrownBy(
              () -> service.create(OWNER, PROJECT_CODE, form("https://x.com", VALID_EVENTS)))
          .isInstanceOf(ItemNotFoundException.class);
    }
  }

  // ─── update ───────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("update")
  class UpdateTests {

    @Test
    @DisplayName("updates all provided fields when valid")
    void updates_whenValid() {
      UUID projectId = UUID.randomUUID();
      UUID webhookId = UUID.randomUUID();
      var existing = webhook(webhookId, projectId);
      existing.setUrl("https://old.com");
      var saved = webhook(webhookId, projectId);
      var expected = info(webhookId);

      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.of(summary(projectId, OWNER)));
      when(projectWebhookRepository.findById(webhookId)).thenReturn(Optional.of(existing));
      when(projectWebhookRepository.existsByProjectIdAndUrl(projectId, "https://new.com"))
          .thenReturn(false);
      when(projectWebhookRepository.save(any(ProjectWebhook.class))).thenReturn(saved);
      when(webhookMapper.toInfoFromProject(saved)).thenReturn(expected);

      var form =
          new WebhookUpdateForm("https://new.com", "mysecret", false, Set.of("TASK_UPDATED"));
      var result = service.update(OWNER, PROJECT_CODE, webhookId, form);

      assertThat(result).isEqualTo(expected);
      assertThat(existing.getUrl()).isEqualTo("https://new.com");
      assertThat(existing.getSecret()).isEqualTo("mysecret");
      assertThat(existing.isEnabled()).isFalse();
      assertThat(existing.getSubscribedEvents()).containsExactly("TASK_UPDATED");
    }

    @Test
    @DisplayName("skips URL conflict check when URL is unchanged")
    void skipsConflictCheck_whenUrlUnchanged() {
      UUID projectId = UUID.randomUUID();
      UUID webhookId = UUID.randomUUID();
      var existing = webhook(webhookId, projectId);
      existing.setUrl("https://same.com");

      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.of(summary(projectId, OWNER)));
      when(projectWebhookRepository.findById(webhookId)).thenReturn(Optional.of(existing));
      when(projectWebhookRepository.save(any(ProjectWebhook.class))).thenReturn(existing);
      when(webhookMapper.toInfoFromProject(existing)).thenReturn(info(webhookId));

      var form = new WebhookUpdateForm("https://same.com", null, null, null);
      service.update(OWNER, PROJECT_CODE, webhookId, form);

      verify(projectWebhookRepository, never()).existsByProjectIdAndUrl(any(), any());
    }

    @Test
    @DisplayName("throws ErrorOccurredException when new URL conflicts with existing webhook")
    void throws_whenNewUrlConflicts() {
      UUID projectId = UUID.randomUUID();
      UUID webhookId = UUID.randomUUID();
      var existing = webhook(webhookId, projectId);
      existing.setUrl("https://old.com");

      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.of(summary(projectId, OWNER)));
      when(projectWebhookRepository.findById(webhookId)).thenReturn(Optional.of(existing));
      when(projectWebhookRepository.existsByProjectIdAndUrl(projectId, "https://conflict.com"))
          .thenReturn(true);

      var form = new WebhookUpdateForm("https://conflict.com", null, null, null);
      assertThatThrownBy(() -> service.update(OWNER, PROJECT_CODE, webhookId, form))
          .isInstanceOf(ErrorOccurredException.class)
          .hasMessageContaining("already exists");
      verify(projectWebhookRepository, never()).save(any());
    }

    @Test
    @DisplayName("sets secret to null when blank string is passed")
    void setsSecretToNull_whenSecretIsBlank() {
      UUID projectId = UUID.randomUUID();
      UUID webhookId = UUID.randomUUID();
      var existing = webhook(webhookId, projectId);
      existing.setSecret("old-secret");

      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.of(summary(projectId, OWNER)));
      when(projectWebhookRepository.findById(webhookId)).thenReturn(Optional.of(existing));
      when(projectWebhookRepository.save(any(ProjectWebhook.class))).thenReturn(existing);
      when(webhookMapper.toInfoFromProject(existing)).thenReturn(info(webhookId));

      var form = new WebhookUpdateForm(null, "   ", null, null);
      service.update(OWNER, PROJECT_CODE, webhookId, form);

      assertThat(existing.getSecret()).isNull();
    }

    @Test
    @DisplayName("throws AccessNotAllowedException when caller is not project owner")
    void throws_whenCallerNotOwner() {
      UUID projectId = UUID.randomUUID();
      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.of(summary(projectId, "bob")));

      assertThatThrownBy(
              () ->
                  service.update(
                      OWNER,
                      PROJECT_CODE,
                      UUID.randomUUID(),
                      new WebhookUpdateForm(null, null, null, null)))
          .isInstanceOf(AccessNotAllowedException.class);
      verify(projectWebhookRepository, never()).save(any());
    }

    @Test
    @DisplayName("throws AccessNotAllowedException when webhook belongs to a different project")
    void throws_whenWebhookBelongsToDifferentProject() {
      UUID projectId = UUID.randomUUID();
      UUID otherProjectId = UUID.randomUUID();
      UUID webhookId = UUID.randomUUID();
      var foreignWebhook = webhook(webhookId, otherProjectId);

      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.of(summary(projectId, OWNER)));
      when(projectWebhookRepository.findById(webhookId)).thenReturn(Optional.of(foreignWebhook));

      assertThatThrownBy(
              () ->
                  service.update(
                      OWNER,
                      PROJECT_CODE,
                      webhookId,
                      new WebhookUpdateForm(null, null, null, null)))
          .isInstanceOf(AccessNotAllowedException.class);
      verify(projectWebhookRepository, never()).save(any());
    }

    @Test
    @DisplayName("throws ItemNotFoundException when webhook not found")
    void throws_whenWebhookNotFound() {
      UUID projectId = UUID.randomUUID();
      UUID webhookId = UUID.randomUUID();

      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.of(summary(projectId, OWNER)));
      when(projectWebhookRepository.findById(webhookId)).thenReturn(Optional.empty());

      assertThatThrownBy(
              () ->
                  service.update(
                      OWNER,
                      PROJECT_CODE,
                      webhookId,
                      new WebhookUpdateForm(null, null, null, null)))
          .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    @DisplayName("throws ItemNotFoundException when project not found")
    void throws_whenProjectNotFound() {
      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.empty());

      assertThatThrownBy(
              () ->
                  service.update(
                      OWNER,
                      PROJECT_CODE,
                      UUID.randomUUID(),
                      new WebhookUpdateForm(null, null, null, null)))
          .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    @DisplayName("throws ErrorOccurredException when updated events contain invalid type")
    void throws_whenInvalidEvents() {
      UUID projectId = UUID.randomUUID();
      UUID webhookId = UUID.randomUUID();
      var existing = webhook(webhookId, projectId);

      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.of(summary(projectId, OWNER)));
      when(projectWebhookRepository.findById(webhookId)).thenReturn(Optional.of(existing));

      var form = new WebhookUpdateForm(null, null, null, Set.of("REPO_PUSHED"));
      assertThatThrownBy(() -> service.update(OWNER, PROJECT_CODE, webhookId, form))
          .isInstanceOf(ErrorOccurredException.class)
          .hasMessageContaining("Invalid event");
      verify(projectWebhookRepository, never()).save(any());
    }
  }

  // ─── delete ───────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("delete")
  class DeleteTests {

    @Test
    @DisplayName("deletes webhook when caller is owner and webhook belongs to project")
    void deletes_whenValid() {
      UUID projectId = UUID.randomUUID();
      UUID webhookId = UUID.randomUUID();
      var existing = webhook(webhookId, projectId);

      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.of(summary(projectId, OWNER)));
      when(projectWebhookRepository.findById(webhookId)).thenReturn(Optional.of(existing));

      service.delete(OWNER, PROJECT_CODE, webhookId);

      verify(projectWebhookRepository).delete(existing);
    }

    @Test
    @DisplayName("throws AccessNotAllowedException when caller is not project owner")
    void throws_whenCallerNotOwner() {
      UUID projectId = UUID.randomUUID();
      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.of(summary(projectId, "bob")));

      assertThatThrownBy(() -> service.delete(OWNER, PROJECT_CODE, UUID.randomUUID()))
          .isInstanceOf(AccessNotAllowedException.class);
      verify(projectWebhookRepository, never()).delete(any());
    }

    @Test
    @DisplayName("throws ItemNotFoundException when webhook not found")
    void throws_whenWebhookNotFound() {
      UUID projectId = UUID.randomUUID();
      UUID webhookId = UUID.randomUUID();

      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.of(summary(projectId, OWNER)));
      when(projectWebhookRepository.findById(webhookId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.delete(OWNER, PROJECT_CODE, webhookId))
          .isInstanceOf(ItemNotFoundException.class);
      verify(projectWebhookRepository, never()).delete(any());
    }

    @Test
    @DisplayName("throws AccessNotAllowedException when webhook belongs to a different project")
    void throws_whenWebhookBelongsToDifferentProject() {
      UUID projectId = UUID.randomUUID();
      UUID otherProjectId = UUID.randomUUID();
      UUID webhookId = UUID.randomUUID();
      var foreignWebhook = webhook(webhookId, otherProjectId);

      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.of(summary(projectId, OWNER)));
      when(projectWebhookRepository.findById(webhookId)).thenReturn(Optional.of(foreignWebhook));

      assertThatThrownBy(() -> service.delete(OWNER, PROJECT_CODE, webhookId))
          .isInstanceOf(AccessNotAllowedException.class);
      verify(projectWebhookRepository, never()).delete(any());
    }

    @Test
    @DisplayName("throws ItemNotFoundException when project not found")
    void throws_whenProjectNotFound() {
      when(projectAccessService.findByOwnerAndCode(OWNER, PROJECT_CODE))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.delete(OWNER, PROJECT_CODE, UUID.randomUUID()))
          .isInstanceOf(ItemNotFoundException.class);
      verify(projectWebhookRepository, never()).delete(any());
    }
  }
}
