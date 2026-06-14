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
package dev.gitnode.os.ai.services;

import static dev.gitnode.os.shared.util.FileDiffParser.parseFileDiff;

import dev.gitnode.os.shared.commit.dtos.DiffHunk;
import dev.gitnode.os.shared.commit.dtos.FileDiff;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.jspecify.annotations.NullMarked;

/** Memory-safe bounded diff collection and formatting for AI features. */
@NullMarked
final class AiDiffFormatter {

  private static final int DIFF_BASE_SCORE = 10;
  private static final int DIFF_SECURITY_BONUS = 80;
  private static final int DIFF_SOURCE_CODE_BONUS = 40;
  private static final int DIFF_CONFIG_BONUS = 35;
  private static final int DIFF_TEST_BONUS = 20;
  private static final int DIFF_LOCKFILE_PENALTY = 30;

  private AiDiffFormatter() {}

  static DiffBuildResult buildBoundedDiff(
      final Repository gitRepo,
      final AbstractTreeIterator targetTree,
      final AbstractTreeIterator sourceTree,
      final int maxFiles,
      final int maxChars)
      throws IOException {

    final var stats = new DiffBuildStats();
    final var candidatePool = new PriorityQueue<>(Comparator.comparingInt(DiffCandidate::priority));

    try (final var formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
      formatter.setRepository(gitRepo);
      formatter.setDiffComparator(RawTextComparator.DEFAULT);
      formatter.setDetectRenames(true);

      final var entries = formatter.scan(targetTree, sourceTree);
      for (final var entry : entries) {
        stats.totalEntriesScanned++;
        if (stats.totalEntriesScanned > AiInputBounds.DIFF_MAX_ENTRIES_SCANNED) {
          stats.scanLimitReached = true;
          break;
        }

        final var path = resolvePath(entry);
        if (CodebaseFileSampler.shouldSkipPath(path)) {
          stats.skippedPaths++;
          continue;
        }

        final var priority = diffFilePriority(path);
        if (priority < 0) {
          stats.skippedPaths++;
          continue;
        }

        offerCandidate(candidatePool, new DiffCandidate(path, priority, entry));
      }

      final var selected = rankedCandidates(candidatePool).stream().limit(maxFiles).toList();
      stats.filesSelected = selected.size();

      final var diffs = new ArrayList<FileDiff>(selected.size());
      for (final var candidate : selected) {
        diffs.add(parseFileDiff(gitRepo, formatter, candidate.entry()));
      }

      final var body = formatDiffsBounded(diffs, maxChars);
      final var text = buildHeader(stats) + body;
      return new DiffBuildResult(AiInputBounds.boundUserPrompt(text), stats);
    }
  }

  static String boundClientDiff(final String diff, final int maxChars) {
    final var header =
        """
        DIFF INPUT NOTES:
        - Client-provided diff may be truncated for memory safety.
        - Infer the primary change from visible hunks only.

        """;
    return header + AiInputBounds.boundDiffText(diff, maxChars);
  }

  private static String buildHeader(final DiffBuildStats stats) {
    final var scanNote =
        stats.scanLimitReached
            ? "Scan stopped at safety limit; diff is a prioritized sample, not exhaustive."
            : "Diff scan completed within safety limits.";

    return """
        DIFF SAMPLING NOTES:
        - Entries scanned: %d (%s)
        - Vendor/build paths skipped: %d
        - Files included in AI input: %d (highest relevance first)

        """
        .formatted(stats.totalEntriesScanned, scanNote, stats.skippedPaths, stats.filesSelected);
  }

  private static void offerCandidate(
      final PriorityQueue<DiffCandidate> pool, final DiffCandidate candidate) {
    if (pool.size() < AiInputBounds.DIFF_MAX_CANDIDATE_POOL) {
      pool.offer(candidate);
      return;
    }
    final var weakest = pool.peek();
    if (weakest != null && candidate.priority() > weakest.priority()) {
      pool.poll();
      pool.offer(candidate);
    }
  }

  private static List<DiffCandidate> rankedCandidates(
      final PriorityQueue<DiffCandidate> candidatePool) {
    final var ranked = new ArrayList<>(candidatePool);
    ranked.sort(Comparator.comparingInt(DiffCandidate::priority).reversed());
    return List.copyOf(ranked);
  }

  private static String resolvePath(final DiffEntry entry) {
    final var newPath = entry.getNewPath();
    if (newPath != null && !DiffEntry.DEV_NULL.equals(newPath)) {
      return newPath;
    }
    return entry.getOldPath();
  }

  static int diffFilePriority(final String path) {
    if (CodebaseFileSampler.shouldSkipPath(path)) {
      return -1;
    }
    final var lower = path.toLowerCase();
    return DIFF_BASE_SCORE + diffScoreBonus(lower) + diffScorePenalty(lower);
  }

  private static int diffScoreBonus(final String lower) {
    if (isDiffSecurityPath(lower)) {
      return DIFF_SECURITY_BONUS;
    }
    if (isDiffSourceCodePath(lower)) {
      return DIFF_SOURCE_CODE_BONUS;
    }
    if (isDiffConfigPath(lower)) {
      return DIFF_CONFIG_BONUS;
    }
    if (isDiffTestPath(lower)) {
      return DIFF_TEST_BONUS;
    }
    return 0;
  }

  private static int diffScorePenalty(final String lower) {
    return isDiffLowPriorityPath(lower) ? -DIFF_LOCKFILE_PENALTY : 0;
  }

  private static boolean isDiffSecurityPath(final String lower) {
    return lower.contains("security")
        || lower.contains("/auth/")
        || lower.contains("authentication")
        || lower.contains("authorization");
  }

  private static boolean isDiffSourceCodePath(final String lower) {
    return lower.endsWith(".java")
        || lower.endsWith(".kt")
        || lower.endsWith(".ts")
        || lower.endsWith(".tsx")
        || lower.endsWith(".py")
        || lower.endsWith(".go");
  }

  private static boolean isDiffConfigPath(final String lower) {
    return lower.endsWith(".sql")
        || lower.endsWith(".yaml")
        || lower.endsWith(".yml")
        || lower.endsWith(".properties");
  }

  private static boolean isDiffTestPath(final String lower) {
    return lower.contains("test") || lower.contains("spec");
  }

  private static boolean isDiffLowPriorityPath(final String lower) {
    return lower.endsWith(".md") || (lower.endsWith(".json") && lower.contains("lock"));
  }

  private static String formatDiffsBounded(final List<FileDiff> diffs, final int maxChars) {
    final var builder = new StringBuilder();
    for (final var diff : diffs) {
      if (builder.length() >= maxChars) {
        builder.append("\n[additional file diffs omitted — character budget reached]");
        break;
      }
      appendFileDiff(builder, diff, maxChars);
    }
    if (builder.length() > maxChars) {
      return builder.substring(0, maxChars) + "\n[diff truncated for memory safety]";
    }
    return builder.toString();
  }

  private static void appendFileDiff(
      final StringBuilder builder, final FileDiff diff, final int maxChars) {
    if (builder.length() >= maxChars) {
      return;
    }
    builder.append("--- ").append(diff.oldPath()).append('\n');
    builder.append("+++ ").append(diff.newPath()).append('\n');
    final var completed = appendHunksBody(builder, diff, maxChars);
    if (completed && diff.isTruncated()) {
      builder.append("[parser truncated large file]\n");
    }
  }

  private static boolean appendHunksBody(
      final StringBuilder builder, final FileDiff diff, final int maxChars) {
    int hunkCount = 0;
    int lineCount = 0;
    for (final var hunk : diff.hunks()) {
      if (hunkCount >= AiInputBounds.DIFF_MAX_HUNKS_PER_FILE
          || builder.length() >= maxChars
          || lineCount >= AiInputBounds.DIFF_MAX_LINES_PER_FILE) {
        builder.append("[file diff truncated — hunk/line budget reached]\n");
        return true;
      }
      hunkCount++;
      final var result = appendHunkLines(builder, hunk, lineCount, maxChars);
      if (result < 0) {
        return false;
      }
      lineCount = result;
    }
    return true;
  }

  private static int appendHunkLines(
      final StringBuilder builder, final DiffHunk hunk, final int startCount, final int maxChars) {
    int lineCount = startCount;
    for (final var line : hunk.lines()) {
      if (lineCount >= AiInputBounds.DIFF_MAX_LINES_PER_FILE || builder.length() >= maxChars) {
        builder.append("[file diff truncated — line budget reached]\n");
        return -1;
      }
      lineCount++;
      final var prefix =
          switch (line.type()) {
            case ADD -> "+";
            case DELETE -> "-";
            case CONTEXT -> " ";
          };
      builder.append(prefix).append(AiInputBounds.truncateLine(line.content())).append('\n');
    }
    return lineCount;
  }

  record DiffCandidate(String path, int priority, DiffEntry entry) {}

  static final class DiffBuildStats {
    int totalEntriesScanned;
    int skippedPaths;
    int filesSelected;
    boolean scanLimitReached;
  }

  record DiffBuildResult(String text, DiffBuildStats stats) {}
}
