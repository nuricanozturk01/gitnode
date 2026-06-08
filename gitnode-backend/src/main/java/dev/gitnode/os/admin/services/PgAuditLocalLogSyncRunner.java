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
package dev.gitnode.os.admin.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
@NullMarked
@Slf4j
public class PgAuditLocalLogSyncRunner {

  private static final String DEFAULT_CONTAINER = "gitnode-postgres";

  private final @Nullable String logDirectory;
  private final String postgresContainer;
  private final boolean syncOnStartup;

  PgAuditLocalLogSyncRunner(
      @Value("${gitnode.admin.pgaudit.log-directory:}") final String logDirectory,
      @Value("${gitnode.admin.pgaudit.local-sync-container:gitnode-postgres}")
          final String postgresContainer,
      @Value("${gitnode.admin.pgaudit.local-sync-on-startup:true}") final boolean syncOnStartup) {

    this.logDirectory = logDirectory.isBlank() ? null : logDirectory.trim();
    this.postgresContainer =
        postgresContainer.isBlank() ? DEFAULT_CONTAINER : postgresContainer.trim();
    this.syncOnStartup = syncOnStartup;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void syncPostgresLogsOnStartup() {

    if (!this.syncOnStartup || this.logDirectory == null) {
      return;
    }

    final Path directory = Path.of(this.logDirectory);
    try {
      Files.createDirectories(directory);
    } catch (IOException ex) {
      log.warn("Could not create pgAudit log directory {}: {}", directory, ex.getMessage());
      return;
    }

    if (hasLogFiles(directory)) {
      return;
    }

    if (!this.syncFromDockerContainer(directory)) {
      log.info(
          "pgAudit log directory {} is empty. Start Postgres (make infra) then run `make"
              + " sync-postgres-logs`.",
          directory);
    }
  }

  static boolean hasLogFiles(final Path directory) {

    if (!Files.isDirectory(directory)) {
      return false;
    }

    try (var paths = Files.list(directory)) {
      return paths
          .filter(Files::isRegularFile)
          .anyMatch(
              path -> {
                final String name = path.getFileName().toString().toLowerCase();
                return name.endsWith(".log") || name.endsWith(".csv");
              });
    } catch (IOException ex) {
      return false;
    }
  }

  private boolean syncFromDockerContainer(final Path directory) {

    final Process process;
    try {
      process =
          new ProcessBuilder(
                  "docker",
                  "cp",
                  this.postgresContainer + ":/var/log/postgresql/.",
                  directory.toString())
              .redirectErrorStream(true)
              .start();
    } catch (IOException ex) {
      log.debug("Could not sync pgAudit logs via docker: {}", ex.getMessage());
      return false;
    }

    try {
      final boolean finished = process.waitFor(30, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        log.warn("Timed out syncing pgAudit logs from container {}", this.postgresContainer);
        return false;
      }

      if (process.exitValue() != 0) {
        log.debug(
            "Skipped automatic pgAudit log sync from {} (exit code {}). Is `make infra` running?",
            this.postgresContainer,
            process.exitValue());
        return false;
      }

      if (hasLogFiles(directory)) {
        log.info("Synced pgAudit logs from {} into {}", this.postgresContainer, directory);
        return true;
      }

      log.info(
          "Synced from {} but no log files were found yet. Postgres may still be initializing.",
          this.postgresContainer);
      return false;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      return false;
    }
  }
}
