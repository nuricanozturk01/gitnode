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

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class AiInputBounds {

  static final int MAX_USER_PROMPT_CHARS = 48_000;

  static final int MAX_LINE_LENGTH = 500;

  static final int COMMIT_MAX_DIFF_CHARS = 8_000;
  static final int COMMIT_MAX_COMPLETION_TOKENS = 256;

  static final int PR_DESC_MAX_FILES = 18;
  static final int PR_DESC_MAX_DIFF_CHARS = 10_000;
  static final int PR_DESC_MAX_COMPLETION_TOKENS = 1536;

  static final int REVIEW_MAX_FILES = 15;
  static final int REVIEW_MAX_DIFF_CHARS = 10_000;
  static final int REVIEW_MAX_COMPLETION_TOKENS = 3072;

  static final int DIFF_MAX_ENTRIES_SCANNED = 500;
  static final int DIFF_MAX_CANDIDATE_POOL = 80;
  static final int DIFF_MAX_LINES_PER_FILE = 80;
  static final int DIFF_MAX_HUNKS_PER_FILE = 6;

  static final int CODEBASE_MAIN_MAX_COMPLETION_TOKENS = 4096;
  static final int CODEBASE_SECURITY_MAX_COMPLETION_TOKENS = 2048;

  static final int TEST_CONNECTION_MAX_COMPLETION_TOKENS = 16;

  public static final int LLM_CALL_TIMEOUT_SECONDS = 240;

  private AiInputBounds() {}

  public static String boundUserPrompt(final String prompt) {
    if (prompt.length() <= MAX_USER_PROMPT_CHARS) {
      return prompt;
    }
    return prompt.substring(0, MAX_USER_PROMPT_CHARS)
        + "\n\n[INPUT TRUNCATED — "
        + (prompt.length() - MAX_USER_PROMPT_CHARS)
        + " chars omitted for memory safety]";
  }

  public static String boundDiffText(final String diff, final int maxChars) {
    if (diff.length() <= maxChars) {
      return diff;
    }
    return diff.substring(0, maxChars)
        + "\n\n[DIFF TRUNCATED — "
        + (diff.length() - maxChars)
        + " chars omitted for memory safety]";
  }

  public static String truncateLine(final String line) {
    if (line.length() <= MAX_LINE_LENGTH) {
      return line;
    }
    return line.substring(0, MAX_LINE_LENGTH) + "…";
  }
}
