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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.webhook.entities.Webhook;
import com.nuricanozturk.originhub.webhook.entities.WebhookDeadLetter;
import com.nuricanozturk.originhub.webhook.repositories.WebhookDeadLetterRepository;
import com.nuricanozturk.originhub.webhook.repositories.WebhookRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookDlqRetryScheduler unit tests")
class WebhookDlqRetrySchedulerTest {

  @Mock private WebhookDeadLetterRepository deadLetterRepository;
  @Mock private WebhookRepository webhookRepository;
  @Mock private WebhookDeliveryService deliveryService;

  private WebhookDlqRetryScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler =
        new WebhookDlqRetryScheduler(
            deadLetterRepository,
            webhookRepository,
            deliveryService,
            new SimpleMeterRegistry(),
            50);
  }

  @Test
  @DisplayName("retryDeadLetters does nothing when no entries are due")
  void retryDeadLetters_doesNothing_whenNoDueEntries() {
    when(deadLetterRepository.findDueForRetry(any(), anyInt(), any(Pageable.class)))
        .thenReturn(List.of());

    scheduler.retryDeadLetters();

    verify(deliveryService, never()).redeliverRaw(any(), any(), any());
    verify(deadLetterRepository, never()).delete(any(WebhookDeadLetter.class));
  }

  @Test
  @DisplayName("successful redelivery deletes DLQ entry")
  void retryDeadLetters_deletesEntry_onSuccess() {
    final var dl = deadLetter(UUID.randomUUID(), "https://hook.test", "{}", 0);
    final var webhook = webhook(dl.getWebhookId(), "secret");

    when(deadLetterRepository.findDueForRetry(any(), anyInt(), any(Pageable.class)))
        .thenReturn(List.of(dl));
    when(webhookRepository.findById(dl.getWebhookId())).thenReturn(Optional.of(webhook));

    scheduler.retryDeadLetters();

    verify(deliveryService).redeliverRaw("https://hook.test", "secret", "{}");
    verify(deadLetterRepository).delete(dl);
  }

  @Test
  @DisplayName("DLQ entry discarded when webhook no longer exists")
  void retryDeadLetters_discardsEntry_whenWebhookDeleted() {
    final var dl = deadLetter(UUID.randomUUID(), "https://hook.test", "{}", 0);

    when(deadLetterRepository.findDueForRetry(any(), anyInt(), any(Pageable.class)))
        .thenReturn(List.of(dl));
    when(webhookRepository.findById(dl.getWebhookId())).thenReturn(Optional.empty());

    scheduler.retryDeadLetters();

    verify(deliveryService, never()).redeliverRaw(any(), any(), any());
    verify(deadLetterRepository).delete(dl);
  }

  @Test
  @DisplayName("DLQ entry discarded when payload is null")
  void retryDeadLetters_discardsEntry_whenPayloadNull() {
    final var dl = deadLetter(UUID.randomUUID(), "https://hook.test", null, 0);
    final var webhook = webhook(dl.getWebhookId(), null);

    when(deadLetterRepository.findDueForRetry(any(), anyInt(), any(Pageable.class)))
        .thenReturn(List.of(dl));
    when(webhookRepository.findById(dl.getWebhookId())).thenReturn(Optional.of(webhook));

    scheduler.retryDeadLetters();

    verify(deliveryService, never()).redeliverRaw(any(), any(), any());
    verify(deadLetterRepository).delete(dl);
  }

  @Test
  @DisplayName("failed retry increments dlqRetryCount and schedules nextRetryAt")
  void retryDeadLetters_schedulesNextRetry_onFirstFailure() {
    final var dl = deadLetter(UUID.randomUUID(), "https://hook.test", "{}", 0);
    final var webhook = webhook(dl.getWebhookId(), null);

    when(deadLetterRepository.findDueForRetry(any(), anyInt(), any(Pageable.class)))
        .thenReturn(List.of(dl));
    when(webhookRepository.findById(dl.getWebhookId())).thenReturn(Optional.of(webhook));
    doThrow(new RuntimeException("connection refused"))
        .when(deliveryService)
        .redeliverRaw(any(), any(), any());

    final var before = Instant.now();
    scheduler.retryDeadLetters();

    final var captor = ArgumentCaptor.forClass(WebhookDeadLetter.class);
    verify(deadLetterRepository).save(captor.capture());
    verify(deadLetterRepository, never()).delete(any(WebhookDeadLetter.class));

    final var saved = captor.getValue();
    assertThat(saved.getDlqRetryCount()).isEqualTo(1);
    assertThat(saved.getNextRetryAt()).isNotNull().isAfter(before);
  }

  @Test
  @DisplayName("entry deleted and exhausted counter incremented when max retries reached")
  void retryDeadLetters_deletesAndCountsExhausted_whenMaxRetriesReached() {
    final var dl = deadLetter(UUID.randomUUID(), "https://hook.test", "{}", 2);
    final var webhook = webhook(dl.getWebhookId(), null);

    when(deadLetterRepository.findDueForRetry(any(), anyInt(), any(Pageable.class)))
        .thenReturn(List.of(dl));
    when(webhookRepository.findById(dl.getWebhookId())).thenReturn(Optional.of(webhook));
    doThrow(new RuntimeException("still failing"))
        .when(deliveryService)
        .redeliverRaw(any(), any(), any());

    scheduler.retryDeadLetters();

    verify(deadLetterRepository).delete(eq(dl));
    verify(deadLetterRepository, never()).save(any());
  }

  @Test
  @DisplayName("redeliverRaw called with webhook secret from repository, not DLQ")
  void retryDeadLetters_usesCurrentWebhookSecret() {
    final var dl =
        deadLetter(UUID.randomUUID(), "https://hook.test", "{\"event\":\"repo.pushed\"}", 0);
    final var webhook = webhook(dl.getWebhookId(), "current-secret");

    when(deadLetterRepository.findDueForRetry(any(), anyInt(), any(Pageable.class)))
        .thenReturn(List.of(dl));
    when(webhookRepository.findById(dl.getWebhookId())).thenReturn(Optional.of(webhook));

    scheduler.retryDeadLetters();

    verify(deliveryService).redeliverRaw(any(), eq("current-secret"), any());
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private static WebhookDeadLetter deadLetter(
      final UUID webhookId, final String url, final String payload, final int dlqRetryCount) {
    final var dl = new WebhookDeadLetter();
    dl.setWebhookId(webhookId);
    dl.setUrl(url);
    dl.setPayload(payload);
    dl.setAttemptCount(3);
    dl.setDlqRetryCount(dlqRetryCount);
    return dl;
  }

  private static Webhook webhook(final UUID id, final String secret) {
    final var w = new Webhook();
    w.setSecret(secret);
    return w;
  }
}
