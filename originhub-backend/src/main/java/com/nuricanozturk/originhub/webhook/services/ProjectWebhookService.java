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
import com.nuricanozturk.originhub.shared.project.dtos.ProjectSummary;
import com.nuricanozturk.originhub.shared.project.services.ProjectAccessService;
import com.nuricanozturk.originhub.webhook.dtos.WebhookForm;
import com.nuricanozturk.originhub.webhook.dtos.WebhookInfo;
import com.nuricanozturk.originhub.webhook.dtos.WebhookUpdateForm;
import com.nuricanozturk.originhub.webhook.entities.ProjectWebhook;
import com.nuricanozturk.originhub.webhook.entities.WebhookEventType;
import com.nuricanozturk.originhub.webhook.mappers.WebhookMapper;
import com.nuricanozturk.originhub.webhook.repositories.ProjectWebhookRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
@NullMarked
public class ProjectWebhookService {

  private static final int MAX_WEBHOOKS_PER_PROJECT = 3;

  private static final Set<String> PROJECT_VALID_EVENTS =
      Stream.of(
              WebhookEventType.TASK_CREATED,
              WebhookEventType.TASK_DELETED,
              WebhookEventType.TASK_UPDATED)
          .map(Enum::name)
          .collect(Collectors.toUnmodifiableSet());

  private final ProjectWebhookRepository projectWebhookRepository;
  private final ProjectAccessService projectAccessService;
  private final WebhookMapper webhookMapper;

  public List<WebhookInfo> list(final String ownerUsername, final String projectCode) {
    final var projectId = this.resolveProject(ownerUsername, projectCode).id();
    return this.projectWebhookRepository.findAllByProjectId(projectId).stream()
        .map(this.webhookMapper::toInfoFromProject)
        .toList();
  }

  @Transactional
  public WebhookInfo create(
      final String ownerUsername, final String projectCode, final WebhookForm form) {
    final var project = this.resolveProject(ownerUsername, projectCode);
    this.requireOwner(project.ownerUsername(), ownerUsername);

    if (this.projectWebhookRepository.countByProjectId(project.id()) >= MAX_WEBHOOKS_PER_PROJECT) {
      throw new ErrorOccurredException(
          "Maximum of " + MAX_WEBHOOKS_PER_PROJECT + " webhooks per project allowed");
    }
    if (this.projectWebhookRepository.existsByProjectIdAndUrl(project.id(), form.url())) {
      throw new ErrorOccurredException("Webhook with this URL already exists");
    }
    this.validateEvents(form.events());

    final var webhook = new ProjectWebhook();
    webhook.setProjectId(project.id());
    webhook.setUrl(form.url());
    webhook.setSecret(form.secret());
    webhook.setEnabled(form.enabled());
    webhook.setSubscribedEvents(new HashSet<>(form.events()));

    return this.webhookMapper.toInfoFromProject(this.projectWebhookRepository.save(webhook));
  }

  @Transactional
  public WebhookInfo update(
      final String ownerUsername,
      final String projectCode,
      final UUID webhookId,
      final WebhookUpdateForm form) {
    final var project = this.resolveProject(ownerUsername, projectCode);
    this.requireOwner(project.ownerUsername(), ownerUsername);
    final var webhook = this.findWebhook(webhookId, project.id());

    if (form.url() != null) {
      this.applyUrlUpdate(webhook, project.id(), form.url());
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

    return this.webhookMapper.toInfoFromProject(this.projectWebhookRepository.save(webhook));
  }

  @Transactional
  public void delete(final String ownerUsername, final String projectCode, final UUID webhookId) {
    final var project = this.resolveProject(ownerUsername, projectCode);
    this.requireOwner(project.ownerUsername(), ownerUsername);
    final var webhook = this.findWebhook(webhookId, project.id());
    this.projectWebhookRepository.delete(webhook);
  }

  private void applyUrlUpdate(
      final ProjectWebhook webhook, final UUID projectId, final String newUrl) {
    if (!newUrl.equals(webhook.getUrl())
        && this.projectWebhookRepository.existsByProjectIdAndUrl(projectId, newUrl)) {
      throw new ErrorOccurredException("Webhook with this URL already exists");
    }
    webhook.setUrl(newUrl);
  }

  private ProjectSummary resolveProject(final String ownerUsername, final String projectCode) {
    return this.projectAccessService
        .findByOwnerAndCode(ownerUsername, projectCode)
        .orElseThrow(() -> new ItemNotFoundException("Project not found"));
  }

  private ProjectWebhook findWebhook(final UUID webhookId, final UUID projectId) {
    final var webhook =
        this.projectWebhookRepository
            .findById(webhookId)
            .orElseThrow(() -> new ItemNotFoundException("Webhook not found"));
    if (!projectId.equals(webhook.getProjectId())) {
      throw new AccessNotAllowedException("notAuthorized");
    }
    return webhook;
  }

  private void requireOwner(final String actualOwner, final String callerUsername) {
    if (!actualOwner.equals(callerUsername)) {
      throw new AccessNotAllowedException("notAuthorized");
    }
  }

  private void validateEvents(final Set<String> events) {
    final var invalid = events.stream().filter(e -> !PROJECT_VALID_EVENTS.contains(e)).toList();
    if (!invalid.isEmpty()) {
      throw new ErrorOccurredException("Invalid event types: " + invalid);
    }
  }
}
