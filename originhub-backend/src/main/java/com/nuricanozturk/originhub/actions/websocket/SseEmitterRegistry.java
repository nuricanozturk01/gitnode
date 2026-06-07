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

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Component
@NullMarked
public class SseEmitterRegistry {

  private final ConcurrentHashMap<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

  public SseEmitter subscribe(final UUID stepId) {
    final var emitter = new SseEmitter(0L);
    final var list = this.emitters.computeIfAbsent(stepId, _ -> new CopyOnWriteArrayList<>());
    list.add(emitter);

    final Runnable cleanup = () -> this.remove(stepId, emitter);
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(_ -> cleanup.run());

    return emitter;
  }

  public void broadcast(final UUID stepId, final Object data) {
    final var list = this.emitters.get(stepId);
    if (list == null || list.isEmpty()) {
      return;
    }
    for (final var emitter : list) {
      try {
        emitter.send(SseEmitter.event().data(data));
      } catch (final IOException | IllegalStateException ex) {
        log.debug("SSE send failed for step {}, removing emitter", stepId);
        this.remove(stepId, emitter);
      }
    }
  }

  public void complete(final UUID stepId) {
    final var list = this.emitters.remove(stepId);
    if (list == null) {
      return;
    }
    for (final var emitter : list) {
      emitter.complete();
    }
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
    final var list = this.emitters.get(stepId);
    if (list != null) {
      list.remove(emitter);
    }
  }
}
