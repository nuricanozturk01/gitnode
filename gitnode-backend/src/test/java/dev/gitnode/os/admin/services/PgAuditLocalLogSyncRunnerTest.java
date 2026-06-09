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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("PgAuditLocalLogSyncRunner unit tests")
class PgAuditLocalLogSyncRunnerTest {

  @Test
  @DisplayName("hasLogFiles returns false for missing directory")
  void hasLogFiles_returnsFalse_whenDirectoryMissing(@TempDir Path tempDir) {
    assertThat(PgAuditLocalLogSyncRunner.hasLogFiles(tempDir.resolve("missing"))).isFalse();
  }

  @Test
  @DisplayName("hasLogFiles detects postgres log files")
  void hasLogFiles_returnsTrue_whenLogPresent(@TempDir Path tempDir) throws Exception {
    Files.writeString(tempDir.resolve("postgresql-2026-06-05.log"), "line");

    assertThat(PgAuditLocalLogSyncRunner.hasLogFiles(tempDir)).isTrue();
  }

  @Test
  @DisplayName("hasLogFiles ignores non-log files")
  void hasLogFiles_returnsFalse_whenOnlyOtherFiles(@TempDir Path tempDir) throws Exception {
    Files.writeString(tempDir.resolve("readme.txt"), "hello");

    assertThat(PgAuditLocalLogSyncRunner.hasLogFiles(tempDir)).isFalse();
  }

  @Test
  @DisplayName("syncPostgresLogsOnStartup skips when sync disabled")
  void syncPostgresLogsOnStartup_skips_whenDisabled(@TempDir Path tempDir) {
    var logDir = tempDir.resolve("pgaudit-logs");
    var runner = new PgAuditLocalLogSyncRunner(logDir.toString(), "gitnode-postgres", false);

    runner.syncPostgresLogsOnStartup();

    assertThat(Files.exists(logDir)).isFalse();
  }

  @Test
  @DisplayName("syncPostgresLogsOnStartup skips when log directory not configured")
  void syncPostgresLogsOnStartup_skips_whenDirectoryBlank() {
    var runner = new PgAuditLocalLogSyncRunner("", "gitnode-postgres", true);

    runner.syncPostgresLogsOnStartup();
  }

  @Test
  @DisplayName("syncPostgresLogsOnStartup skips docker when logs already exist")
  void syncPostgresLogsOnStartup_skipsDocker_whenLogsPresent(@TempDir Path tempDir)
      throws Exception {
    Files.writeString(tempDir.resolve("postgresql.log"), "existing");
    var runner = new PgAuditLocalLogSyncRunner(tempDir.toString(), "gitnode-postgres", true);

    runner.syncPostgresLogsOnStartup();

    assertThat(Files.readString(tempDir.resolve("postgresql.log"))).isEqualTo("existing");
  }
}
