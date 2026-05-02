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
package com.nuricanozturk.originhub.tree.utils;

import java.util.Set;
import org.jspecify.annotations.NonNull;

public final class ArchivePathSupport {

  private static final char MIN_PRINTABLE_ASCII = 0x20;
  private static final Set<Character> INVALID_CHARS =
      Set.of('/', '\\', ':', '*', '?', '"', '<', '>', '|');
  private static final String DOUBLE_DASH = "--";
  private static final String SINGLE_DASH = "-";
  private static final String FALLBACK_NAME = "archive";

  private ArchivePathSupport() {}

  public static @NonNull String attachmentFileName(
      final @NonNull String owner, final @NonNull String repo, final @NonNull String branch) {

    return sanitizePathToken(owner + SINGLE_DASH + repo + SINGLE_DASH + branch) + ".zip";
  }

  /** Root folder inside the ZIP (GitHub-style single top-level directory). */
  public static @NonNull String archiveTreePrefix(
      final @NonNull String owner, final @NonNull String repo, final @NonNull String branch) {

    return sanitizePathToken(owner + SINGLE_DASH + repo + SINGLE_DASH + branch) + "/";
  }

  static @NonNull String sanitizePathToken(final @NonNull String raw) {
    final var replaced = replaceInvalidChars(raw);
    final var collapsed = collapseDoubleDashes(replaced);
    final var stripped = collapsed.strip();
    return stripped.isEmpty() ? FALLBACK_NAME : stripped;
  }

  private static @NonNull String replaceInvalidChars(final @NonNull String raw) {
    final var sb = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      sb.append(isInvalidChar(raw.charAt(i)) ? '-' : raw.charAt(i));
    }
    return sb.toString();
  }

  private static boolean isInvalidChar(final char c) {
    return c < MIN_PRINTABLE_ASCII || INVALID_CHARS.contains(c);
  }

  private static @NonNull String collapseDoubleDashes(final @NonNull String s) {
    var result = s;
    while (result.contains(DOUBLE_DASH)) {
      result = result.replace(DOUBLE_DASH, SINGLE_DASH);
    }
    return result;
  }
}
