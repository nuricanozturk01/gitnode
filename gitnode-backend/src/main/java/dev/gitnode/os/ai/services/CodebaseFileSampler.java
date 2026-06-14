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

import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import dev.gitnode.os.shared.git.provider.GitProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jspecify.annotations.NullMarked;

/**
 * Bounded, memory-safe file sampler for large repositories. Scans the tree with hard limits, skips
 * vendor/build directories, keeps only top-priority file metadata in memory, and loads content for
 * the final selection only.
 */
@NullMarked
final class CodebaseFileSampler {

  static final int MAX_TREE_ENTRIES_SCANNED = 50_000;
  static final int MAX_CANDIDATE_POOL = 150;
  static final int MAX_CONTENT_LOADS = 60;
  static final int MAX_FILES = 35;
  static final int MAX_SECURITY_FILES = 18;
  static final int MAX_FILE_SIZE_BYTES = 8_000;
  static final int MAX_TOTAL_SIZE = 45_000;
  static final int MAX_LINES_PER_FILE = 100;
  static final int SECURITY_PRIORITY_THRESHOLD = 70;

  private static final int MAX_TOP_LEVEL_MODULES = 12;
  private static final int MIN_SECURITY_FALLBACK_FILES = 10;

  private static final int PRIORITY_SECURITY_CONFIG = 100;
  private static final int PRIORITY_AUTH = 95;
  private static final int PRIORITY_APP_CONFIG = 90;
  private static final int PRIORITY_DOCKER = 85;
  private static final int PRIORITY_BUILD_FILE = 80;
  private static final int PRIORITY_SENSITIVE_FILTER = 75;
  private static final int PRIORITY_SQL_MIGRATION = 60;
  private static final int PRIORITY_README = 55;
  private static final int PRIORITY_SOURCE_CODE = 30;
  private static final int PRIORITY_DEFAULT = 10;

  private static final int TEXT_DETECT_CTRL_MIN = 9;
  private static final int TEXT_DETECT_CR_MAX = 13;
  private static final int TEXT_DETECT_SPACE = 32;
  private static final int TEXT_DETECT_MIN_SUSPICIOUS = 8;
  private static final int TEXT_DETECT_SUSPICIOUS_RATIO = 20;

  private static final Set<String> ANALYZABLE_EXTENSIONS =
      Set.of(
          ".java",
          ".kt",
          ".kts",
          ".scala",
          ".ts",
          ".tsx",
          ".js",
          ".jsx",
          ".py",
          ".go",
          ".rs",
          ".yaml",
          ".yml",
          ".json",
          ".xml",
          ".properties",
          ".sql",
          ".sh",
          ".gradle");

  private static final Set<String> SKIP_DIR_SEGMENTS =
      Set.of(
          "node_modules",
          "vendor",
          "target",
          "build",
          "dist",
          "out",
          "bin",
          "obj",
          ".git",
          ".gradle",
          ".idea",
          ".vscode",
          ".next",
          ".nuxt",
          ".turbo",
          ".cache",
          ".venv",
          "venv",
          "__pycache__",
          "site-packages",
          "coverage",
          "third_party",
          "third-party",
          "pods",
          "carthage",
          "deriveddata",
          "tmp",
          "temp",
          "logs");

  private static final Set<String> SKIP_FILE_NAMES =
      Set.of(
          "package-lock.json",
          "yarn.lock",
          "pnpm-lock.yaml",
          "go.sum",
          "cargo.lock",
          "poetry.lock",
          "gemfile.lock",
          "composer.lock");

  private final GitProvider gitProvider;

  CodebaseFileSampler(final GitProvider gitProvider) {
    this.gitProvider = gitProvider;
  }

  SamplingResult sample(final String owner, final String repoName, final String branch)
      throws IOException {
    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {
      final var branchRef = gitRepo.resolve(Constants.R_HEADS + branch);
      if (branchRef == null) {
        throw new ErrorOccurredException("Branch not found: " + branch);
      }

      final var stats = new SamplingStats();
      final var candidatePool =
          new PriorityQueue<>(Comparator.comparingInt(FileCandidate::priority));

      try (final var revWalk = new org.eclipse.jgit.revwalk.RevWalk(gitRepo)) {
        final var commit = revWalk.parseCommit(branchRef);
        scanTree(gitRepo, commit.getTree(), stats, candidatePool);
      }

      final var rankedCandidates = rankedCandidates(candidatePool);
      final var snippets = this.loadSnippets(gitRepo, rankedCandidates, stats);
      return new SamplingResult(snippets, stats);
    }
  }

  private static void scanTree(
      final Repository gitRepo,
      final org.eclipse.jgit.lib.ObjectId tree,
      final SamplingStats stats,
      final PriorityQueue<FileCandidate> candidatePool)
      throws IOException {
    try (final var treeWalk = new TreeWalk(gitRepo)) {
      treeWalk.addTree(tree);
      treeWalk.setRecursive(true);
      while (treeWalk.next()) {
        stats.scannedEntries++;
        if (stats.scannedEntries > MAX_TREE_ENTRIES_SCANNED) {
          stats.scanLimitReached = true;
          break;
        }
        processTreeEntry(gitRepo, treeWalk, stats, candidatePool);
      }
    }
  }

  private static void processTreeEntry(
      final Repository gitRepo,
      final TreeWalk treeWalk,
      final SamplingStats stats,
      final PriorityQueue<FileCandidate> candidatePool)
      throws IOException {
    final var path = treeWalk.getPathString();
    if (shouldSkipPath(path)) {
      stats.skippedPaths++;
      return;
    }
    if (!isAnalyzable(path)) {
      return;
    }
    stats.analyzableFilesSeen++;
    recordTopLevelModule(stats.topLevelModules, path);

    final var objectId = treeWalk.getObjectId(0);
    final var loader = gitRepo.open(objectId);
    final var size = loader.getSize();
    if (size <= 0 || size > MAX_FILE_SIZE_BYTES) {
      stats.skippedOversized++;
      return;
    }
    offerCandidate(candidatePool, new FileCandidate(path, filePriority(path), objectId, size));
  }

  static String formatSample(
      final SamplingResult result, final int maxFiles, final int maxTotalSize) {
    if (result.snippets().isEmpty()) {
      return "No analyzable source files found in repository.";
    }
    final var header = buildSampleHeader(result.stats());
    final var body = formatSnippets(result.snippets(), maxFiles, maxTotalSize);
    return header + body;
  }

  static String formatSecuritySample(
      final SamplingResult result, final int maxFiles, final int maxTotalSize) {
    final var securitySnippets =
        result.snippets().stream()
            .filter(snippet -> snippet.priority() >= SECURITY_PRIORITY_THRESHOLD)
            .toList();

    final var selected =
        securitySnippets.isEmpty()
            ? result.snippets().stream()
                .limit(Math.min(MIN_SECURITY_FALLBACK_FILES, result.snippets().size()))
                .toList()
            : securitySnippets;

    if (selected.isEmpty()) {
      return "";
    }

    final var header =
        """
        SECURITY-FOCUSED SAMPLE (highest-priority auth/config files):

        """;
    return header + formatSnippets(selected, maxFiles, maxTotalSize);
  }

  private static String buildSampleHeader(final SamplingStats stats) {
    final var modules =
        stats.topLevelModules.isEmpty() ? "n/a" : String.join(", ", stats.topLevelModules);
    final var scanNote =
        stats.scanLimitReached
            ? "Scan stopped at the safety limit; sample is representative, not exhaustive."
            : "Full tree scan completed within safety limits.";

    return """
        REPOSITORY SAMPLING NOTES:
        - Tree entries scanned: %d (%s)
        - Analyzable source files seen: %d
        - Vendor/build paths skipped: %d
        - Top-level areas: %s
        - Files loaded for AI analysis: %d (highest relevance first)

        """
        .formatted(
            stats.scannedEntries,
            scanNote,
            stats.analyzableFilesSeen,
            stats.skippedPaths,
            modules,
            stats.loadedFiles);
  }

  private static String formatSnippets(
      final List<FileSnippet> snippets, final int maxFiles, final int maxTotalSize) {
    final var files = new ArrayList<String>();
    int totalSize = 0;
    for (final var snippet : snippets) {
      if (files.size() >= maxFiles || totalSize >= maxTotalSize) {
        break;
      }
      final var formatted = snippet.format();
      files.add(formatted);
      totalSize += formatted.length();
    }
    return String.join("\n", files);
  }

  private static void offerCandidate(
      final PriorityQueue<FileCandidate> pool, final FileCandidate candidate) {
    if (pool.size() < MAX_CANDIDATE_POOL) {
      pool.offer(candidate);
      return;
    }
    final var weakest = pool.peek();
    if (weakest != null && candidate.priority() > weakest.priority()) {
      pool.poll();
      pool.offer(candidate);
    }
  }

  private static List<FileCandidate> rankedCandidates(
      final PriorityQueue<FileCandidate> candidatePool) {
    final var ranked = new ArrayList<>(candidatePool);
    ranked.sort(Comparator.comparingInt(FileCandidate::priority).reversed());
    return List.copyOf(ranked);
  }

  private List<FileSnippet> loadSnippets(
      final Repository gitRepo, final List<FileCandidate> candidates, final SamplingStats stats)
      throws IOException {
    final var snippets = new ArrayList<FileSnippet>();
    int totalSize = 0;

    for (final var candidate : candidates) {
      if (snippets.size() >= MAX_CONTENT_LOADS || totalSize >= MAX_TOTAL_SIZE) {
        break;
      }

      try {
        final var loader = gitRepo.open(candidate.objectId());
        if (loader.getSize() > MAX_FILE_SIZE_BYTES) {
          stats.skippedOversized++;
          continue;
        }

        final var bytes = loader.getBytes();
        if (!isTextContent(bytes)) {
          stats.skippedBinary++;
          continue;
        }

        final var content = trimContent(new String(bytes, StandardCharsets.UTF_8));
        final var snippet = new FileSnippet(candidate.path(), candidate.priority(), content);
        snippets.add(snippet);
        totalSize += snippet.format().length();
      } catch (final Exception e) {
        stats.skippedUnreadable++;
      }
    }

    snippets.sort(Comparator.comparingInt(FileSnippet::priority).reversed());
    stats.loadedFiles = snippets.size();
    return List.copyOf(snippets);
  }

  private static void recordTopLevelModule(final Set<String> modules, final String path) {
    final var slash = path.indexOf('/');
    final var topLevel = slash < 0 ? path : path.substring(0, slash);
    if (!topLevel.isBlank() && modules.size() < MAX_TOP_LEVEL_MODULES) {
      modules.add(topLevel);
    }
  }

  static boolean shouldSkipPath(final String path) {
    final var lower = path.toLowerCase();
    final var fileName = lower.contains("/") ? lower.substring(lower.lastIndexOf('/') + 1) : lower;
    if (SKIP_FILE_NAMES.contains(fileName)) {
      return true;
    }
    if (isMinifiedOrMapFile(lower)) {
      return true;
    }
    return hasSkippedDirectorySegment(lower);
  }

  private static boolean isMinifiedOrMapFile(final String lower) {
    return lower.endsWith(".min.js") || lower.endsWith(".min.css") || lower.endsWith(".map");
  }

  private static boolean hasSkippedDirectorySegment(final String lower) {
    for (final var segment : lower.split("/")) {
      if (SKIP_DIR_SEGMENTS.contains(segment)) {
        return true;
      }
    }
    return false;
  }

  static boolean isAnalyzable(final String path) {
    final var lower = path.toLowerCase();
    if (lower.endsWith("dockerfile") || lower.contains("dockerfile.")) {
      return true;
    }
    for (final var ext : ANALYZABLE_EXTENSIONS) {
      if (lower.endsWith(ext)) {
        return true;
      }
    }
    return false;
  }

  static int filePriority(final String path) {
    final var lower = path.toLowerCase();
    final var secPriority = securityFilePriority(lower);
    if (secPriority > 0) {
      return secPriority;
    }
    return generalFilePriority(lower);
  }

  private static int securityFilePriority(final String lower) {
    if (isSecurityConfigPath(lower)) {
      return PRIORITY_SECURITY_CONFIG;
    }
    if (isAuthPath(lower)) {
      return PRIORITY_AUTH;
    }
    if (isSensitiveFilterPath(lower)) {
      return PRIORITY_SENSITIVE_FILTER;
    }
    return 0;
  }

  private static int generalFilePriority(final String lower) {
    if (isAppConfigPath(lower)) {
      return PRIORITY_APP_CONFIG;
    }
    if (isDockerPath(lower)) {
      return PRIORITY_DOCKER;
    }
    if (isBuildFilePath(lower)) {
      return PRIORITY_BUILD_FILE;
    }
    if (isSqlMigrationPath(lower)) {
      return PRIORITY_SQL_MIGRATION;
    }
    if (isReadmePath(lower)) {
      return PRIORITY_README;
    }
    if (isSourceCodePath(lower)) {
      return PRIORITY_SOURCE_CODE;
    }
    return PRIORITY_DEFAULT;
  }

  private static boolean isSecurityConfigPath(final String lower) {
    return lower.contains("securityconfig")
        || lower.contains("security/")
        || lower.contains("/security/")
        || lower.contains("websecurity");
  }

  private static boolean isAuthPath(final String lower) {
    return lower.contains("/auth/")
        || lower.contains("authentication")
        || lower.contains("authorization")
        || lower.contains("jwt")
        || lower.contains("oauth")
        || lower.contains("saml")
        || lower.contains("ldap");
  }

  private static boolean isSensitiveFilterPath(final String lower) {
    return lower.contains("secret")
        || lower.contains("encrypt")
        || lower.contains("ratelimit")
        || lower.contains("cors")
        || lower.contains("csrf")
        || lower.contains("filter")
        || lower.contains("interceptor");
  }

  private static boolean isAppConfigPath(final String lower) {
    return lower.endsWith("application.yaml")
        || lower.endsWith("application.yml")
        || lower.endsWith("application.properties")
        || lower.endsWith("application-local.yaml");
  }

  private static boolean isDockerPath(final String lower) {
    return lower.contains("dockerfile") || lower.endsWith("docker-compose.yml");
  }

  private static boolean isBuildFilePath(final String lower) {
    return lower.endsWith("pom.xml")
        || lower.endsWith("build.gradle")
        || lower.endsWith("build.gradle.kts");
  }

  private static boolean isSqlMigrationPath(final String lower) {
    return lower.contains("/migration/") && lower.endsWith(".sql");
  }

  private static boolean isReadmePath(final String lower) {
    return lower.endsWith("readme.md") || lower.endsWith("claude.md");
  }

  private static boolean isSourceCodePath(final String lower) {
    return lower.endsWith(".java") || lower.endsWith(".kt") || lower.endsWith(".ts");
  }

  private static String trimContent(final String content) {
    final var lines = content.split("\n", -1);
    if (lines.length <= MAX_LINES_PER_FILE) {
      return content;
    }
    final var sb = new StringBuilder();
    for (int i = 0; i < MAX_LINES_PER_FILE; i++) {
      sb.append(lines[i]).append("\n");
    }
    sb.append("... [").append(lines.length - MAX_LINES_PER_FILE).append(" more lines omitted]");
    return sb.toString();
  }

  private static boolean isTextContent(final byte[] bytes) {
    if (bytes.length == 0) {
      return false;
    }
    int suspicious = 0;
    for (final byte value : bytes) {
      if (value == 0) {
        return false;
      }
      if (value < TEXT_DETECT_CTRL_MIN
          || (value > TEXT_DETECT_CR_MAX && value < TEXT_DETECT_SPACE)) {
        suspicious++;
      }
    }
    return suspicious
        < Math.max(TEXT_DETECT_MIN_SUSPICIOUS, bytes.length / TEXT_DETECT_SUSPICIOUS_RATIO);
  }

  record FileCandidate(String path, int priority, ObjectId objectId, long size) {}

  record FileSnippet(String path, int priority, String content) {
    String format() {
      return "=== " + this.path + " ===\n" + this.content + "\n";
    }
  }

  static final class SamplingStats {
    int scannedEntries;
    int analyzableFilesSeen;
    int skippedPaths;
    int skippedOversized;
    int skippedBinary;
    int skippedUnreadable;
    int loadedFiles;
    boolean scanLimitReached;
    final Set<String> topLevelModules = new LinkedHashSet<>();
  }

  record SamplingResult(List<FileSnippet> snippets, SamplingStats stats) {}
}
