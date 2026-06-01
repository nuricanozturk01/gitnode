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

import com.nuricanozturk.originhub.webhook.entities.WebhookEventType;
import java.nio.charset.StandardCharsets;
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

  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  public WebhookDeliveryService(
      final ObjectMapper objectMapper,
      @Qualifier("webhookRestClient") final RestClient restClient) {
    this.objectMapper = objectMapper;
    this.restClient = restClient;
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
    try {
      final var payload = new LinkedHashMap<String, Object>();
      payload.put("event", type.getValue());
      payload.put("timestamp", Instant.now().toString());
      if (repoId != null) {
        payload.put("repoId", repoId.toString());
      }
      payload.put("data", data);

      final var body = this.objectMapper.writeValueAsString(payload);

      var spec = this.restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body);

      if (secret != null && !secret.isBlank()) {
        spec = spec.header("X-Hub-Signature-256", this.computeHmacSha256(body, secret));
      }

      spec.retrieve().toBodilessEntity();

    } catch (final Exception ex) {
      log.warn("{} delivery failed id={} url={}: {}", logLabel, id, url, ex.getMessage());
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
