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
package com.nuricanozturk.originhub.admin.services;

import com.nuricanozturk.originhub.admin.dtos.PgAuditLogEntry;
import com.nuricanozturk.originhub.admin.dtos.PgAuditLogSearchResponse;
import com.nuricanozturk.originhub.admin.dtos.PgAuditLogStatus;
import com.nuricanozturk.originhub.admin.dtos.PgAuditStatusReason;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@NullMarked
public class PgAuditLogReaderService {

  static final int MAX_PAGE_SIZE = 50;
  private static final int MAX_FRACTION_DIGITS = 6;
  private static final int MIN_SCAN_LINES = 1_000;
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int FALLBACK_TS_LENGTH = 23;

  private static final DateTimeFormatter LOG_TIMESTAMP =
      new DateTimeFormatterBuilder()
          .appendPattern("yyyy-MM-dd HH:mm:ss")
          .appendFraction(ChronoField.MILLI_OF_SECOND, 1, MAX_FRACTION_DIGITS, true)
          .appendLiteral(' ')
          .appendPattern("z")
          .toFormatter(Locale.US)
          .withZone(ZoneOffset.UTC);

  private static final Pattern PG_LOG_LINE_START =
      Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.");

  private static final Pattern PG_LOG_HEADER =
      Pattern.compile("^(?<ts>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d+ \\S+)");

  private static final Pattern PG_CONNECTION_INFO =
      Pattern.compile("user=(?<user>[^,]*),db=(?<db>[^,]*)(?:,.*?client=(?<client>[^\\s]+))?");

  private static final Pattern CATEGORY =
      Pattern.compile(",(?<category>WRITE|DDL|ROLE|READ|MISC|FUNCTION|VIEW|SEQUENCE),");

  private final AdminPlatformSettingsService adminPlatformSettingsService;
  private final @Nullable String logDirectory;
  private final int maxScanLines;

  PgAuditLogReaderService(
      final AdminPlatformSettingsService adminPlatformSettingsService,
      @Value("${originhub.admin.pgaudit.log-directory:}") final String logDirectory,
      @Value("${originhub.admin.pgaudit.max-scan-lines:50000}") final int maxScanLines) {

    this.adminPlatformSettingsService = adminPlatformSettingsService;
    this.logDirectory = logDirectory.isBlank() ? null : logDirectory.trim();
    this.maxScanLines = Math.max(MIN_SCAN_LINES, maxScanLines);
  }

  public PgAuditLogStatus status() {

    final boolean viewerEnabled = this.adminPlatformSettingsService.isPgAuditViewerEnabled();
    if (!viewerEnabled) {
      return new PgAuditLogStatus(
          false,
          false,
          PgAuditStatusReason.VIEWER_DISABLED,
          this.logDirectory,
          "pgAudit viewer is turned off. Enable it under Platform settings → Diagnostic viewers.");
    }

    if (this.logDirectory == null) {
      return new PgAuditLogStatus(
          true,
          false,
          PgAuditStatusReason.LOG_DIRECTORY_NOT_CONFIGURED,
          null,
          "PostgreSQL log directory is not configured on the server. Set"
              + " ORIGINHUB_ADMIN_PGAUDIT_LOG_DIRECTORY and mount the Postgres log volume into the"
              + " app container (see make up / docker-compose).");
    }

    final Path directory = this.ensureLogDirectory();
    if (directory == null) {
      return new PgAuditLogStatus(
          true,
          false,
          PgAuditStatusReason.LOG_DIRECTORY_NOT_FOUND,
          this.logDirectory,
          "Could not create log directory: "
              + this.logDirectory
              + " — check permissions or set ORIGINHUB_ADMIN_PGAUDIT_LOG_DIRECTORY.");
    }

    if (!Files.isReadable(directory)) {
      return new PgAuditLogStatus(
          true,
          false,
          PgAuditStatusReason.LOG_DIRECTORY_NOT_READABLE,
          this.logDirectory,
          "Log directory exists but is not readable by the app process: " + this.logDirectory);
    }

    if (!hasLogFiles(directory)) {
      return new PgAuditLogStatus(
          true,
          true,
          PgAuditStatusReason.READY,
          this.logDirectory,
          "Log directory is ready but contains no PostgreSQL log files yet. Start Postgres (make"
              + " infra) and run `make sync-postgres-logs`, then refresh this page.");
    }

    return new PgAuditLogStatus(
        true,
        true,
        PgAuditStatusReason.READY,
        this.logDirectory,
        "Reading PostgreSQL server logs written by pgAudit (write, ddl, role).");
  }

  private @Nullable Path ensureLogDirectory() {

    if (this.logDirectory == null) {
      return null;
    }

    final Path directory = Path.of(this.logDirectory);
    if (Files.isDirectory(directory)) {
      return directory;
    }

    try {
      Files.createDirectories(directory);
      return Files.isDirectory(directory) ? directory : null;
    } catch (IOException ignored) {
      return null;
    }
  }

  private static boolean hasLogFiles(final Path directory) {

    return PgAuditLocalLogSyncRunner.hasLogFiles(directory);
  }

  public PgAuditLogSearchResponse search(
      final int page,
      final int size,
      final @Nullable String q,
      final @Nullable String dbUser,
      final @Nullable String category,
      final @Nullable Instant from,
      final @Nullable Instant to) {

    final var availability = this.status();
    if (!availability.available()) {
      return emptyResponse(page, cappedSize(size), availability.message());
    }

    final int pageSize = cappedSize(size);
    final int safePage = Math.max(page, 0);
    final SearchResult result =
        this.readMatchingEntries(
            Path.of(availability.logDirectory()),
            safePage,
            pageSize,
            blankToNull(q),
            blankToNull(dbUser),
            blankToNull(category),
            from,
            to);

    final int totalPages =
        pageSize == 0 ? 0 : (int) Math.ceil((double) result.totalMatches() / pageSize);

    return new PgAuditLogSearchResponse(
        result.pageContent(),
        safePage,
        pageSize,
        result.totalMatches(),
        totalPages,
        true,
        availability.message());
  }

  static int cappedSize(final int size) {

    if (size <= 0) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(size, MAX_PAGE_SIZE);
  }

  private SearchResult readMatchingEntries(
      final Path directory,
      final int page,
      final int pageSize,
      final @Nullable String q,
      final @Nullable String dbUser,
      final @Nullable String category,
      final @Nullable Instant from,
      final @Nullable Instant to) {

    final var logFiles = listLogFiles(directory);
    if (logFiles.isEmpty()) {
      return new SearchResult(List.of(), 0);
    }

    final int bufferCapacity = Math.min((page + 1) * pageSize, this.maxScanLines);
    final Deque<PgAuditLogEntry> tailBuffer = new ArrayDeque<>(Math.min(bufferCapacity, 64));
    final AtomicLong totalMatches = new AtomicLong();
    var scannedLines = 0;

    for (final Path file : logFiles) {
      if (scannedLines >= this.maxScanLines) {
        break;
      }

      scannedLines +=
          scanAuditRecords(
              file,
              this.maxScanLines - scannedLines,
              record -> {
                final @Nullable PgAuditLogEntry entry = parseLine(record);
                if (entry == null || !matchesFilters(entry, q, dbUser, category, from, to)) {
                  return;
                }

                totalMatches.incrementAndGet();
                if (tailBuffer.size() == bufferCapacity) {
                  tailBuffer.removeFirst();
                }
                tailBuffer.addLast(entry);
              });
    }

    final long matchCount = totalMatches.get();
    return new SearchResult(slicePage(tailBuffer, page, pageSize, matchCount), matchCount);
  }

  static List<PgAuditLogEntry> slicePage(
      final Deque<PgAuditLogEntry> tailBuffer,
      final int page,
      final int pageSize,
      final long totalMatches) {

    if (totalMatches == 0 || pageSize <= 0 || tailBuffer.isEmpty()) {
      return List.of();
    }

    final long pageStartAsc = Math.max(0, totalMatches - ((long) (page + 1) * pageSize));
    final long pageEndAsc = Math.min(totalMatches, totalMatches - (long) page * pageSize);
    if (pageStartAsc >= pageEndAsc) {
      return List.of();
    }

    final long bufferStartAsc = totalMatches - tailBuffer.size();
    final int fromIndex = (int) Math.max(0, pageStartAsc - bufferStartAsc);
    final int toIndex = (int) Math.min(tailBuffer.size(), pageEndAsc - bufferStartAsc);
    if (fromIndex >= toIndex) {
      return List.of();
    }

    return collectPageContent(tailBuffer, fromIndex, toIndex);
  }

  private static List<PgAuditLogEntry> collectPageContent(
      final Deque<PgAuditLogEntry> tailBuffer, final int fromIndex, final int toIndex) {

    final var pageContent = new ArrayList<PgAuditLogEntry>(toIndex - fromIndex);
    var index = 0;
    for (final PgAuditLogEntry entry : tailBuffer) {
      if (index >= fromIndex && index < toIndex) {
        pageContent.add(entry);
      }
      index++;
    }
    pageContent.sort(Comparator.comparing(PgAuditLogEntry::occurredAt).reversed());
    return List.copyOf(pageContent);
  }

  static int scanAuditRecords(
      final Path file, final int maxScanLines, final Consumer<String> recordConsumer) {

    final var recordBuilder = new StringBuilder(256);
    var scannedLines = 0;

    try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
      final var iterator = lines.iterator();
      while (iterator.hasNext() && scannedLines < maxScanLines) {
        processLogLine(iterator.next(), recordBuilder, recordConsumer);
        scannedLines++;
      }
    } catch (IOException ex) {
      return scannedLines;
    }

    flushAuditRecord(recordBuilder, recordConsumer);
    return scannedLines;
  }

  private static void processLogLine(
      final String line, final StringBuilder recordBuilder, final Consumer<String> recordConsumer) {

    if (isPgLogLineStart(line)) {
      flushAuditRecord(recordBuilder, recordConsumer);
      if (line.contains("AUDIT:")) {
        recordBuilder.append(line.trim());
        if (line.contains("<not logged>")) {
          flushAuditRecord(recordBuilder, recordConsumer);
        }
      }
      return;
    }

    if (!recordBuilder.isEmpty()) {
      recordBuilder.append('\n').append(line);
      if (line.contains("<not logged>")) {
        flushAuditRecord(recordBuilder, recordConsumer);
      }
    }
  }

  private static void flushAuditRecord(
      final StringBuilder recordBuilder, final Consumer<String> recordConsumer) {

    if (recordBuilder.isEmpty()) {
      return;
    }
    recordConsumer.accept(recordBuilder.toString());
    recordBuilder.setLength(0);
  }

  static List<String> extractAuditRecords(final Path file, final int maxScanLines) {

    final var records = new ArrayList<String>();
    scanAuditRecords(file, maxScanLines, records::add);
    return records;
  }

  private static boolean isPgLogLineStart(final String line) {

    return PG_LOG_LINE_START.matcher(line).lookingAt();
  }

  private static List<Path> listLogFiles(final Path directory) {

    try (Stream<Path> paths = Files.list(directory)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(
              path -> {
                final String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.endsWith(".log") || name.endsWith(".csv");
              })
          .sorted(Comparator.comparing((Path path) -> path.toFile().lastModified()).reversed())
          .toList();
    } catch (IOException ex) {
      return List.of();
    }
  }

  static @Nullable PgAuditLogEntry parseLine(final String line) {

    final String trimmed = line.trim();
    final Matcher headerMatcher = PG_LOG_HEADER.matcher(trimmed);
    if (!headerMatcher.find()) {
      return null;
    }

    final Instant occurredAt = parseTimestamp(headerMatcher.group("ts"));
    if (occurredAt == null) {
      return null;
    }

    final int auditIndex = trimmed.indexOf("AUDIT:");
    if (auditIndex < 0) {
      return null;
    }

    final String payload = trimmed.substring(auditIndex + "AUDIT:".length()).trim();
    final ParsedPayload parsed = parsePayload(payload);

    @Nullable String user = null;
    @Nullable String db = null;
    @Nullable String client = null;
    final Matcher connectionMatcher = PG_CONNECTION_INFO.matcher(trimmed);
    if (connectionMatcher.find()) {
      user = blankToNull(connectionMatcher.group("user"));
      db = blankToNull(connectionMatcher.group("db"));
      client = blankToNull(connectionMatcher.group("client"));
    }

    final String id =
        UUID.nameUUIDFromBytes(
                (occurredAt.toString() + "|" + trimmed).getBytes(StandardCharsets.UTF_8))
            .toString();

    return new PgAuditLogEntry(
        id,
        occurredAt,
        user,
        db,
        client,
        parsed.category(),
        parsed.command(),
        parsed.objectType(),
        parsed.objectName(),
        parsed.statement(),
        trimmed);
  }

  private static @Nullable Instant parseTimestamp(final String raw) {

    try {
      return LOG_TIMESTAMP.parse(raw, Instant::from);
    } catch (RuntimeException ex) {
      try {
        return LocalDateTime.parse(
                raw.substring(0, FALLBACK_TS_LENGTH),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
            .toInstant(ZoneOffset.UTC);
      } catch (RuntimeException ignored) {
        return null;
      }
    }
  }

  private static ParsedPayload parsePayload(final String payload) {

    final Matcher categoryMatcher = CATEGORY.matcher(payload);
    final @Nullable String category =
        categoryMatcher.find() ? categoryMatcher.group("category") : null;

    final List<String> parts = splitCsv(payload);
    final @Nullable String command = parts.size() > 4 ? blankToNull(parts.get(4)) : null;
    final @Nullable String objectType = parts.size() > 5 ? blankToNull(parts.get(5)) : null;
    final @Nullable String objectName = parts.size() > 6 ? blankToNull(parts.get(6)) : null;
    final @Nullable String statement = parts.size() > 7 ? blankToNull(unquote(parts.get(7))) : null;

    return new ParsedPayload(category, command, objectType, objectName, statement);
  }

  private static List<String> splitCsv(final String value) {

    final var parts = new ArrayList<String>();
    final var current = new StringBuilder();
    var inQuotes = false;

    for (int index = 0; index < value.length(); index++) {
      final char ch = value.charAt(index);
      if (ch == '"') {
        inQuotes = !inQuotes;
        current.append(ch);
        continue;
      }

      if (ch == ',' && !inQuotes) {
        parts.add(current.toString());
        current.setLength(0);
        continue;
      }

      current.append(ch);
    }

    parts.add(current.toString());
    return parts;
  }

  private static String unquote(final String value) {

    final String trimmed = value.trim();
    if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
      return trimmed.substring(1, trimmed.length() - 1).replace("\"\"", "\"");
    }
    return trimmed;
  }

  private static boolean matchesFilters(
      final PgAuditLogEntry entry,
      final @Nullable String q,
      final @Nullable String dbUser,
      final @Nullable String category,
      final @Nullable Instant from,
      final @Nullable Instant to) {

    return matchesDbUser(entry, dbUser)
        && matchesCategory(entry, category)
        && matchesTimeRange(entry, from, to)
        && matchesQuery(entry, q);
  }

  private static boolean matchesDbUser(final PgAuditLogEntry entry, final @Nullable String dbUser) {
    if (dbUser == null) {
      return true;
    }
    return entry.dbUser() != null
        && entry.dbUser().toLowerCase(Locale.ROOT).contains(dbUser.toLowerCase(Locale.ROOT));
  }

  private static boolean matchesCategory(
      final PgAuditLogEntry entry, final @Nullable String category) {
    if (category == null) {
      return true;
    }
    return entry.category() != null && entry.category().equalsIgnoreCase(category);
  }

  private static boolean matchesTimeRange(
      final PgAuditLogEntry entry, final @Nullable Instant from, final @Nullable Instant to) {

    if (from != null && entry.occurredAt().isBefore(from)) {
      return false;
    }
    if (to != null && entry.occurredAt().isAfter(to)) {
      return false;
    }
    return true;
  }

  private static boolean matchesQuery(final PgAuditLogEntry entry, final @Nullable String q) {
    if (q == null) {
      return true;
    }
    final String query = q.toLowerCase(Locale.ROOT);
    return containsIgnoreCase(entry.rawLine(), query) || matchesQueryInFields(entry, query);
  }

  private static boolean matchesQueryInFields(final PgAuditLogEntry entry, final String query) {
    return containsIgnoreCase(entry.dbUser(), query)
        || containsIgnoreCase(entry.database(), query)
        || containsIgnoreCase(entry.client(), query)
        || containsIgnoreCase(entry.category(), query)
        || matchesQueryInIdentifiers(entry, query);
  }

  private static boolean matchesQueryInIdentifiers(
      final PgAuditLogEntry entry, final String query) {
    return containsIgnoreCase(entry.command(), query)
        || containsIgnoreCase(entry.objectType(), query)
        || containsIgnoreCase(entry.objectName(), query)
        || containsIgnoreCase(entry.statement(), query);
  }

  private static boolean containsIgnoreCase(final @Nullable String value, final String query) {

    return value != null && !value.isBlank() && value.toLowerCase(Locale.ROOT).contains(query);
  }

  private static PgAuditLogSearchResponse emptyResponse(
      final int page, final int size, final @Nullable String message) {

    return new PgAuditLogSearchResponse(List.of(), page, size, 0, 0, false, message);
  }

  private static @Nullable String blankToNull(final @Nullable String value) {

    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private record ParsedPayload(
      @Nullable String category,
      @Nullable String command,
      @Nullable String objectType,
      @Nullable String objectName,
      @Nullable String statement) {}

  private record SearchResult(List<PgAuditLogEntry> pageContent, long totalMatches) {}
}
