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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.actions.entities.Runner;
import dev.gitnode.os.actions.entities.RunnerStatus;
import dev.gitnode.os.actions.repositories.RunnerRepository;
import dev.gitnode.os.actions.services.WorkflowExecutionService;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@DisplayName("RunnerWebSocketHandler — connection lifecycle")
class RunnerWebSocketHandlerTest {

  private final RunnerSessionRegistry registry = new RunnerSessionRegistry();
  private final RunnerRepository runnerRepository = mock(RunnerRepository.class);
  private final WorkflowExecutionService executionService = mock(WorkflowExecutionService.class);
  private final RunnerWebSocketHandler handler =
      new RunnerWebSocketHandler(this.registry, this.runnerRepository, this.executionService);

  private final UUID runnerId = UUID.randomUUID();
  private final Runner runner = new Runner();

  @BeforeEach
  void setUp() {
    this.runner.setStatus(RunnerStatus.ONLINE);
    when(this.runnerRepository.findById(this.runnerId)).thenReturn(Optional.of(this.runner));
  }

  private WebSocketSession session(final String id) {
    final var session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn(id);
    when(session.isOpen()).thenReturn(true);
    when(session.getAttributes())
        .thenReturn(Map.of(RunnerHandshakeInterceptor.ATTR_RUNNER_ID, this.runnerId));
    return session;
  }

  @Test
  @DisplayName("late close of an old session does not drop the newer session or mark OFFLINE")
  void closeOfStaleSession_isIgnored() {
    final var oldSession = this.session("old");
    final var newSession = this.session("new");
    this.handler.afterConnectionEstablished(oldSession);
    this.handler.afterConnectionEstablished(newSession);

    this.handler.afterConnectionClosed(oldSession, CloseStatus.GOING_AWAY);

    assertThat(this.registry.isConnected(this.runnerId)).isTrue();
    assertThat(this.runner.getStatus()).isEqualTo(RunnerStatus.ONLINE);
    verify(this.executionService, never()).handleRunnerDisconnected(any());
  }

  @Test
  @DisplayName("close of the current session marks the runner OFFLINE")
  void closeOfCurrentSession_marksOffline() {
    final var current = this.session("current");
    this.handler.afterConnectionEstablished(current);

    this.handler.afterConnectionClosed(current, CloseStatus.NORMAL);

    assertThat(this.registry.get(this.runnerId)).isEmpty();
    assertThat(this.runner.getStatus()).isEqualTo(RunnerStatus.OFFLINE);
    verify(this.executionService).handleRunnerDisconnected(this.runnerId);
  }

  @Test
  @DisplayName("heartbeat revives a runner that was wrongly marked OFFLINE")
  void heartbeat_revivesOfflineRunner() throws Exception {
    this.runner.setStatus(RunnerStatus.OFFLINE);
    final var session = this.session("s");
    this.handler.afterConnectionEstablished(session);
    this.runner.setStatus(RunnerStatus.OFFLINE);

    this.handler.handleTextMessage(session, new TextMessage("{\"type\":\"HEARTBEAT\"}"));

    assertThat(this.runner.getStatus()).isEqualTo(RunnerStatus.ONLINE);
    assertThat(this.runner.getLastHeartbeat()).isNotNull();
  }

  @Test
  @DisplayName("heartbeat does not flip a BUSY runner to ONLINE")
  void heartbeat_keepsBusy() throws Exception {
    final var session = this.session("s");
    this.handler.afterConnectionEstablished(session);
    this.runner.setStatus(RunnerStatus.BUSY);

    this.handler.handleTextMessage(session, new TextMessage("{\"type\":\"HEARTBEAT\"}"));

    assertThat(this.runner.getStatus()).isEqualTo(RunnerStatus.BUSY);
  }
}
