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

import dev.gitnode.os.shared.circuitbreaker.DistributedCircuitBreakerGuard;
import dev.gitnode.os.webhook.entities.WebhookDeadLetter;
import dev.gitnode.os.webhook.entities.WebhookEventType;
import dev.gitnode.os.webhook.repositories.WebhookDeadLetterRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@NullMarked
public class WebhookDeliveryService {

  private static final int MAX_ATTEMPTS = 3;
  private static final String WEBHOOK_CB_CONFIG = "webhook-delivery";

  /** Overridable in tests to avoid real sleeps. */
  Duration retryBaseDelay = Duration.ofSeconds(1);

  private final ObjectMapper objectMapper;
  private final RestClient restClient;
  private final WebhookDeadLetterRepository deadLetterRepository;
  private final CircuitBreakerRegistry circuitBreakerRegistry;
  private final DistributedCircuitBreakerGuard cbGuard;
  private final Counter deliverySuccessCounter;
  private final Counter deliveryFailureCounter;
  private final Timer deliveryTimer;

  public WebhookDeliveryService(
      final ObjectMapper objectMapper,
      @Qualifier("webhookRestClient") final RestClient restClient,
      final WebhookDeadLetterRepository deadLetterRepository,
      final CircuitBreakerRegistry circuitBreakerRegistry,
      final DistributedCircuitBreakerGuard cbGuard,
      final MeterRegistry meterRegistry) {
    this.objectMapper = objectMapper;
    this.restClient = restClient;
    this.deadLetterRepository = deadLetterRepository;
    this.circuitBreakerRegistry = circuitBreakerRegistry;
    this.cbGuard = cbGuard;
    this.deliverySuccessCounter =
        Counter.builder("webhook.delivery.success")
            .description("Successful webhook deliveries")
            .register(meterRegistry);
    this.deliveryFailureCounter =
        Counter.builder("webhook.delivery.failure")
            .description("Failed webhook deliveries routed to DLQ")
            .register(meterRegistry);
    this.deliveryTimer =
        Timer.builder("webhook.delivery.duration")
            .description("Webhook HTTP delivery duration")
            .register(meterRegistry);
  }

  @Async
  public void deliver(
      final UUID id,
      final String url,
      final @Nullable String secret,
      final @Nullable UUID repoId,
      final String logLabel,
      final WebhookEventType type,
      final Map<String, Object> data) {

    final var body = this.serializePayload(id, repoId, type, data, logLabel);
    if (body == null) {
      return;
    }

    this.attemptDeliveryWithRetry(id, url, secret, type, body, logLabel);
  }

  private @Nullable String serializePayload(
      final UUID id,
      final @Nullable UUID repoId,
      final WebhookEventType type,
      final Map<String, Object> data,
      final String logLabel) {

    try {
      final var payload = new LinkedHashMap<String, Object>();
      payload.put("event", type.getValue());
      payload.put("timestamp", Instant.now().toString());
      if (repoId != null) {
        payload.put("repoId", repoId.toString());
      }
      payload.put("data", data);
      return this.objectMapper.writeValueAsString(payload);
    } catch (final Exception ex) {
      log.error("{} payload serialization failed id={}: {}", logLabel, id, ex.getMessage());
      return null;
    }
  }

  private void attemptDeliveryWithRetry(
      final UUID id,
      final String url,
      final @Nullable String secret,
      final WebhookEventType type,
      final String body,
      final String logLabel) {

    final CircuitBreaker circuitBreaker = this.circuitBreakerFor(url);

    if (this.cbGuard.isGloballyOpen(circuitBreaker.getName())) {
      log.warn(
          "{} circuit OPEN (globally via Redis), routing to DLQ id={} url={}", logLabel, id, url);
      this.deliveryFailureCounter.increment();
      this.saveDeadLetter(id, url, type, body, null);
      return;
    }

    Exception lastException = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        this.deliveryTimer.record(
            () -> circuitBreaker.executeRunnable(() -> this.doDeliver(url, secret, body)));
        this.deliverySuccessCounter.increment();
        return;
      } catch (final CallNotPermittedException ex) {
        log.warn("{} circuit OPEN, routing directly to DLQ id={} url={}", logLabel, id, url);
        this.deliveryFailureCounter.increment();
        this.saveDeadLetter(id, url, type, body, ex);
        return;
      } catch (final Exception ex) {
        lastException = ex;
        if (attempt < MAX_ATTEMPTS && this.sleepBeforeRetry(logLabel, id, url, attempt, ex)) {
          break;
        }
      }
    }

    log.debug(
        "{} delivery permanently failed after {} attempts id={} url={}, routing to DLQ",
        logLabel,
        MAX_ATTEMPTS,
        id,
        url);
    this.deliveryFailureCounter.increment();
    this.saveDeadLetter(id, url, type, body, lastException);
  }

  private boolean sleepBeforeRetry(
      final String logLabel,
      final UUID id,
      final String url,
      final int attempt,
      final Exception cause) {

    log.warn(
        "{} delivery attempt {}/{} failed id={} url={}: {}",
        logLabel,
        attempt,
        MAX_ATTEMPTS,
        id,
        url,
        cause.getMessage());
    try {
      Thread.sleep(this.retryBaseDelay.multipliedBy(1L << (attempt - 1)));
      return false;
    } catch (final InterruptedException ie) {
      Thread.currentThread().interrupt();
      return true;
    }
  }

  void redeliverRaw(final String url, final @Nullable String secret, final String body) {
    final CircuitBreaker circuitBreaker = this.circuitBreakerFor(url);
    this.deliveryTimer.record(
        () -> circuitBreaker.executeRunnable(() -> this.doDeliver(url, secret, body)));
  }

  private CircuitBreaker circuitBreakerFor(final String url) {
    String host;
    try {
      final var h = URI.create(url).getHost();
      host = h != null ? h : "unknown";
    } catch (final IllegalArgumentException ignored) {
      host = "unknown";
    }
    final var cb = this.circuitBreakerRegistry.circuitBreaker("webhook." + host, WEBHOOK_CB_CONFIG);
    this.cbGuard.registerIfAbsent(cb);
    return cb;
  }

  private void doDeliver(final String url, final @Nullable String secret, final String body) {
    var spec = this.restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body);
    if (secret != null && !secret.isBlank()) {
      spec = spec.header("X-Hub-Signature-256", this.computeHmacSha256(body, secret));
    }
    spec.retrieve().toBodilessEntity();
  }

  private void saveDeadLetter(
      final UUID webhookId,
      final String url,
      final WebhookEventType type,
      final String body,
      final @Nullable Exception ex) {
    try {
      final var dl = new WebhookDeadLetter();
      dl.setWebhookId(webhookId);
      dl.setUrl(url);
      dl.setEventType(type.getValue());
      dl.setPayload(body);
      dl.setErrorMessage(ex != null ? ex.getMessage() : "unknown");
      dl.setAttemptCount(MAX_ATTEMPTS);
      this.deadLetterRepository.save(dl);
    } catch (final Exception saveEx) {
      log.error("Failed to persist webhook dead letter id={}: {}", webhookId, saveEx.getMessage());
    }
  }

  private String computeHmacSha256(final String body, final String secret) {
    try {
      final var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      final var bytes = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
      return "sha256=" + HexFormat.of().formatHex(bytes);
    } catch (final Exception ex) {
      throw new RuntimeException("HMAC-SHA256 computation failed", ex);
    }
  }
}
