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

import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.webhook.api.WebhookDlqAdminPort;
import dev.gitnode.os.webhook.api.WebhookDlqEntry;
import dev.gitnode.os.webhook.entities.WebhookDeadLetter;
import dev.gitnode.os.webhook.repositories.WebhookDeadLetterRepository;
import dev.gitnode.os.webhook.repositories.WebhookRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class WebhookDlqAdminService implements WebhookDlqAdminPort {

  private final WebhookDeadLetterRepository deadLetterRepository;
  private final WebhookRepository webhookRepository;
  private final WebhookDeliveryService deliveryService;

  @Override
  @Transactional(readOnly = true)
  public Page<WebhookDlqEntry> list(final Pageable pageable) {

    return this.deadLetterRepository.findAllByOrderByFailedAtDesc(pageable).map(this::toEntry);
  }

  @Override
  @Transactional(readOnly = true)
  public long countPending() {

    return this.deadLetterRepository.count();
  }

  @Override
  @Transactional
  public void retry(final UUID id) {

    final var dl = this.requireDeadLetter(id);
    final var payload = dl.getPayload();
    if (payload == null) {
      throw new ItemNotFoundException("webhookDlqPayloadMissing");
    }

    final var webhook =
        this.webhookRepository
            .findById(dl.getWebhookId())
            .orElseThrow(() -> new ItemNotFoundException("webhookNotExist"));

    this.deliveryService.redeliverRaw(dl.getUrl(), webhook.getSecret(), payload);
    this.deadLetterRepository.delete(dl);
    log.info("Admin manually retried DLQ entry id={} webhookId={}", id, dl.getWebhookId());
  }

  @Override
  @Transactional
  public void dismiss(final UUID id) {

    final var dl = this.requireDeadLetter(id);
    this.deadLetterRepository.delete(dl);
    log.info("Admin dismissed DLQ entry id={} webhookId={}", id, dl.getWebhookId());
  }

  private WebhookDeadLetter requireDeadLetter(final UUID id) {

    return this.deadLetterRepository
        .findById(id)
        .orElseThrow(() -> new ItemNotFoundException("webhookDlqNotFound"));
  }

  private WebhookDlqEntry toEntry(final WebhookDeadLetter dl) {

    return new WebhookDlqEntry(
        dl.getId(),
        dl.getWebhookId(),
        dl.getUrl(),
        dl.getEventType(),
        dl.getErrorMessage(),
        dl.getAttemptCount(),
        dl.getDlqRetryCount(),
        dl.getNextRetryAt(),
        dl.getFailedAt());
  }
}
