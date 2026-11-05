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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PathFilters")
class PathFiltersTest {

  @Test
  @DisplayName("* stays in one directory, ** crosses directories, **/ may match none")
  void globs() {
    assertThat(PathFilters.matches("src/App.java", "src/*.java")).isTrue();
    assertThat(PathFilters.matches("src/a/App.java", "src/*.java")).isFalse();
    assertThat(PathFilters.matches("src/a/b/App.java", "src/**")).isTrue();
    assertThat(PathFilters.matches("docs/a.md", "docs/**/*.md")).isTrue();
    assertThat(PathFilters.matches("docs/x/y/a.md", "docs/**/*.md")).isTrue();
    assertThat(PathFilters.matches("a.md", "**/*.md")).isTrue();
    assertThat(PathFilters.matches("README.md", "README.md")).isTrue();
    assertThat(PathFilters.matches("READMExmd", "README.md")).isFalse();
    assertThat(PathFilters.matches("a1.txt", "a?.txt")).isTrue();
    assertThat(PathFilters.matches("a/.txt", "a?.txt")).isFalse();
    assertThat(PathFilters.matches("v1.txt", "v[0-9].txt")).isTrue();
    assertThat(PathFilters.matches("va.txt", "v[!0-9].txt")).isTrue();
    assertThat(PathFilters.matches("v1.txt", "v[!0-9].txt")).isFalse();
  }

  @Test
  @DisplayName("regex characters in a pattern are plain characters")
  void regexCharactersAreLiteral() {
    assertThat(PathFilters.matches("a+b(1).txt", "a+b(1).txt")).isTrue();
    assertThat(PathFilters.matches("aab(1).txt", "a+b(1).txt")).isFalse();
    assertThat(PathFilters.matches("a|b", "a|b")).isTrue();
    assertThat(PathFilters.matches("a", "a|b")).isFalse();
  }

  @Test
  @DisplayName("paths: at least one changed file must match")
  void pathsNeedsAMatch() {
    final var paths = List.of("src/**");

    assertThat(PathFilters.allows(List.of("src/a.java", "README.md"), paths, null)).isTrue();
    assertThat(PathFilters.allows(List.of("README.md"), paths, null)).isFalse();
    assertThat(PathFilters.allows(List.of(), paths, null)).isFalse();
  }

  @Test
  @DisplayName("paths: a ! pattern takes files out, the last matching pattern decides")
  void pathsNegation() {
    final var paths = List.of("src/**", "!src/**/*.md");

    assertThat(PathFilters.allows(List.of("src/a.java"), paths, null)).isTrue();
    assertThat(PathFilters.allows(List.of("src/a.md"), paths, null)).isFalse();
    assertThat(PathFilters.allows(List.of("src/a.md", "src/b.java"), paths, null)).isTrue();

    final var backAgain = List.of("src/**", "!src/**/*.md", "src/keep.md");
    assertThat(PathFilters.allows(List.of("src/keep.md"), backAgain, null)).isTrue();
  }

  @Test
  @DisplayName("paths-ignore: runs unless every changed file is ignored")
  void pathsIgnore() {
    final var ignore = List.of("docs/**", "*.md");

    assertThat(PathFilters.allows(List.of("docs/a.txt", "README.md"), null, ignore)).isFalse();
    assertThat(PathFilters.allows(List.of("docs/a.txt", "src/A.java"), null, ignore)).isTrue();
    assertThat(PathFilters.allows(List.of(), null, ignore)).isTrue();
  }

  @Test
  @DisplayName("no filter, or unknown changed files, never keep a workflow from running")
  void failsOpen() {
    assertThat(PathFilters.allows(List.of("a"), null, null)).isTrue();
    assertThat(PathFilters.allows(List.of("a"), List.of(), List.of())).isTrue();
    assertThat(PathFilters.allows(null, List.of("src/**"), null)).isTrue();
    assertThat(PathFilters.allows(null, null, List.of("docs/**"))).isTrue();
  }

  @Test
  @DisplayName("with both filters, paths is the one that applies")
  void pathsWinsOverIgnore() {
    assertThat(PathFilters.allows(List.of("src/a.java"), List.of("src/**"), List.of("src/**")))
        .isTrue();
  }
}
