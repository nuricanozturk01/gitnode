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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.gitnode.os.actions.entities.Runner;
import dev.gitnode.os.actions.entities.RunnerStatus;
import dev.gitnode.os.actions.entities.WorkflowJob;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;

/**
 * Explains why a queued job is not being picked up. Without this a job that no runner can take just
 * stays "queued" with nothing in the logs. Each job is reported at most once per interval so a
 * stuck job does not flood the log.
 */
@Slf4j
@NullMarked
final class DispatchDiagnostics {

  private static final Duration DEFAULT_INTERVAL = Duration.ofMinutes(1);

  private static final long MAX_TRACKED_JOBS = 10_000;

  /** Jobs warned about within the interval; entries expire by themselves, the size is capped. */
  private final Cache<UUID, Boolean> recentlyWarned;

  private final Supplier<Instant> clock;

  DispatchDiagnostics() {
    this(Instant::now, DEFAULT_INTERVAL);
  }

  DispatchDiagnostics(final Supplier<Instant> clock, final Duration interval) {
    this.clock = clock;
    this.recentlyWarned =
        Caffeine.newBuilder()
            .maximumSize(MAX_TRACKED_JOBS)
            .expireAfterWrite(interval)
            .ticker(() -> Duration.between(Instant.EPOCH, clock.get()).toNanos())
            .build();
  }

  long trackedJobs() {
    this.recentlyWarned.cleanUp();
    return this.recentlyWarned.estimatedSize();
  }

  /** Logs a warning for the job unless one was logged within the interval. */
  void warn(final WorkflowJob job, final String reason) {
    if (this.recentlyWarned.asMap().putIfAbsent(job.getId(), Boolean.TRUE) != null) {
      return;
    }
    log.warn(
        "Job '{}' ({}) is queued and not being picked up: {}. Required labels={}",
        job.getName(),
        job.getId(),
        reason,
        job.getRunnerLabels());
  }

  /** One line per runner saying whether it can take the job and, if not, why not. */
  static String describeRunners(
      final List<Runner> runners, final List<String> required, final UUID tenantId) {

    if (runners.isEmpty()) {
      return "no runner is registered";
    }
    return runners.stream()
        .map(r -> r.getName() + " -> " + explain(r, required, tenantId))
        .collect(Collectors.joining("; "));
  }

  /** The reason a runner cannot take a job, or "matches" when it can. */
  static String explain(final Runner runner, final List<String> required, final UUID tenantId) {
    if (runner.getStatus() != RunnerStatus.ONLINE) {
      return "status is "
          + runner.getStatus()
          + " (last heartbeat "
          + runner.getLastHeartbeat()
          + ")";
    }
    if (!tenantId.equals(runner.getTenantId())) {
      return "registered for another owner (runner tenant "
          + runner.getTenantId()
          + ", repository owner "
          + tenantId
          + ")";
    }
    final var missing = required.stream().filter(l -> !runner.getLabels().contains(l)).toList();
    if (!missing.isEmpty()) {
      return "missing labels " + missing + " (has " + runner.getLabels() + ")";
    }
    return "matches";
  }
}
