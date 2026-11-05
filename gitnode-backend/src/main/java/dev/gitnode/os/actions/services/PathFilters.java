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

import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * GitHub's {@code paths} / {@code paths-ignore} filters. A pattern is a glob over the whole path:
 * {@code *} stays inside one directory, {@code **} crosses directories (and {@code **}{@code /}
 * also matches no directory at all), {@code ?} is one character, {@code [abc]} a character class.
 * In {@code paths}, a pattern starting with {@code !} takes files out again; the last pattern that
 * matches a file decides.
 */
@NullMarked
final class PathFilters {

  private static final int DOUBLE_STAR = 2;
  private static final int DOUBLE_STAR_SLASH = 3;

  private PathFilters() {}

  /**
   * Whether a run with these changed files is wanted. With {@code paths} at least one file must
   * match; with {@code paths-ignore} not every file may be ignored. When both are given {@code
   * paths} wins, as GitHub does not allow the two together. Null {@code changedFiles} (they could
   * not be determined) never filters a run away.
   */
  static boolean allows(
      final @Nullable List<String> changedFiles,
      final @Nullable List<String> paths,
      final @Nullable List<String> pathsIgnore) {

    if (changedFiles == null || !isFiltered(paths, pathsIgnore)) {
      return true;
    }
    if (isSet(paths)) {
      return changedFiles.stream().anyMatch(file -> included(file, paths));
    }
    // A push that changes no file at all (an empty commit) has nothing to ignore.
    return changedFiles.isEmpty()
        || changedFiles.stream().anyMatch(file -> !matchesAny(file, pathsIgnore));
  }

  /** Whether the workflow declares a path filter, so the changed files are worth computing. */
  static boolean isFiltered(
      final @Nullable List<String> paths, final @Nullable List<String> pathsIgnore) {
    return isSet(paths) || isSet(pathsIgnore);
  }

  private static boolean isSet(final @Nullable List<String> patterns) {
    return patterns != null && !patterns.isEmpty();
  }

  private static boolean included(final String file, final List<String> patterns) {
    boolean included = false;
    for (final var pattern : patterns) {
      if (pattern.startsWith("!")) {
        if (matches(file, pattern.substring(1))) {
          included = false;
        }
      } else if (matches(file, pattern)) {
        included = true;
      }
    }
    return included;
  }

  private static boolean matchesAny(final String file, final List<String> patterns) {
    return patterns.stream().anyMatch(p -> matches(file, p));
  }

  static boolean matches(final String file, final String pattern) {
    return Pattern.compile(toRegex(pattern)).matcher(file).matches();
  }

  static String toRegex(final String pattern) {

    final var regex = new StringBuilder();
    int i = 0;
    while (i < pattern.length()) {
      i = appendToken(pattern, i, regex);
    }
    return regex.toString();
  }

  /** Appends the regex for the token at {@code i} and returns where the next token starts. */
  private static int appendToken(final String pattern, final int i, final StringBuilder regex) {

    final char c = pattern.charAt(i);
    if (c == '*') {
      return appendStar(pattern, i, regex);
    }
    if (c == '?') {
      regex.append("[^/]");
      return i + 1;
    }
    if (c == '[') {
      final int end = pattern.indexOf(']', i + 1);
      if (end > i + 1) {
        appendClass(pattern.substring(i + 1, end), regex);
        return end + 1;
      }
    }
    regex.append(Pattern.quote(String.valueOf(c)));
    return i + 1;
  }

  private static int appendStar(final String pattern, final int i, final StringBuilder regex) {

    final boolean doubled = i + 1 < pattern.length() && pattern.charAt(i + 1) == '*';
    if (!doubled) {
      regex.append("[^/]*");
      return i + 1;
    }
    final boolean slashFollows =
        i + DOUBLE_STAR < pattern.length() && pattern.charAt(i + DOUBLE_STAR) == '/';
    regex.append(slashFollows ? "(?:.*/)?" : ".*");
    return i + (slashFollows ? DOUBLE_STAR_SLASH : DOUBLE_STAR);
  }

  private static void appendClass(final String body, final StringBuilder regex) {
    final var negated = body.startsWith("!");
    final var chars = negated ? body.substring(1) : body;
    regex
        .append('[')
        .append(negated ? "^" : "")
        .append(chars.replace("\\", "\\\\").replace("[", "\\["))
        .append(']');
  }
}
