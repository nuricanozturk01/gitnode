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
package dev.gitnode.os.actions.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.gitnode.os.actions.entities.RunnerStatus;
import dev.gitnode.os.actions.repositories.RunnerRepository;
import dev.gitnode.os.actions.services.WorkflowExecutionService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class RunnerWebSocketHandler extends TextWebSocketHandler {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final RunnerSessionRegistry sessionRegistry;
  private final RunnerRepository runnerRepository;
  private final WorkflowExecutionService executionService;

  @Override
  public void afterConnectionEstablished(final WebSocketSession session) {

    final var runnerId = extractRunnerId(session);

    if (runnerId == null) {
      log.warn("WebSocket connected without runnerId attribute, closing");
      closeQuietly(session);
      return;
    }

    this.sessionRegistry.register(new RunnerSession(runnerId, session));
    this.markRunnerOnline(runnerId);

    log.info("Runner connected: runnerId={}", runnerId);
  }

  @Override
  protected void handleTextMessage(final WebSocketSession session, final TextMessage message) {

    final var runnerId = extractRunnerId(session);

    try {
      final var root = MAPPER.readTree(message.getPayload());
      final var type = root.path("type").asText();
      final var data = root.path("data");

      this.dispatchMessage(type, data, runnerId);
    } catch (final Exception ex) {
      log.error("Error processing WS message from runner {}", runnerId, ex);
    }
  }

  @Override
  public void afterConnectionClosed(final WebSocketSession session, final CloseStatus status) {

    final var runnerId = extractRunnerId(session);
    if (runnerId == null) {
      return;
    }

    this.sessionRegistry.remove(runnerId);
    this.executionService.handleRunnerDisconnected(runnerId);
    this.markRunnerOffline(runnerId);

    log.info("Runner disconnected: runnerId={}, status={}", runnerId, status);
  }

  @Override
  public boolean supportsPartialMessages() {
    return false;
  }

  // ── dispatch ──────────────────────────────────────────────────────────────

  private void dispatchMessage(
      final String type, final JsonNode data, final @Nullable UUID runnerId) {

    switch (type) {
      case "REGISTER" -> log.debug("REGISTER acknowledged for runner {}", runnerId);
      case "HEARTBEAT" -> this.handleHeartbeat(runnerId);
      case "JOB_CLAIMED" -> this.handleJobClaimed(data, runnerId);
      case "JOB_COMPLETED" -> this.handleJobCompleted(data);
      default -> this.dispatchStepEvent(type, data, runnerId);
    }
  }

  private void dispatchStepEvent(
      final String type, final JsonNode data, final @Nullable UUID runnerId) {

    switch (type) {
      case "STEP_STARTED" -> this.handleStepStarted(data);
      case "LOG" -> this.handleLog(data);
      case "STEP_COMPLETED" -> this.handleStepCompleted(data);
      default -> log.warn("Unknown WS message type '{}' from runner {}", type, runnerId);
    }
  }

  // ── inbound handlers ──────────────────────────────────────────────────────

  private void handleHeartbeat(final UUID runnerId) {
    this.runnerRepository
        .findById(runnerId)
        .ifPresent(
            runner -> {
              runner.setLastHeartbeat(java.time.Instant.now());
              this.runnerRepository.save(runner);
            });
  }

  private void handleJobClaimed(final JsonNode data, final UUID runnerId) {
    final var jobId = uuidField(data, "jobId");
    if (jobId == null || runnerId == null) {
      return;
    }
    this.executionService.handleJobClaimed(jobId, runnerId);
  }

  private void handleStepStarted(final JsonNode data) {
    final var stepId = uuidField(data, "stepId");
    if (stepId == null) {
      return;
    }
    this.executionService.handleStepStarted(stepId);
  }

  private void handleLog(final JsonNode data) {
    final var stepId = uuidField(data, "stepId");
    if (stepId == null) {
      return;
    }
    final int lineNumber = data.path("line").asInt();
    final var content = data.path("content").asText();
    final var level = data.path("level").asText("info");
    this.executionService.ingestLog(stepId, lineNumber, content, level);
  }

  private void handleStepCompleted(final JsonNode data) {
    final var stepId = uuidField(data, "stepId");
    if (stepId == null) {
      return;
    }
    final var conclusion = data.path("conclusion").asText("success");
    this.executionService.handleStepCompleted(stepId, conclusion);
  }

  private void handleJobCompleted(final JsonNode data) {
    final var jobId = uuidField(data, "jobId");
    if (jobId == null) {
      return;
    }
    final var conclusion = data.path("conclusion").asText("success");
    this.executionService.handleJobCompleted(jobId, conclusion);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private void markRunnerOnline(final UUID runnerId) {
    this.runnerRepository
        .findById(runnerId)
        .ifPresent(
            runner -> {
              runner.setStatus(RunnerStatus.ONLINE);
              runner.setLastHeartbeat(java.time.Instant.now());
              this.runnerRepository.save(runner);
            });
  }

  private void markRunnerOffline(final UUID runnerId) {
    this.runnerRepository
        .findById(runnerId)
        .ifPresent(
            runner -> {
              runner.setStatus(RunnerStatus.OFFLINE);
              this.runnerRepository.save(runner);
            });
  }

  private static @Nullable UUID extractRunnerId(final WebSocketSession session) {
    final var val = session.getAttributes().get(RunnerHandshakeInterceptor.ATTR_RUNNER_ID);
    return val instanceof UUID id ? id : null;
  }

  private static @Nullable UUID uuidField(final JsonNode node, final String field) {
    final var text = node.path(field).asText(null);
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(text);
    } catch (final IllegalArgumentException ex) {
      log.warn("Invalid UUID '{}' in field '{}'", text, field);
      return null;
    }
  }

  private static void closeQuietly(final WebSocketSession session) {
    try {
      session.close(CloseStatus.POLICY_VIOLATION);
    } catch (final Exception ignored) {
    }
  }
}
