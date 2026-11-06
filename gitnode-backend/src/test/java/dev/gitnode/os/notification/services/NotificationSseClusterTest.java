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

import static org.assertj.core.api.Assertions.assertThat;

import dev.gitnode.os.notification.dtos.NotificationInfo;
import dev.gitnode.os.shared.cluster.InMemoryClusterEventBus;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

class NotificationSseClusterTest {

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
  private NotificationSseRegistry instanceA;
  private NotificationSseRegistry instanceB;
  private final UUID user = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    this.bus = new InMemoryClusterEventBus();
    this.emitterOnB = new RecordingEmitter();
    this.instanceA = new NotificationSseRegistry(JsonMapper.builder().build(), this.bus);
    this.instanceA.listenToCluster();
    this.instanceB =
        new NotificationSseRegistry(JsonMapper.builder().build(), this.bus) {
          @Override
          SseEmitter newEmitter(final long timeoutMillis) {
            return NotificationSseClusterTest.this.emitterOnB;
          }
        };
    this.instanceB.listenToCluster();
  }

  private NotificationInfo info() {
    return Mockito.mock(NotificationInfo.class);
  }

  @Test
  void pushOnOneInstanceReachesTheUsersStreamOnAnother() {
    this.instanceB.subscribe(this.user);

    this.instanceA.push(this.user, this.info());

    assertThat(this.emitterOnB.sent).hasSize(1);
  }

  @Test
  void closeAllOnOneInstanceClosesTheStreamOnAnother() {
    this.instanceB.subscribe(this.user);

    this.instanceA.closeAll(this.user);

    assertThat(this.emitterOnB.completed).isTrue();
  }

  @Test
  void deliversLocallyWhenTheBusIsDown() {
    this.instanceB.subscribe(this.user);
    this.bus.setAvailable(false);

    this.instanceB.push(this.user, this.info());
    this.instanceB.closeAll(this.user);

    assertThat(this.emitterOnB.sent).hasSize(1);
    assertThat(this.emitterOnB.completed).isTrue();
  }

  @Test
  void otherUsersDoNotReceiveTheEvent() {
    this.instanceB.subscribe(this.user);

    this.instanceA.push(UUID.randomUUID(), this.info());

    assertThat(this.emitterOnB.sent).isEmpty();
  }
}
