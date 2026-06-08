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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import dev.gitnode.os.webhook.dtos.WebhookForm;
import dev.gitnode.os.webhook.dtos.WebhookInfo;
import dev.gitnode.os.webhook.dtos.WebhookUpdateForm;
import dev.gitnode.os.webhook.entities.UserWebhook;
import dev.gitnode.os.webhook.entities.WebhookEventType;
import dev.gitnode.os.webhook.mappers.WebhookMapper;
import dev.gitnode.os.webhook.repositories.UserWebhookRepository;
import dev.gitnode.os.webhook.utils.WebhookValidator;
import java.time.Instant;
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
@DisplayName("UserWebhookService unit tests")
class UserWebhookServiceTest {

  private static final Set<String> VALID_USER_EVENTS =
      Set.of(WebhookEventType.PROJECT_CREATED.name());

  @Mock private UserWebhookRepository userWebhookRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private WebhookMapper webhookMapper;

  @InjectMocks private UserWebhookService userWebhookService;

  @Test
  @DisplayName("create throws ItemNotFoundException when user does not exist")
  void create_throws_whenUserMissing() {
    WebhookForm form = new WebhookForm("https://hook.test", null, true, VALID_USER_EVENTS);
    when(tenantRepository.findByUsername("ghost")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userWebhookService.create("ghost", form))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("User not found");
  }

  @Test
  @DisplayName("create throws ErrorOccurredException when max user webhooks reached")
  void create_throws_whenMaxReached() {
    UUID userId = UUID.randomUUID();
    stubUser(userId);
    when(userWebhookRepository.countByUserId(userId)).thenReturn(WebhookValidator.MAX_WEBHOOKS);
    WebhookForm form = new WebhookForm("https://hook.test", null, true, VALID_USER_EVENTS);

    assertThatThrownBy(() -> userWebhookService.create("alice", form))
        .isInstanceOf(ErrorOccurredException.class)
        .hasMessageContaining("Maximum of");
  }

  @Test
  @DisplayName("create throws ErrorOccurredException for invalid user event types")
  void create_throws_whenInvalidEvents() {
    UUID userId = UUID.randomUUID();
    stubUser(userId);
    when(userWebhookRepository.countByUserId(userId)).thenReturn(0);
    WebhookForm form =
        new WebhookForm(
            "https://hook.test", null, true, Set.of(WebhookEventType.REPO_CREATED.name()));

    assertThatThrownBy(() -> userWebhookService.create("alice", form))
        .isInstanceOf(ErrorOccurredException.class)
        .hasMessageContaining("Invalid event types");
  }

  @Test
  @DisplayName("create saves user webhook when valid")
  void create_savesWebhook_whenValid() {
    UUID userId = UUID.randomUUID();
    stubUser(userId);
    when(userWebhookRepository.countByUserId(userId)).thenReturn(0);
    when(userWebhookRepository.existsByUserIdAndUrl(userId, "https://hook.test")).thenReturn(false);
    UserWebhook saved = new UserWebhook();
    saved.setId(UUID.randomUUID());
    when(userWebhookRepository.save(any(UserWebhook.class))).thenReturn(saved);
    WebhookInfo info =
        WebhookInfo.builder()
            .id(saved.getId())
            .url("https://hook.test")
            .enabled(true)
            .events(VALID_USER_EVENTS)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    when(webhookMapper.toInfoFromUser(saved)).thenReturn(info);
    WebhookForm form = new WebhookForm("https://hook.test", null, true, VALID_USER_EVENTS);

    WebhookInfo result = userWebhookService.create("alice", form);

    assertThat(result.url()).isEqualTo("https://hook.test");
  }

  @Test
  @DisplayName("update throws AccessNotAllowedException when webhook belongs to another user")
  void update_throws_whenWrongUser() {
    UUID userId = UUID.randomUUID();
    stubUser(userId);
    UserWebhook webhook = new UserWebhook();
    webhook.setId(UUID.randomUUID());
    webhook.setUserId(UUID.randomUUID());
    when(userWebhookRepository.findById(webhook.getId())).thenReturn(Optional.of(webhook));
    WebhookUpdateForm form = new WebhookUpdateForm(null, null, null, null);

    assertThatThrownBy(() -> userWebhookService.update("alice", webhook.getId(), form))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining(WebhookValidator.ERR_NOT_AUTHORIZED);
  }

  @Test
  @DisplayName("delete removes webhook for owning user")
  void delete_removesWebhook_whenOwnerMatches() {
    UUID userId = UUID.randomUUID();
    stubUser(userId);
    UserWebhook webhook = new UserWebhook();
    webhook.setId(UUID.randomUUID());
    webhook.setUserId(userId);
    when(userWebhookRepository.findById(webhook.getId())).thenReturn(Optional.of(webhook));

    userWebhookService.delete("alice", webhook.getId());

    verify(userWebhookRepository).delete(webhook);
  }

  private void stubUser(UUID userId) {
    Tenant tenant = new Tenant();
    tenant.setId(userId);
    tenant.setUsername("alice");
    when(tenantRepository.findByUsername("alice")).thenReturn(Optional.of(tenant));
  }
}
