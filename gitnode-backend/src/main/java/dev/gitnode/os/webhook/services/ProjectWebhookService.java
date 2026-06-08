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

import dev.gitnode.os.shared.audit.annotations.Audited;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.project.dtos.ProjectSummary;
import dev.gitnode.os.shared.project.services.ProjectAccessService;
import dev.gitnode.os.webhook.dtos.WebhookForm;
import dev.gitnode.os.webhook.dtos.WebhookInfo;
import dev.gitnode.os.webhook.dtos.WebhookUpdateForm;
import dev.gitnode.os.webhook.entities.ProjectWebhook;
import dev.gitnode.os.webhook.entities.WebhookEventType;
import dev.gitnode.os.webhook.mappers.WebhookMapper;
import dev.gitnode.os.webhook.repositories.ProjectWebhookRepository;
import dev.gitnode.os.webhook.utils.WebhookValidator;
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

  @Audited(
      action = "CREATE_PROJECT_WEBHOOK",
      entityType = "WEBHOOK",
      entityIdSpEL = "#result.id().toString()",
      detailsSpEL = "'project=' + #ownerUsername + '/' + #projectCode + ', url=' + #form.url()")
  @Transactional
  public WebhookInfo create(
      final String ownerUsername, final String projectCode, final WebhookForm form) {
    final var project = this.resolveProject(ownerUsername, projectCode);
    this.requireOwner(project.ownerUsername(), ownerUsername);

    if (this.projectWebhookRepository.countByProjectId(project.id())
        >= WebhookValidator.MAX_WEBHOOKS) {
      throw new ErrorOccurredException(
          "Maximum of " + WebhookValidator.MAX_WEBHOOKS + " webhooks per project allowed");
    }
    if (this.projectWebhookRepository.existsByProjectIdAndUrl(project.id(), form.url())) {
      throw new ErrorOccurredException(WebhookValidator.ERR_URL_EXISTS);
    }
    WebhookValidator.validateEvents(form.events(), PROJECT_VALID_EVENTS);

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
      WebhookValidator.validateEvents(form.events(), PROJECT_VALID_EVENTS);
      webhook.setSubscribedEvents(new HashSet<>(form.events()));
    }

    return this.webhookMapper.toInfoFromProject(this.projectWebhookRepository.save(webhook));
  }

  @Audited(
      action = "DELETE_PROJECT_WEBHOOK",
      entityType = "WEBHOOK",
      entityIdSpEL = "#webhookId.toString()",
      detailsSpEL = "'project=' + #ownerUsername + '/' + #projectCode")
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
      throw new ErrorOccurredException(WebhookValidator.ERR_URL_EXISTS);
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
            .orElseThrow(() -> new ItemNotFoundException(WebhookValidator.ERR_NOT_FOUND));
    if (!projectId.equals(webhook.getProjectId())) {
      throw new AccessNotAllowedException(WebhookValidator.ERR_NOT_AUTHORIZED);
    }
    return webhook;
  }

  private void requireOwner(final String actualOwner, final String callerUsername) {
    if (!actualOwner.equals(callerUsername)) {
      throw new AccessNotAllowedException(WebhookValidator.ERR_NOT_AUTHORIZED);
    }
  }
}
