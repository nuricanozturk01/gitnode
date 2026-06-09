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
import static org.mockito.Mockito.when;

import dev.gitnode.os.admin.dtos.PgAuditStatusReason;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PgAuditLogReaderService unit tests")
class PgAuditLogReaderServiceTest {

  @Mock private AdminPlatformSettingsService adminPlatformSettingsService;

  private static final String SAMPLE_LINE =
      "2026-06-05 10:15:30.123 UTC [42]: [1-1] user=admin,db=gitnode,app=[unknown],client=172.18.0.2"
          + " LOG:  AUDIT: SESSION,1,1,WRITE,INSERT,TABLE,public.accounts,\"INSERT INTO accounts"
          + " VALUES (...)\",<not logged>";

  private static final String SAMPLE_LINE_WITHOUT_CONNECTION =
      "2026-06-05 19:01:10.106 UTC [60] LOG:  AUDIT: SESSION,1,1,DDL,CREATE DATABASE,,,\"CREATE"
          + " DATABASE \"\"gitnode\"\"\",<not logged>";

  @Test
  @DisplayName("parses standard pgAudit log lines")
  void parseLine_parsesStandardPgAuditLine() {
    var entry = PgAuditLogReaderService.parseLine(SAMPLE_LINE);

    assertThat(entry).isNotNull();
    assertThat(entry.dbUser()).isEqualTo("admin");
    assertThat(entry.database()).isEqualTo("gitnode");
    assertThat(entry.client()).isEqualTo("172.18.0.2");
    assertThat(entry.category()).isEqualTo("WRITE");
    assertThat(entry.command()).isEqualTo("INSERT");
    assertThat(entry.objectType()).isEqualTo("TABLE");
    assertThat(entry.objectName()).isEqualTo("public.accounts");
    assertThat(entry.statement()).contains("INSERT INTO accounts");
  }

  @Test
  @DisplayName("parses pgAudit lines without user/db connection info")
  void parseLine_parsesLineWithoutConnectionInfo() {
    var entry = PgAuditLogReaderService.parseLine(SAMPLE_LINE_WITHOUT_CONNECTION);

    assertThat(entry).isNotNull();
    assertThat(entry.dbUser()).isNull();
    assertThat(entry.database()).isNull();
    assertThat(entry.category()).isEqualTo("DDL");
    assertThat(entry.command()).isEqualTo("CREATE DATABASE");
    assertThat(entry.statement()).contains("CREATE DATABASE \"gitnode\"");
  }

  @Test
  @DisplayName("extracts multi-line audit records")
  void extractAuditRecords_joinsContinuationLines(@TempDir Path tempDir) throws Exception {
    var log =
"""
2026-06-05 19:01:10.106 UTC [60] LOG:  AUDIT: SESSION,1,1,DDL,CREATE TABLE,,,"CREATE TABLE audit_logs (
    id bigint primary key
)",<not logged>
""";
    Files.writeString(tempDir.resolve("postgresql-2026-06-05.log"), log);

    var records =
        PgAuditLogReaderService.extractAuditRecords(
            tempDir.resolve("postgresql-2026-06-05.log"), 100);

    assertThat(records).hasSize(1);
    assertThat(records.get(0)).contains("CREATE TABLE audit_logs");
    assertThat(records.get(0)).contains("id bigint primary key");
  }

  @Test
  @DisplayName("returns unavailable status when viewer is disabled")
  void status_returnsUnavailable_whenDisabled() {
    when(adminPlatformSettingsService.isPgAuditViewerEnabled()).thenReturn(false);
    var service = new PgAuditLogReaderService(adminPlatformSettingsService, "", 10_000);

    var status = service.status();

    assertThat(status.available()).isFalse();
    assertThat(status.reason()).isEqualTo(PgAuditStatusReason.VIEWER_DISABLED);
  }

  @Test
  @DisplayName("creates log directory when missing")
  void status_createsDirectory_whenMissing(@TempDir Path tempDir) {
    when(adminPlatformSettingsService.isPgAuditViewerEnabled()).thenReturn(true);
    final Path logDir = tempDir.resolve("nested/postgres-logs");
    var service =
        new PgAuditLogReaderService(adminPlatformSettingsService, logDir.toString(), 10_000);

    var status = service.status();

    assertThat(status.available()).isTrue();
    assertThat(Files.isDirectory(logDir)).isTrue();
    assertThat(status.message()).contains("no PostgreSQL log files");
  }

  @Test
  @DisplayName("returns unavailable status when log directory is not configured")
  void status_returnsUnavailable_whenDirectoryMissing() {
    when(adminPlatformSettingsService.isPgAuditViewerEnabled()).thenReturn(true);
    var service = new PgAuditLogReaderService(adminPlatformSettingsService, "", 10_000);

    var status = service.status();

    assertThat(status.available()).isFalse();
    assertThat(status.message()).contains("not configured");
    assertThat(status.reason()).isEqualTo(PgAuditStatusReason.LOG_DIRECTORY_NOT_CONFIGURED);
  }

  @Test
  @DisplayName("does not scan log files when viewer is disabled")
  void search_skipsFileScan_whenDisabled(@TempDir Path tempDir) throws Exception {
    when(adminPlatformSettingsService.isPgAuditViewerEnabled()).thenReturn(false);
    Files.writeString(
        tempDir.resolve("postgresql-2026-06-05.log"), SAMPLE_LINE + System.lineSeparator());

    var service =
        new PgAuditLogReaderService(adminPlatformSettingsService, tempDir.toString(), 10_000);
    var response = service.search(0, 20, null, null, null, null, null);

    assertThat(response.available()).isFalse();
    assertThat(response.content()).isEmpty();
  }

  @Test
  void search_readsEntriesFromLogDirectory(@TempDir Path tempDir) throws Exception {
    when(adminPlatformSettingsService.isPgAuditViewerEnabled()).thenReturn(true);
    Files.writeString(
        tempDir.resolve("postgresql-2026-06-05.log"), SAMPLE_LINE + System.lineSeparator());

    var service =
        new PgAuditLogReaderService(adminPlatformSettingsService, tempDir.toString(), 10_000);
    var response = service.search(0, 20, null, null, null, null, null);

    assertThat(response.available()).isTrue();
    assertThat(response.content()).hasSize(1);
    assertThat(response.content().get(0).category()).isEqualTo("WRITE");
  }

  @Test
  @DisplayName("reads entries from logs without connection info")
  void search_readsEntriesWithoutConnectionInfo(@TempDir Path tempDir) throws Exception {
    when(adminPlatformSettingsService.isPgAuditViewerEnabled()).thenReturn(true);
    Files.writeString(
        tempDir.resolve("postgresql-2026-06-05.log"),
        SAMPLE_LINE_WITHOUT_CONNECTION + System.lineSeparator());

    var service =
        new PgAuditLogReaderService(adminPlatformSettingsService, tempDir.toString(), 10_000);
    var response = service.search(0, 20, null, null, null, null, null);

    assertThat(response.available()).isTrue();
    assertThat(response.content()).hasSize(1);
    assertThat(response.content().get(0).category()).isEqualTo("DDL");
  }

  @Test
  @DisplayName("filters by database user and category")
  void search_appliesFilters(@TempDir Path tempDir) throws Exception {
    when(adminPlatformSettingsService.isPgAuditViewerEnabled()).thenReturn(true);
    var ddlLine =
        "2026-06-05 10:16:00.000 UTC [43] user=postgres,db=gitnode LOG:  AUDIT: SESSION,2,1,DDL,CREATE,TABLE,public.audit_logs,\"CREATE TABLE audit_logs (...)\"";
    Files.writeString(
        tempDir.resolve("postgresql-2026-06-05.log"),
        SAMPLE_LINE + System.lineSeparator() + ddlLine + System.lineSeparator());

    var service =
        new PgAuditLogReaderService(adminPlatformSettingsService, tempDir.toString(), 10_000);

    var ddlOnly = service.search(0, 20, null, "postgres", "DDL", null, null);
    assertThat(ddlOnly.content()).hasSize(1);
    assertThat(ddlOnly.content().get(0).command()).isEqualTo("CREATE");
  }

  @Test
  @DisplayName("caps page size at 50")
  void cappedSize_capsAt50() {
    assertThat(PgAuditLogReaderService.cappedSize(200)).isEqualTo(50);
    assertThat(PgAuditLogReaderService.cappedSize(10)).isEqualTo(10);
  }

  @Test
  @DisplayName("paginates without loading all entries into memory")
  void search_returnsRequestedPage(@TempDir Path tempDir) throws Exception {
    when(adminPlatformSettingsService.isPgAuditViewerEnabled()).thenReturn(true);
    var builder = new StringBuilder();
    for (int index = 0; index < 30; index++) {
      builder
          .append("2026-06-05 10:15:")
          .append(String.format("%02d", index))
          .append(".000 UTC [")
          .append(index)
          .append("] LOG:  AUDIT: SESSION,1,1,WRITE,INSERT,TABLE,public.t")
          .append(index)
          .append(",\"INSERT INTO t")
          .append(index)
          .append("\",<not logged>")
          .append(System.lineSeparator());
    }
    Files.writeString(tempDir.resolve("postgresql-2026-06-05.log"), builder.toString());

    var service =
        new PgAuditLogReaderService(adminPlatformSettingsService, tempDir.toString(), 10_000);

    var firstPage = service.search(0, 10, null, null, null, null, null);
    var secondPage = service.search(1, 10, null, null, null, null, null);

    assertThat(firstPage.totalElements()).isEqualTo(30);
    assertThat(firstPage.content()).hasSize(10);
    assertThat(secondPage.content()).hasSize(10);
    assertThat(firstPage.content().get(0).occurredAt())
        .isAfter(secondPage.content().get(0).occurredAt());
  }
}
