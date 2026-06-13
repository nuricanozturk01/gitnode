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

import dev.gitnode.os.shared.lock.DistributedLockService;
import dev.gitnode.os.webhook.entities.Webhook;
import dev.gitnode.os.webhook.entities.WebhookDeadLetter;
import dev.gitnode.os.webhook.repositories.WebhookDeadLetterRepository;
import dev.gitnode.os.webhook.repositories.WebhookRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
  private static final String LOCK_KEY = "lock:webhook:dlq:retry";
  private static final Duration LOCK_TTL = Duration.ofMinutes(4);

  private final WebhookDeadLetterRepository deadLetterRepository;
  private final WebhookRepository webhookRepository;
  private final WebhookDeliveryService deliveryService;
  private final DistributedLockService lockService;
  private final Counter dlqRetrySuccessCounter;
  private final Counter dlqRetryExhaustedCounter;
  private final int batchSize;

  public WebhookDlqRetryScheduler(
      final WebhookDeadLetterRepository deadLetterRepository,
      final WebhookRepository webhookRepository,
      final WebhookDeliveryService deliveryService,
      final DistributedLockService lockService,
      final MeterRegistry meterRegistry,
      @Value("${gitnode.webhook.dlq.batch-size:50}") final int batchSize) {
    this.deadLetterRepository = deadLetterRepository;
    this.webhookRepository = webhookRepository;
    this.deliveryService = deliveryService;
    this.lockService = lockService;
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

  @Scheduled(cron = "${gitnode.webhook.dlq.retry-cron:0 */5 * * * *}")
  @Transactional
  public void retryDeadLetters() {
    final String owner = this.lockService.generateOwner();
    if (!this.lockService.tryLock(LOCK_KEY, owner, LOCK_TTL)) {
      log.debug("DLQ retry skipped — another instance holds the lock");
      return;
    }
    try {
      final var due =
          this.deadLetterRepository.findDueForRetry(
              Instant.now(), MAX_DLQ_RETRIES, PageRequest.of(0, this.batchSize));
      if (due.isEmpty()) {
        return;
      }
      log.info("DLQ retry run: {} entries due", due.size());
      final Map<UUID, Webhook> webhookMap = this.loadWebhooks(due);
      due.forEach(dl -> this.processEntry(dl, webhookMap));
    } finally {
      this.lockService.unlock(LOCK_KEY, owner);
    }
  }

  private Map<UUID, Webhook> loadWebhooks(final List<WebhookDeadLetter> entries) {
    final var ids = entries.stream().map(WebhookDeadLetter::getWebhookId).distinct().toList();
    return this.webhookRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(Webhook::getId, w -> w));
  }

  private void processEntry(final WebhookDeadLetter dl, final Map<UUID, Webhook> webhookMap) {
    final var webhook = webhookMap.get(dl.getWebhookId());
    if (webhook == null) {
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
    final var secret = webhook.getSecret();
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
