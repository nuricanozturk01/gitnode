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

import com.nuricanozturk.originhub.shared.collaborator.dtos.CollaboratorPermission;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.AccessNotAllowedException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.repo.services.RepoService;
import com.nuricanozturk.originhub.webhook.dtos.WebhookForm;
import com.nuricanozturk.originhub.webhook.dtos.WebhookInfo;
import com.nuricanozturk.originhub.webhook.dtos.WebhookUpdateForm;
import com.nuricanozturk.originhub.webhook.entities.Webhook;
import com.nuricanozturk.originhub.webhook.mappers.WebhookMapper;
import com.nuricanozturk.originhub.webhook.repositories.WebhookRepository;
import com.nuricanozturk.originhub.webhook.utils.WebhookValidator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
@NullMarked
public class WebhookService {

  private final WebhookRepository webhookRepository;
  private final RepoRepository repoRepository;
  private final RepoService repoService;
  private final WebhookMapper webhookMapper;

  public List<WebhookInfo> list(final UUID requesterId, final String owner, final String repoName) {
    this.repoService.assertUserHasAnyPermission(
        requesterId,
        owner,
        repoName,
        CollaboratorPermission.SETTINGS_READ,
        CollaboratorPermission.SETTINGS_WRITE);
    final var repoId = this.resolveRepoId(owner, repoName);
    return this.webhookRepository.findAllByRepoId(repoId).stream()
        .map(this.webhookMapper::toInfo)
        .toList();
  }

  @Transactional
  public WebhookInfo create(
      final UUID requesterId, final String owner, final String repoName, final WebhookForm form) {
    this.repoService.assertUserHasPermission(
        requesterId, owner, repoName, CollaboratorPermission.SETTINGS_WRITE);
    final var repoId = this.resolveRepoId(owner, repoName);
    if (this.webhookRepository.countByRepoId(repoId) >= WebhookValidator.MAX_WEBHOOKS) {
      throw new ErrorOccurredException(
          "Maximum of " + WebhookValidator.MAX_WEBHOOKS + " webhooks per repository allowed");
    }
    if (this.webhookRepository.existsByRepoIdAndUrl(repoId, form.url())) {
      throw new ErrorOccurredException(WebhookValidator.ERR_URL_EXISTS);
    }
    WebhookValidator.validateEvents(form.events(), WebhookValidator.ALL_EVENTS);

    final var webhook = new Webhook();
    webhook.setRepoId(repoId);
    webhook.setUrl(form.url());
    webhook.setSecret(form.secret());
    webhook.setEnabled(form.enabled());
    webhook.setSubscribedEvents(new HashSet<>(form.events()));

    return this.webhookMapper.toInfo(this.webhookRepository.save(webhook));
  }

  @Transactional
  public WebhookInfo update(
      final UUID requesterId,
      final String owner,
      final String repoName,
      final UUID webhookId,
      final WebhookUpdateForm form) {
    this.repoService.assertUserHasPermission(
        requesterId, owner, repoName, CollaboratorPermission.SETTINGS_WRITE);
    final var repoId = this.resolveRepoId(owner, repoName);
    final var webhook = this.findWebhook(webhookId, repoId);

    if (form.url() != null) {
      this.applyUrlUpdate(webhook, repoId, form.url());
    }
    if (form.secret() != null) {
      webhook.setSecret(form.secret().isBlank() ? null : form.secret());
    }
    if (form.enabled() != null) {
      webhook.setEnabled(form.enabled());
    }
    if (form.events() != null) {
      WebhookValidator.validateEvents(form.events(), WebhookValidator.ALL_EVENTS);
      webhook.setSubscribedEvents(new HashSet<>(form.events()));
    }

    return this.webhookMapper.toInfo(this.webhookRepository.save(webhook));
  }

  @Transactional
  public void delete(
      final UUID requesterId, final String owner, final String repoName, final UUID webhookId) {
    this.repoService.assertUserHasPermission(
        requesterId, owner, repoName, CollaboratorPermission.SETTINGS_WRITE);
    final var repoId = this.resolveRepoId(owner, repoName);
    final var webhook = this.findWebhook(webhookId, repoId);
    this.webhookRepository.delete(webhook);
  }

  private void applyUrlUpdate(final Webhook webhook, final UUID repoId, final String newUrl) {
    if (!newUrl.equals(webhook.getUrl())
        && this.webhookRepository.existsByRepoIdAndUrl(repoId, newUrl)) {
      throw new ErrorOccurredException(WebhookValidator.ERR_URL_EXISTS);
    }
    webhook.setUrl(newUrl);
  }

  private UUID resolveRepoId(final String owner, final String repoName) {
    return this.repoRepository
        .findByOwnerUsernameAndName(owner, repoName)
        .map(Repo::getId)
        .orElseThrow(() -> new ItemNotFoundException("Repository not found"));
  }

  private Webhook findWebhook(final UUID webhookId, final UUID repoId) {
    final var webhook =
        this.webhookRepository
            .findById(webhookId)
            .orElseThrow(() -> new ItemNotFoundException(WebhookValidator.ERR_NOT_FOUND));
    if (!repoId.equals(webhook.getRepoId())) {
      throw new AccessNotAllowedException(WebhookValidator.ERR_NOT_AUTHORIZED);
    }
    return webhook;
  }
}
