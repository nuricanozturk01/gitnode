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

import dev.gitnode.os.shared.cluster.ClusterEventBus;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Live log lines of a step. The browser's stream is held by one instance while the runner's log
 * lines may arrive on another, so {@link #broadcast} and {@link #complete} go through the cluster
 * bus and every instance forwards to the emitters it holds.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class SseEmitterRegistry {

  static final String LINE_CHANNEL = "gitnode:sse:step:";
  static final String DONE_CHANNEL = "gitnode:sse:step-done:";

  private final ConcurrentHashMap<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

  private final ClusterEventBus bus;
  private final ObjectMapper objectMapper;

  @PostConstruct
  void listenToCluster() {
    this.bus.subscribe(
        LINE_CHANNEL + "*",
        (channel, payload) -> this.deliver(idOf(channel, LINE_CHANNEL), payload));
    this.bus.subscribe(
        DONE_CHANNEL + "*", (channel, _) -> this.completeLocal(idOf(channel, DONE_CHANNEL)));
  }

  public SseEmitter subscribe(final UUID stepId) {
    final var emitter = this.newEmitter(TimeUnit.MINUTES.toMillis(30));
    final var list = this.emitters.computeIfAbsent(stepId, _ -> new CopyOnWriteArrayList<>());
    list.add(emitter);

    final Runnable cleanup = () -> this.remove(stepId, emitter);
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(_ -> cleanup.run());

    return emitter;
  }

  SseEmitter newEmitter(final long timeoutMillis) {
    return new SseEmitter(timeoutMillis);
  }

  public void broadcast(final UUID stepId, final Object data) {
    final String json;
    try {
      json = this.objectMapper.writeValueAsString(data);
    } catch (final JacksonException ex) {
      log.warn("Failed to serialize log line of step {}: {}", stepId, ex.getMessage());
      return;
    }
    if (!this.bus.publish(LINE_CHANNEL + stepId, json)) {
      this.deliver(stepId, json);
    }
  }

  public void complete(final UUID stepId) {
    if (!this.bus.publish(DONE_CHANNEL + stepId, "")) {
      this.completeLocal(stepId);
    }
  }

  private void deliver(final UUID stepId, final String json) {
    final var list = this.emitters.get(stepId);
    if (list == null || list.isEmpty()) {
      return;
    }
    for (final var emitter : list) {
      try {
        emitter.send(SseEmitter.event().data(json));
      } catch (final IOException | IllegalStateException ex) {
        log.debug("SSE send failed for step {}, removing emitter", stepId);
        this.remove(stepId, emitter);
      }
    }
  }

  private void completeLocal(final UUID stepId) {
    final var list = this.emitters.remove(stepId);
    if (list == null) {
      return;
    }
    for (final var emitter : list) {
      try {
        emitter.complete();
      } catch (final IllegalStateException alreadyCompleted) {
        log.debug("SSE stream of step {} was already completed", stepId);
      }
    }
  }

  private static UUID idOf(final String channel, final String prefix) {
    return UUID.fromString(channel.substring(prefix.length()));
  }

  @Scheduled(fixedDelay = 30_000)
  public void sendHeartbeat() {
    this.emitters.forEach(
        (stepId, list) ->
            list.forEach(
                emitter -> {
                  try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                  } catch (final IOException | IllegalStateException ex) {
                    this.remove(stepId, emitter);
                  }
                }));
  }

  private void remove(final UUID stepId, final SseEmitter emitter) {
    this.emitters.computeIfPresent(
        stepId,
        (id, list) -> {
          list.remove(emitter);
          return list.isEmpty() ? null : list;
        });
  }
}
