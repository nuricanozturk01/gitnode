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

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.gitnode.os.actions.entities.Runner;
import dev.gitnode.os.actions.entities.RunnerStatus;
import dev.gitnode.os.actions.entities.WorkflowJob;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

@DisplayName("DispatchDiagnostics")
class DispatchDiagnosticsTest {

  private static final UUID TENANT = UUID.randomUUID();

  private final AtomicReference<Instant> now =
      new AtomicReference<>(Instant.parse("2026-11-04T12:00:00Z"));
  private final DispatchDiagnostics diagnostics =
      new DispatchDiagnostics(this.now::get, Duration.ofMinutes(1));
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private final Logger logger = (Logger) LoggerFactory.getLogger(DispatchDiagnostics.class);

  @BeforeEach
  void attach() {
    this.appender.start();
    this.logger.addAppender(this.appender);
  }

  @AfterEach
  void detach() {
    this.logger.detachAppender(this.appender);
  }

  private static Runner runner(
      final String name, final RunnerStatus status, final UUID tenant, final List<String> labels) {
    final var r = new Runner();
    r.setName(name);
    r.setStatus(status);
    r.setTenantId(tenant);
    r.setLabels(labels);
    r.setLastHeartbeat(Instant.parse("2026-11-04T11:59:00Z"));
    return r;
  }

  private static WorkflowJob job(final UUID id) {
    final var j = new WorkflowJob();
    j.setId(id);
    j.setName("build");
    j.setRunnerLabels(List.of("self-hosted", "linux"));
    return j;
  }

  @Test
  @DisplayName("a runner that is not ONLINE is explained by its status")
  void explain_status() {
    final var r = runner("r", RunnerStatus.OFFLINE, TENANT, List.of("self-hosted", "linux"));
    assertThat(DispatchDiagnostics.explain(r, List.of("self-hosted"), TENANT))
        .contains("status is OFFLINE");
  }

  @Test
  @DisplayName("a runner of another owner is explained by the tenant mismatch")
  void explain_tenant() {
    final var r = runner("r", RunnerStatus.ONLINE, UUID.randomUUID(), List.of("self-hosted"));
    assertThat(DispatchDiagnostics.explain(r, List.of("self-hosted"), TENANT))
        .contains("registered for another owner");
  }

  @Test
  @DisplayName("missing labels are listed")
  void explain_labels() {
    final var r = runner("r", RunnerStatus.ONLINE, TENANT, List.of("self-hosted"));
    assertThat(DispatchDiagnostics.explain(r, List.of("self-hosted", "linux"), TENANT))
        .contains("missing labels [linux]")
        .contains("has [self-hosted]");
  }

  @Test
  @DisplayName("a matching runner is reported as matching")
  void explain_matches() {
    final var r = runner("r", RunnerStatus.ONLINE, TENANT, List.of("self-hosted", "linux", "x"));
    assertThat(DispatchDiagnostics.explain(r, List.of("self-hosted", "linux"), TENANT))
        .isEqualTo("matches");
  }

  @Test
  @DisplayName("describeRunners names every runner, or says none is registered")
  void describeRunners() {
    assertThat(DispatchDiagnostics.describeRunners(List.of(), List.of("a"), TENANT))
        .isEqualTo("no runner is registered");
    final var text =
        DispatchDiagnostics.describeRunners(
            List.of(
                runner("alpha", RunnerStatus.ONLINE, TENANT, List.of("a")),
                runner("beta", RunnerStatus.OFFLINE, TENANT, List.of("a"))),
            List.of("a"),
            TENANT);
    assertThat(text).contains("alpha -> matches").contains("beta -> status is OFFLINE");
  }

  @Test
  @DisplayName("warns with the job, the reason and the required labels")
  void warn_logsReason() {
    this.diagnostics.warn(job(UUID.randomUUID()), "no runner can take it");

    assertThat(this.appender.list).hasSize(1);
    final var event = this.appender.list.get(0);
    assertThat(event.getLevel()).isEqualTo(Level.WARN);
    assertThat(event.getFormattedMessage())
        .contains("'build'")
        .contains("no runner can take it")
        .contains("[self-hosted, linux]");
  }

  @Test
  @DisplayName("the same job is reported at most once per interval")
  void warn_throttled() {
    final var j = job(UUID.randomUUID());
    this.diagnostics.warn(j, "x");
    this.now.set(this.now.get().plusSeconds(30));
    this.diagnostics.warn(j, "x");
    assertThat(this.appender.list).hasSize(1);

    this.now.set(this.now.get().plusSeconds(31));
    this.diagnostics.warn(j, "x");
    assertThat(this.appender.list).hasSize(2);
  }

  @Test
  @DisplayName("different jobs are throttled independently")
  void warn_perJob() {
    this.diagnostics.warn(job(UUID.randomUUID()), "x");
    this.diagnostics.warn(job(UUID.randomUUID()), "x");
    assertThat(this.appender.list).hasSize(2);
  }

  @Test
  @DisplayName("jobs that left the queue are forgotten and warned about again if re-queued")
  void retainOnly_forgets() {
    final var id = UUID.randomUUID();
    this.diagnostics.warn(job(id), "x");
    this.diagnostics.retainOnly(List.of());
    this.diagnostics.warn(job(id), "x");
    assertThat(this.appender.list).hasSize(2);
  }
}
