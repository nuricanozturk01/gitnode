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
package dev.gitnode.os.actions.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.gitnode.os.actions.websocket.ServerMessage;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tells a runner to do something over the same Redis channel jobs are dispatched on, so it reaches
 * the runner whichever backend instance holds its WebSocket.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
class RunnerCommands {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final StringRedisTemplate redisTemplate;

  /** Asks the runner to stop the job; sent once the current transaction commits. */
  void cancelJob(final UUID runnerId, final UUID jobId) {

    final Runnable send =
        () -> {
          try {
            final var json =
                MAPPER.writeValueAsString(ServerMessage.jobCancelled(jobId.toString()));
            this.redisTemplate.convertAndSend(
                JobDispatcher.DISPATCH_CHANNEL_PREFIX + runnerId, json);
            log.info("Cancel sent to runner: jobId={}, runnerId={}", jobId, runnerId);
          } catch (final Exception ex) {
            log.error("Failed to send cancel: jobId={}, runnerId={}", jobId, runnerId, ex);
          }
        };

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              send.run();
            }
          });
    } else {
      send.run();
    }
  }
}
