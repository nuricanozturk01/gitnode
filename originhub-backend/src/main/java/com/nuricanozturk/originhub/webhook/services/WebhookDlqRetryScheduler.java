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

import com.nuricanozturk.originhub.webhook.entities.WebhookDeadLetter;
import com.nuricanozturk.originhub.webhook.repositories.WebhookDeadLetterRepository;
import com.nuricanozturk.originhub.webhook.repositories.WebhookRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@NullMarked
public class WebhookDlqRetryScheduler {

  private static final int MAX_DLQ_RETRIES = 3;
  private static final int DLQ_BACKOFF_MINUTES = 5;

  private final WebhookDeadLetterRepository deadLetterRepository;
  private final WebhookRepository webhookRepository;
  private final WebhookDeliveryService deliveryService;
  private final Counter dlqRetrySuccessCounter;
  private final Counter dlqRetryExhaustedCounter;
  private final int batchSize;

  public WebhookDlqRetryScheduler(
      final WebhookDeadLetterRepository deadLetterRepository,
      final WebhookRepository webhookRepository,
      final WebhookDeliveryService deliveryService,
      final MeterRegistry meterRegistry,
      @Value("${originhub.webhook.dlq.batch-size:50}") final int batchSize) {
    this.deadLetterRepository = deadLetterRepository;
    this.webhookRepository = webhookRepository;
    this.deliveryService = deliveryService;
    this.batchSize = batchSize;
    this.dlqRetrySuccessCounter =
        Counter.builder("webhook.dlq.retry.success")
            .description("DLQ entries successfully redelivered")
            .register(meterRegistry);
    this.dlqRetryExhaustedCounter =
        Counter.builder("webhook.dlq.retry.exhausted")
            .description("DLQ entries permanently exhausted after all retries")
            .register(meterRegistry);
  }

  @Scheduled(cron = "${originhub.webhook.dlq.retry-cron:0 */5 * * * *}")
  @Transactional
  public void retryDeadLetters() {
    final var due =
        this.deadLetterRepository.findDueForRetry(
            Instant.now(), MAX_DLQ_RETRIES, PageRequest.of(0, this.batchSize));
    if (due.isEmpty()) {
      return;
    }
    log.info("DLQ retry run: {} entries due", due.size());
    due.forEach(this::processEntry);
  }

  private void processEntry(final WebhookDeadLetter dl) {
    final var webhookOpt = this.webhookRepository.findById(dl.getWebhookId());
    if (webhookOpt.isEmpty()) {
      log.info(
          "DLQ entry {} discarded — webhook {} no longer exists", dl.getId(), dl.getWebhookId());
      this.deadLetterRepository.delete(dl);
      return;
    }
    final var payload = dl.getPayload();
    if (payload == null) {
      log.warn("DLQ entry {} has null payload, discarding", dl.getId());
      this.deadLetterRepository.delete(dl);
      return;
    }
    final var secret = webhookOpt.get().getSecret();
    try {
      this.deliveryService.redeliverRaw(dl.getUrl(), secret, payload);
      log.info("DLQ retry succeeded id={} webhookId={}", dl.getId(), dl.getWebhookId());
      this.dlqRetrySuccessCounter.increment();
      this.deadLetterRepository.delete(dl);
    } catch (final CallNotPermittedException ex) {
      // Circuit breaker OPEN — no HTTP attempt was made; preserve retry count
      log.warn(
          "DLQ retry skipped (circuit OPEN) id={} url={}, will retry next run",
          dl.getId(),
          dl.getUrl());
    } catch (final Exception ex) {
      final int newCount = dl.getDlqRetryCount() + 1;
      dl.setDlqRetryCount(newCount);
      if (newCount >= MAX_DLQ_RETRIES) {
        log.error(
            "DLQ entry {} permanently exhausted after {} retries url={}: {}",
            dl.getId(),
            newCount,
            dl.getUrl(),
            ex.getMessage());
        this.dlqRetryExhaustedCounter.increment();
        this.deadLetterRepository.delete(dl);
      } else {
        final var delay = Duration.ofMinutes(DLQ_BACKOFF_MINUTES).multipliedBy(1L << newCount);
        dl.setNextRetryAt(Instant.now().plus(delay));
        this.deadLetterRepository.save(dl);
        log.warn(
            "DLQ retry {}/{} failed id={} url={}, next attempt at {}",
            newCount,
            MAX_DLQ_RETRIES,
            dl.getId(),
            dl.getUrl(),
            dl.getNextRetryAt());
      }
    }
  }
}
