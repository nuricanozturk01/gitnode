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

import com.nuricanozturk.originhub.shared.errorhandling.exceptions.AccessNotAllowedException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import com.nuricanozturk.originhub.webhook.dtos.WebhookForm;
import com.nuricanozturk.originhub.webhook.dtos.WebhookInfo;
import com.nuricanozturk.originhub.webhook.dtos.WebhookUpdateForm;
import com.nuricanozturk.originhub.webhook.entities.UserWebhook;
import com.nuricanozturk.originhub.webhook.entities.WebhookEventType;
import com.nuricanozturk.originhub.webhook.mappers.WebhookMapper;
import com.nuricanozturk.originhub.webhook.repositories.UserWebhookRepository;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
@NullMarked
public class UserWebhookService {

  private static final int MAX_WEBHOOKS_PER_USER = 3;

  private static final Set<String> USER_VALID_EVENTS =
      EnumSet.of(
              WebhookEventType.PROJECT_CREATED,
              WebhookEventType.PROJECT_DELETED,
              WebhookEventType.PROJECT_UPDATED,
              WebhookEventType.SNIPPET_CREATED,
              WebhookEventType.SNIPPET_DELETED,
              WebhookEventType.SNIPPET_UPDATED)
          .stream()
          .map(Enum::name)
          .collect(Collectors.toUnmodifiableSet());

  private final UserWebhookRepository userWebhookRepository;
  private final TenantRepository tenantRepository;
  private final WebhookMapper webhookMapper;

  public List<WebhookInfo> list(final String username) {
    final var userId = this.resolveUserId(username);
    return this.userWebhookRepository.findAllByUserId(userId).stream()
        .map(this.webhookMapper::toInfoFromUser)
        .toList();
  }

  @Transactional
  public WebhookInfo create(final String username, final WebhookForm form) {
    final var userId = this.resolveUserId(username);
    if (this.userWebhookRepository.countByUserId(userId) >= MAX_WEBHOOKS_PER_USER) {
      throw new ErrorOccurredException(
          "Maximum of " + MAX_WEBHOOKS_PER_USER + " webhooks per user allowed");
    }
    if (this.userWebhookRepository.existsByUserIdAndUrl(userId, form.url())) {
      throw new ErrorOccurredException("Webhook with this URL already exists");
    }
    this.validateEvents(form.events());

    final var webhook = new UserWebhook();
    webhook.setUserId(userId);
    webhook.setUrl(form.url());
    webhook.setSecret(form.secret());
    webhook.setEnabled(form.enabled());
    webhook.setSubscribedEvents(new HashSet<>(form.events()));

    return this.webhookMapper.toInfoFromUser(this.userWebhookRepository.save(webhook));
  }

  @Transactional
  public WebhookInfo update(
      final String username, final UUID webhookId, final WebhookUpdateForm form) {
    final var userId = this.resolveUserId(username);
    final var webhook = this.findWebhook(webhookId, userId);

    if (form.url() != null) {
      this.applyUrlUpdate(webhook, userId, form.url());
    }
    if (form.secret() != null) {
      webhook.setSecret(form.secret().isBlank() ? null : form.secret());
    }
    if (form.enabled() != null) {
      webhook.setEnabled(form.enabled());
    }
    if (form.events() != null) {
      this.validateEvents(form.events());
      webhook.setSubscribedEvents(new HashSet<>(form.events()));
    }

    return this.webhookMapper.toInfoFromUser(this.userWebhookRepository.save(webhook));
  }

  @Transactional
  public void delete(final String username, final UUID webhookId) {
    final var userId = this.resolveUserId(username);
    final var webhook = this.findWebhook(webhookId, userId);
    this.userWebhookRepository.delete(webhook);
  }

  private void applyUrlUpdate(final UserWebhook webhook, final UUID userId, final String newUrl) {
    if (!newUrl.equals(webhook.getUrl())
        && this.userWebhookRepository.existsByUserIdAndUrl(userId, newUrl)) {
      throw new ErrorOccurredException("Webhook with this URL already exists");
    }
    webhook.setUrl(newUrl);
  }

  private UUID resolveUserId(final String username) {
    return this.tenantRepository
        .findByUsername(username)
        .map(t -> t.getId())
        .orElseThrow(() -> new ItemNotFoundException("User not found"));
  }

  private UserWebhook findWebhook(final UUID webhookId, final UUID userId) {
    final var webhook =
        this.userWebhookRepository
            .findById(webhookId)
            .orElseThrow(() -> new ItemNotFoundException("Webhook not found"));
    if (!userId.equals(webhook.getUserId())) {
      throw new AccessNotAllowedException("notAuthorized");
    }
    return webhook;
  }

  private void validateEvents(final Set<String> events) {
    final var invalid = events.stream().filter(e -> !USER_VALID_EVENTS.contains(e)).toList();
    if (!invalid.isEmpty()) {
      throw new ErrorOccurredException("Invalid event types: " + invalid);
    }
  }
}
