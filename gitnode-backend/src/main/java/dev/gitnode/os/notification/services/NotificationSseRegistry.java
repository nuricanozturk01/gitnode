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
package dev.gitnode.os.notification.services;

import dev.gitnode.os.notification.dtos.NotificationDto;
import java.io.IOException;
import java.util.ArrayList;
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

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class NotificationSseRegistry {

  private static final int MAX_EMITTERS_PER_USER = 5;

  private final ObjectMapper objectMapper;

  /**
   * CopyOnWriteArrayList per user: safe for concurrent push() reads and compute() writes. Max 5
   * entries per user keeps COW overhead negligible.
   */
  private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitters =
      new ConcurrentHashMap<>();

  public SseEmitter subscribe(final UUID recipientId) {
    final var emitter = new SseEmitter(TimeUnit.HOURS.toMillis(1));

    this.emitters.compute(
        recipientId,
        (_, existing) -> {
          final var list = existing != null ? existing : new CopyOnWriteArrayList<SseEmitter>();
          if (list.size() >= MAX_EMITTERS_PER_USER) {
            final var oldest = list.remove(0);
            try {
              oldest.complete();
            } catch (final Exception ignored) {
            }
          }
          list.add(emitter);
          return list;
        });

    final Runnable cleanup = () -> this.remove(recipientId, emitter);
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(_ -> cleanup.run());

    return emitter;
  }

  public void push(final UUID recipientId, final NotificationDto dto) {
    final var list = this.emitters.get(recipientId);
    if (list == null || list.isEmpty()) {
      return;
    }

    final String payload;
    try {
      payload = this.objectMapper.writeValueAsString(dto);
    } catch (final JacksonException e) {
      log.warn("Failed to serialize notification for SSE push: {}", e.getMessage());
      return;
    }

    final List<SseEmitter> dead = new ArrayList<>();
    for (final var emitter : list) {
      try {
        emitter.send(SseEmitter.event().name("notification").data(payload));
      } catch (final IOException e) {
        dead.add(emitter);
      }
    }
    dead.forEach(e -> this.remove(recipientId, e));
  }

  @Scheduled(fixedDelay = 30_000)
  public void sendHeartbeat() {
    this.emitters.forEach(
        (recipientId, list) -> {
          final List<SseEmitter> dead = new ArrayList<>();
          for (final var emitter : list) {
            try {
              emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (final IOException e) {
              dead.add(emitter);
            }
          }
          dead.forEach(e -> this.remove(recipientId, e));
        });
  }

  private void remove(final UUID recipientId, final SseEmitter emitter) {
    this.emitters.computeIfPresent(
        recipientId,
        (_, list) -> {
          list.remove(emitter);
          return list.isEmpty() ? null : list;
        });
  }
}
