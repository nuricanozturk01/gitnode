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
package dev.gitnode.os.pr.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CodeOwnersFileTest {

  private static java.util.List<String> owners(final CodeOwnersFile file, final String path) {
    return file.ruleFor(path).map(CodeOwnersFile.Rule::owners).orElse(java.util.List.of());
  }

  @Test
  void lastMatchingRuleWins() {
    final var file =
        CodeOwnersFile.parse(
            """
            * @alice
            /docs/ @bob
            *.md @carol
            """);

    assertThat(owners(file, "src/Main.java")).containsExactly("alice");
    assertThat(owners(file, "docs/guide.txt")).containsExactly("bob");
    assertThat(owners(file, "docs/guide.md")).containsExactly("carol");
  }

  @Test
  void ignoresCommentsBlankLinesAndInlineComments() {
    final var file =
        CodeOwnersFile.parse(
            """
            # whole line comment

            *.java @alice @bob # trailing comment
            """);

    assertThat(owners(file, "A.java")).containsExactly("alice", "bob");
  }

  @Test
  void anchoredPatternsOnlyMatchFromTheRoot() {
    final var file = CodeOwnersFile.parse("/build.gradle @alice\nsrc/app/ @bob\n");

    assertThat(owners(file, "build.gradle")).containsExactly("alice");
    assertThat(owners(file, "sub/build.gradle")).isEmpty();
    assertThat(owners(file, "src/app/x/Y.java")).containsExactly("bob");
    assertThat(owners(file, "other/src/app/Y.java")).isEmpty();
  }

  @Test
  void unanchoredNamesMatchAtAnyDepthAndInsideDirectories() {
    final var file = CodeOwnersFile.parse("docs @alice\n*.sql @bob\n");

    assertThat(owners(file, "docs/a.txt")).containsExactly("alice");
    assertThat(owners(file, "x/docs/a.txt")).containsExactly("alice");
    assertThat(owners(file, "db/migration/V1__init.sql")).containsExactly("bob");
  }

  @Test
  void doubleStarSpansDirectories() {
    final var file = CodeOwnersFile.parse("/src/**/test/ @alice\n/web/** @bob\n");

    assertThat(owners(file, "src/test/A.java")).containsExactly("alice");
    assertThat(owners(file, "src/a/b/test/A.java")).containsExactly("alice");
    assertThat(owners(file, "web/a/b.ts")).containsExactly("bob");
    assertThat(owners(file, "src/a/main/A.java")).isEmpty();
  }

  @Test
  void singleStarDoesNotCrossDirectories() {
    final var file = CodeOwnersFile.parse("/docs/*.md @alice\n");

    assertThat(owners(file, "docs/a.md")).containsExactly("alice");
    assertThat(owners(file, "docs/sub/a.md")).isEmpty();
  }

  @Test
  void patternWithoutOwnersClearsOwnership() {
    final var file = CodeOwnersFile.parse("* @alice\n/vendor/\n");

    assertThat(file.ruleFor("vendor/lib.js")).isPresent();
    assertThat(owners(file, "vendor/lib.js")).isEmpty();
    assertThat(owners(file, "src/a.js")).containsExactly("alice");
  }

  @Test
  void ownersAreCaseInsensitiveAndTeamsAndEmailsAreIgnored() {
    final var file = CodeOwnersFile.parse("*.ts @Alice @acme/frontend dev@example.com\n");

    assertThat(owners(file, "a.ts")).containsExactly("alice");
  }

  @Test
  void emptyFileOwnsNothing() {
    assertThat(CodeOwnersFile.parse("# nothing\n").isEmpty()).isTrue();
    assertThat(CodeOwnersFile.empty().ruleFor("a")).isEmpty();
  }
}
