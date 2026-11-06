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
package dev.gitnode.os.shared.configs;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigTest {

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final AsyncConfig config = new AsyncConfig(this.meters);

  @Test
  void loggingContextFollowsTheTaskOntoTheWorkerThread() throws Exception {
    final TaskDecorator decorator = this.config.asyncTaskDecorator();
    final var seen = new AtomicReference<String>();
    MDC.put("requestId", "req-42");
    final Runnable decorated = decorator.decorate(() -> seen.set(MDC.get("requestId")));
    MDC.clear();

    final var worker = new Thread(decorated);
    worker.start();
    worker.join();

    assertThat(seen.get()).isEqualTo("req-42");
    assertThat(MDC.get("requestId")).isNull();
  }

  @Test
  void aSaturatedAuditQueueDropsAndCountsInsteadOfBlocking() throws Exception {
    final Executor audit = this.config.auditExecutor();
    final var release = new CountDownLatch(1);
    // 4 workers + 2000 queue slots fill up; the next submissions must be dropped, not run inline.
    final int total = 4 + 2000 + 25;
    for (int i = 0; i < total; i++) {
      audit.execute(
          () -> {
            try {
              release.await(10, TimeUnit.SECONDS);
            } catch (final InterruptedException ignored) {
              Thread.currentThread().interrupt();
            }
          });
    }
    final double dropped =
        this.meters.counter("gitnode.async.dropped", "executor", "auditExecutor").count();
    release.countDown();
    ((ThreadPoolTaskExecutor) audit).shutdown();

    assertThat(dropped).isGreaterThan(0);
  }

  @Test
  void aSaturatedWebhookPoolRunsTheTaskOnTheCallingThread() {
    final var webhook = (ThreadPoolTaskExecutor) this.config.webhookExecutor();
    final var release = new CountDownLatch(1);
    // 16 workers and 1000 queue slots are the capacity; fill all of it with blocked tasks
    for (int i = 0; i < 16 + 1000; i++) {
      webhook.execute(
          () -> {
            try {
              release.await(10, TimeUnit.SECONDS);
            } catch (final InterruptedException ignored) {
              Thread.currentThread().interrupt();
            }
          });
    }

    final var ranOn = new AtomicReference<Thread>();
    webhook.execute(() -> ranOn.set(Thread.currentThread()));

    release.countDown();
    webhook.shutdown();
    assertThat(ranOn.get()).isSameAs(Thread.currentThread());
  }
}
