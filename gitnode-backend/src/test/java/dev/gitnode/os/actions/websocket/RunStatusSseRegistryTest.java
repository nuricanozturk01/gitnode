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

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RunStatusSseRegistry")
class RunStatusSseRegistryTest {

  @Test
  @DisplayName("notifying a run whose stream already completed does not throw")
  void notifyAfterEmitterCompleted() {
    final var registry = new RunStatusSseRegistry();
    final var runId = UUID.randomUUID();
    registry.subscribe(runId).complete();

    assertThatCode(() -> registry.notify(runId)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("completing a run whose stream already completed does not throw (deleting a run)")
  void completeAfterEmitterCompleted() {
    final var registry = new RunStatusSseRegistry();
    final var runId = UUID.randomUUID();
    registry.subscribe(runId).complete();

    assertThatCode(() -> registry.complete(runId)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("the keep-alive does not throw for a stream that already completed")
  void heartbeatAfterEmitterCompleted() {
    final var registry = new RunStatusSseRegistry();
    registry.subscribe(UUID.randomUUID()).complete();

    assertThatCode(registry::sendHeartbeat).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("a completed stream is dropped so later events skip it")
  void completedStreamsAreDropped() {
    final var registry = new RunStatusSseRegistry();
    final var runId = UUID.randomUUID();
    registry.subscribe(runId).complete();
    registry.notify(runId);

    // A second pass must find nothing left to send to.
    assertThatCode(
            () -> {
              registry.notify(runId);
              registry.complete(runId);
            })
        .doesNotThrowAnyException();
  }
}
