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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.webhook.entities.WebhookEventType;
import com.nuricanozturk.originhub.webhook.repositories.WebhookDeadLetterRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookDeliveryService unit tests")
class WebhookDeliveryServiceTest {

  @Mock private RestClient restClient;
  @Mock private WebhookDeadLetterRepository deadLetterRepository;

  private ObjectMapper objectMapper;
  private WebhookDeliveryService deliveryService;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    deliveryService =
        new WebhookDeliveryService(
            objectMapper, restClient, deadLetterRepository, new SimpleMeterRegistry());
    deliveryService.retryBaseDelay = Duration.ZERO;
  }

  @Test
  @DisplayName("deliver POSTs JSON payload with repoId when secret is absent")
  void deliver_postsJson_withoutSignature_whenNoSecret() {
    RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
    RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
    when(restClient.post()).thenReturn(uriSpec);
    when(uriSpec.uri("https://hook.test/deliver")).thenReturn(bodySpec);
    when(bodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(bodySpec);
    when(bodySpec.body(anyString())).thenReturn(bodySpec);
    when(bodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.noContent().build());

    UUID webhookId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    Map<String, Object> data = Map.of("branchName", "main");

    deliveryService.deliver(
        webhookId,
        "https://hook.test/deliver",
        null,
        repoId,
        "Webhook",
        WebhookEventType.REPO_PUSHED,
        data);

    ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
    verify(bodySpec).body(bodyCaptor.capture());
    verify(bodySpec, never()).header(anyString(), anyString());

    String body = bodyCaptor.getValue();
    assertThat(body).contains("\"event\":\"repo.pushed\"");
    assertThat(body).contains("\"repoId\":\"" + repoId + "\"");
    assertThat(body).contains("\"branchName\":\"main\"");
  }

  @Test
  @DisplayName("deliver adds X-Hub-Signature-256 when secret is configured")
  void deliver_addsHmacHeader_whenSecretPresent() throws Exception {
    RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
    RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
    when(restClient.post()).thenReturn(uriSpec);
    when(uriSpec.uri(anyString())).thenReturn(bodySpec);
    when(bodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(bodySpec);
    when(bodySpec.body(anyString())).thenReturn(bodySpec);
    when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
    when(bodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.noContent().build());

    String secret = "whsec_test";
    deliveryService.deliver(
        UUID.randomUUID(),
        "https://hook.test",
        secret,
        null,
        "Webhook",
        WebhookEventType.ISSUE_OPENED,
        Map.of("issueId", UUID.randomUUID()));

    ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
    verify(bodySpec).body(bodyCaptor.capture());
    ArgumentCaptor<String> signatureCaptor = ArgumentCaptor.forClass(String.class);
    verify(bodySpec).header(eq("X-Hub-Signature-256"), signatureCaptor.capture());

    String expected = computeHmacSha256(bodyCaptor.getValue(), secret);
    assertThat(signatureCaptor.getValue()).isEqualTo(expected);
    assertThat(signatureCaptor.getValue()).startsWith("sha256=");
  }

  @Test
  @DisplayName("deliver omits repoId from payload when repoId is null")
  void deliver_omitsRepoId_whenRepoIdNull() {
    RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
    RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
    when(restClient.post()).thenReturn(uriSpec);
    when(uriSpec.uri(anyString())).thenReturn(bodySpec);
    when(bodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(bodySpec);
    when(bodySpec.body(anyString())).thenReturn(bodySpec);
    when(bodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.noContent().build());

    deliveryService.deliver(
        UUID.randomUUID(),
        "https://hook.test",
        null,
        null,
        "User webhook",
        WebhookEventType.PROJECT_CREATED,
        Map.of("name", "App"));

    ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
    verify(bodySpec).body(bodyCaptor.capture());
    assertThat(bodyCaptor.getValue()).doesNotContain("repoId");
    assertThat(bodyCaptor.getValue()).contains("\"event\":\"project.created\"");
  }

  @Test
  @DisplayName("deliver swallows HTTP failures without propagating")
  void deliver_swallowsException_whenHttpFails() {
    RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
    RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
    when(restClient.post()).thenReturn(uriSpec);
    when(uriSpec.uri(anyString())).thenReturn(bodySpec);
    when(bodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(bodySpec);
    when(bodySpec.body(anyString())).thenReturn(bodySpec);
    when(bodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.toBodilessEntity()).thenThrow(new RuntimeException("connection refused"));

    assertThatCode(
            () ->
                deliveryService.deliver(
                    UUID.randomUUID(),
                    "https://hook.test",
                    null,
                    null,
                    "Webhook",
                    WebhookEventType.REPO_PUSHED,
                    Map.of()))
        .doesNotThrowAnyException();
  }

  private static String computeHmacSha256(final String body, final String secret) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] bytes = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
    return "sha256=" + HexFormat.of().formatHex(bytes);
  }
}
