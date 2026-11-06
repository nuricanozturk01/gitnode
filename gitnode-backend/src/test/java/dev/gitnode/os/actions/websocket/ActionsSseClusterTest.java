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

import dev.gitnode.os.shared.cluster.InMemoryClusterEventBus;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

class ActionsSseClusterTest {

  /** Records what would be written to the browser. */
  static class RecordingEmitter extends SseEmitter {
    final List<String> sent = new ArrayList<>();
    boolean completed;

    RecordingEmitter() {
      super(60_000L);
    }

    @Override
    public synchronized void send(final SseEventBuilder builder) throws IOException {
      final var sb = new StringBuilder();
      builder.build().forEach(d -> sb.append(d.getData()));
      this.sent.add(
          sb.toString()
              .lines()
              .filter(l -> l.startsWith("data:"))
              .map(l -> l.substring("data:".length()))
              .reduce("", String::concat));
    }

    @Override
    public synchronized void complete() {
      this.completed = true;
    }
  }

  private InMemoryClusterEventBus bus;
  private RecordingEmitter emitterOnB;
  private SseEmitterRegistry logsA;
  private SseEmitterRegistry logsB;
  private RunStatusSseRegistry runsA;
  private RunStatusSseRegistry runsB;
  private final UUID id = UUID.randomUUID();

  private SseEmitterRegistry logRegistry() {
    final var registry =
        new SseEmitterRegistry(this.bus, JsonMapper.builder().build()) {
          @Override
          SseEmitter newEmitter(final long timeoutMillis) {
            return ActionsSseClusterTest.this.emitterOnB;
          }
        };
    registry.listenToCluster();
    return registry;
  }

  @BeforeEach
  void setUp() {
    this.bus = new InMemoryClusterEventBus();
    this.emitterOnB = new RecordingEmitter();
    this.logsA = new SseEmitterRegistry(this.bus, JsonMapper.builder().build());
    this.logsA.listenToCluster();
    this.logsB = this.logRegistry();
    this.runsA = new RunStatusSseRegistry(this.bus);
    this.runsA.listenToCluster();
    this.runsB =
        new RunStatusSseRegistry(this.bus) {
          @Override
          SseEmitter newEmitter(final long timeoutMillis) {
            return ActionsSseClusterTest.this.emitterOnB;
          }
        };
    this.runsB.listenToCluster();
  }

  @Test
  void logLinePublishedOnAnotherInstanceReachesTheStreamHeldHere() {
    this.logsB.subscribe(this.id);

    this.logsA.broadcast(this.id, java.util.Map.of("lineNum", 7));

    assertThat(this.emitterOnB.sent).containsExactly("{\"lineNum\":7}");
  }

  @Test
  void completionPublishedOnAnotherInstanceClosesTheStreamHeldHere() {
    this.logsB.subscribe(this.id);

    this.logsA.complete(this.id);

    assertThat(this.emitterOnB.completed).isTrue();
  }

  @Test
  void runStatusReachesStreamOnAnotherInstanceAndCompletes() {
    this.runsB.subscribe(this.id);

    this.runsA.notify(this.id);
    this.runsA.complete(this.id);

    assertThat(this.emitterOnB.sent).hasSize(2);
    assertThat(this.emitterOnB.sent.get(0)).isEqualTo(this.id.toString());
    assertThat(this.emitterOnB.completed).isTrue();
  }

  @Test
  void deliversLocallyWhenTheBusIsDown() {
    this.logsB.subscribe(this.id);
    this.bus.setAvailable(false);

    this.logsB.broadcast(this.id, java.util.Map.of("lineNum", 1));
    this.logsB.complete(this.id);

    assertThat(this.emitterOnB.sent).containsExactly("{\"lineNum\":1}");
    assertThat(this.emitterOnB.completed).isTrue();
  }

  @Test
  void eventsForUnknownStreamsAreIgnored() {
    this.logsA.broadcast(UUID.randomUUID(), java.util.Map.of("a", 1));
    this.runsA.notify(UUID.randomUUID());
    assertThat(this.emitterOnB.sent).isEmpty();
  }
}
