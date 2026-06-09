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

import dev.gitnode.os.actions.services.JobDispatcher;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Receives Redis pub/sub messages on {@code gitnode:actions:dispatch:{runnerId}} and forwards them
 * to the locally-connected runner WebSocket session, if any. This allows any backend instance to
 * dispatch a job regardless of which instance holds the runner's WebSocket connection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class RunnerDispatchListener implements MessageListener {

  private final RunnerSessionRegistry sessionRegistry;

  @Override
  public void onMessage(final Message message, final @Nullable byte[] pattern) {
    final var channel = new String(message.getChannel(), StandardCharsets.UTF_8);
    final var prefixLength = JobDispatcher.DISPATCH_CHANNEL_PREFIX.length();
    if (channel.length() <= prefixLength) {
      return;
    }
    final var runnerIdStr = channel.substring(prefixLength);
    try {
      final var runnerId = UUID.fromString(runnerIdStr);
      final var json = new String(message.getBody(), StandardCharsets.UTF_8);
      this.sessionRegistry
          .get(runnerId)
          .filter(RunnerSession::isOpen)
          .ifPresentOrElse(
              session -> {
                session.sendRaw(json);
                log.debug("Forwarded dispatch message to local runner {}", runnerId);
              },
              () ->
                  log.debug(
                      "Runner {} not connected on this instance, ignoring dispatch", runnerId));
    } catch (final IllegalArgumentException ex) {
      log.warn("Invalid runnerId in dispatch channel: {}", channel);
    }
  }
}
