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
package com.nuricanozturk.originhub.actions.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@NullMarked
public class RunnerSession {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Getter private final UUID runnerId;
  private final WebSocketSession session;

  public RunnerSession(final UUID runnerId, final WebSocketSession session) {
    this.runnerId = runnerId;
    this.session = session;
  }

  public boolean isOpen() {
    return this.session.isOpen();
  }

  public synchronized void send(final ServerMessage message) {
    if (!this.session.isOpen()) {
      log.warn("Cannot send message to runner {}: session closed", this.runnerId);
      return;
    }
    try {
      final var json = MAPPER.writeValueAsString(message);
      this.session.sendMessage(new TextMessage(json));
    } catch (final JsonProcessingException ex) {
      log.error("Failed to serialize message for runner {}", this.runnerId, ex);
    } catch (final IOException ex) {
      log.warn("Failed to send message to runner {}", this.runnerId, ex);
    }
  }

  public void close() {
    try {
      this.session.close();
    } catch (final IOException ex) {
      log.debug("Error closing session for runner {}", this.runnerId, ex);
    }
  }
}
