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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AiDiffFormatter safety tests")
class AiDiffFormatterTest {

  @Test
  @DisplayName("diffFilePriority skips vendor paths")
  void diffFilePriority_skipsVendorPaths() {
    assertThat(AiDiffFormatter.diffFilePriority("frontend/node_modules/foo/index.js")).isNegative();
    assertThat(AiDiffFormatter.diffFilePriority("src/main/java/App.java")).isPositive();
    assertThat(AiDiffFormatter.diffFilePriority("src/main/java/security/AuthFilter.java"))
        .isGreaterThan(AiDiffFormatter.diffFilePriority("src/main/java/App.java"));
  }

  @Test
  @DisplayName("boundClientDiff adds truncation notes")
  void boundClientDiff_truncatesLargeInput() {
    final var huge = "+line\n".repeat(5_000);
    final var bounded = AiDiffFormatter.boundClientDiff(huge, AiInputBounds.COMMIT_MAX_DIFF_CHARS);

    assertThat(bounded).contains("DIFF INPUT NOTES");
    assertThat(bounded).contains("TRUNCATED");
    assertThat(bounded.length()).isLessThan(huge.length());
  }

  @Test
  @DisplayName("boundUserPrompt enforces global cap")
  void boundUserPrompt_enforcesGlobalCap() {
    final var huge = "x".repeat(AiInputBounds.MAX_USER_PROMPT_CHARS + 1_000);
    final var bounded = AiInputBounds.boundUserPrompt(huge);

    assertThat(bounded.length()).isLessThan(huge.length());
    assertThat(bounded).contains("INPUT TRUNCATED");
  }
}
