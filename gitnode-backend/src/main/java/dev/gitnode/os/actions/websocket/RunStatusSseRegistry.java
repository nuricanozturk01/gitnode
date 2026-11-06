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

/**
 * Status changes of a workflow run. {@link #notify} and {@link #complete} reach the streams held by
 * any instance through the cluster bus.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class RunStatusSseRegistry {

  static final String UPDATED_CHANNEL = "gitnode:sse:run:";
  static final String DONE_CHANNEL = "gitnode:sse:run-done:";

  private final ConcurrentHashMap<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

  private final ClusterEventBus bus;

  @PostConstruct
  void listenToCluster() {
    this.bus.subscribe(
        UPDATED_CHANNEL + "*", (channel, _) -> this.notifyLocal(idOf(channel, UPDATED_CHANNEL)));
    this.bus.subscribe(
        DONE_CHANNEL + "*", (channel, _) -> this.completeLocal(idOf(channel, DONE_CHANNEL)));
  }

  public SseEmitter subscribe(final UUID runId) {
    final var emitter = this.newEmitter(TimeUnit.MINUTES.toMillis(30));
    final var list = this.emitters.computeIfAbsent(runId, _ -> new CopyOnWriteArrayList<>());
    list.add(emitter);

    final Runnable cleanup = () -> this.remove(runId, emitter);
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(_ -> cleanup.run());

    return emitter;
  }

  SseEmitter newEmitter(final long timeoutMillis) {
    return new SseEmitter(timeoutMillis);
  }

  public void notify(final UUID runId) {
    if (!this.bus.publish(UPDATED_CHANNEL + runId, "")) {
      this.notifyLocal(runId);
    }
  }

  public void complete(final UUID runId) {
    if (!this.bus.publish(DONE_CHANNEL + runId, "")) {
      this.completeLocal(runId);
    }
  }

  private void notifyLocal(final UUID runId) {
    final var list = this.emitters.get(runId);
    if (list == null || list.isEmpty()) {
      return;
    }
    for (final var emitter : list) {
      try {
        emitter.send(SseEmitter.event().name("run_updated").data(runId.toString()));
      } catch (final IOException | IllegalStateException ex) {
        // IllegalStateException: the client left and the stream completed in the meantime.
        log.debug("SSE send failed for run {}, removing emitter", runId);
        this.remove(runId, emitter);
      }
    }
  }

  private void completeLocal(final UUID runId) {
    final var list = this.emitters.remove(runId);
    if (list == null) {
      return;
    }
    for (final var emitter : list) {
      try {
        emitter.send(SseEmitter.event().name("run_completed").data(runId.toString()));
      } catch (final IOException | IllegalStateException alreadyGone) {
        log.debug("SSE stream of run {} was already closed", runId);
      }
      try {
        emitter.complete();
      } catch (final IllegalStateException alreadyCompleted) {
        log.debug("SSE stream of run {} was already completed", runId);
      }
    }
  }

  private static UUID idOf(final String channel, final String prefix) {
    return UUID.fromString(channel.substring(prefix.length()));
  }

  @Scheduled(fixedDelay = 30_000)
  public void sendHeartbeat() {
    this.emitters.forEach(
        (runId, list) ->
            list.forEach(
                emitter -> {
                  try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                  } catch (final IOException | IllegalStateException ex) {
                    this.remove(runId, emitter);
                  }
                }));
  }

  private void remove(final UUID runId, final SseEmitter emitter) {
    this.emitters.computeIfPresent(
        runId,
        (id, list) -> {
          list.remove(emitter);
          return list.isEmpty() ? null : list;
        });
  }
}
