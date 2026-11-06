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

import dev.gitnode.os.notification.dtos.NotificationInfo;
import dev.gitnode.os.shared.cluster.ClusterEventBus;
import jakarta.annotation.PostConstruct;
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

  static final String PUSH_CHANNEL = "gitnode:sse:notif:";
  static final String CLOSE_CHANNEL = "gitnode:sse:notif-close:";

  private final ObjectMapper objectMapper;
  private final ClusterEventBus bus;

  /**
   * CopyOnWriteArrayList per user: safe for concurrent push() reads and compute() writes. Max 5
   * entries per user keeps COW overhead negligible.
   */
  private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitters =
      new ConcurrentHashMap<>();

  @PostConstruct
  void listenToCluster() {
    this.bus.subscribe(
        PUSH_CHANNEL + "*",
        (channel, payload) -> this.deliver(idOf(channel, PUSH_CHANNEL), payload));
    this.bus.subscribe(
        CLOSE_CHANNEL + "*", (channel, _) -> this.closeLocal(idOf(channel, CLOSE_CHANNEL)));
  }

  SseEmitter newEmitter(final long timeoutMillis) {
    return new SseEmitter(timeoutMillis);
  }

  public SseEmitter subscribe(final UUID recipientId) {
    final var emitter = this.newEmitter(TimeUnit.HOURS.toMillis(1));

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

  /** Pushes to the user's streams on every instance. */
  public void push(final UUID recipientId, final NotificationInfo dto) {
    final String payload;
    try {
      payload = this.objectMapper.writeValueAsString(dto);
    } catch (final JacksonException e) {
      log.warn("Failed to serialize notification for SSE push: {}", e.getMessage());
      return;
    }
    if (!this.bus.publish(PUSH_CHANNEL + recipientId, payload)) {
      this.deliver(recipientId, payload);
    }
  }

  private void deliver(final UUID recipientId, final String payload) {
    final var list = this.emitters.get(recipientId);
    if (list == null || list.isEmpty()) {
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

  /** Ends every open stream of a user, for example when the account was disabled. */
  public void closeAll(final UUID recipientId) {
    if (!this.bus.publish(CLOSE_CHANNEL + recipientId, "")) {
      this.closeLocal(recipientId);
    }
  }

  private void closeLocal(final UUID recipientId) {
    final var removed = this.emitters.remove(recipientId);
    if (removed == null) {
      return;
    }
    for (final var emitter : removed) {
      try {
        emitter.complete();
      } catch (final RuntimeException ignored) {
        // already closed
      }
    }
  }

  private static UUID idOf(final String channel, final String prefix) {
    return UUID.fromString(channel.substring(prefix.length()));
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
