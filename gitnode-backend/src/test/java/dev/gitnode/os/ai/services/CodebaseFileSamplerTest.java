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

@DisplayName("CodebaseFileSampler safety tests")
class CodebaseFileSamplerTest {

  @Test
  @DisplayName("shouldSkipPath ignores vendor and lockfile directories")
  void shouldSkipPath_ignoresVendorAndLockfiles() {
    assertThat(CodebaseFileSampler.shouldSkipPath("frontend/node_modules/lodash/index.js"))
        .isTrue();
    assertThat(CodebaseFileSampler.shouldSkipPath("backend/target/classes/App.class")).isTrue();
    assertThat(CodebaseFileSampler.shouldSkipPath("package-lock.json")).isTrue();
    assertThat(CodebaseFileSampler.shouldSkipPath("dist/app.min.js")).isTrue();
    assertThat(CodebaseFileSampler.shouldSkipPath("src/main/java/App.java")).isFalse();
  }

  @Test
  @DisplayName("filePriority prefers security and auth files")
  void filePriority_prefersSecurityFiles() {
    assertThat(CodebaseFileSampler.filePriority("src/main/java/dev/app/SecurityConfig.java"))
        .isGreaterThan(CodebaseFileSampler.filePriority("src/main/java/dev/app/User.java"));
    assertThat(CodebaseFileSampler.filePriority("src/main/resources/application.yaml"))
        .isGreaterThan(CodebaseFileSampler.filePriority("README.md"));
  }

  @Test
  @DisplayName("formatSample includes repository sampling notes")
  void formatSample_includesSamplingHeader() {
    final var stats = new CodebaseFileSampler.SamplingStats();
    stats.scannedEntries = 12_500;
    stats.analyzableFilesSeen = 4_200;
    stats.skippedPaths = 8_000;
    stats.scanLimitReached = true;
    stats.topLevelModules.add("backend");
    stats.topLevelModules.add("frontend");
    stats.loadedFiles = 1;

    final var snippet = new CodebaseFileSampler.FileSnippet("src/App.java", 30, "class App {}");
    final var result = new CodebaseFileSampler.SamplingResult(java.util.List.of(snippet), stats);

    final var formatted = CodebaseFileSampler.formatSample(result, 35, 45_000);

    assertThat(formatted).contains("REPOSITORY SAMPLING NOTES");
    assertThat(formatted).contains("safety limit");
    assertThat(formatted).contains("backend, frontend");
    assertThat(formatted).contains("=== src/App.java ===");
  }
}
