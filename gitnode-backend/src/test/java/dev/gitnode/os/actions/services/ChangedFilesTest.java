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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

/** The changed-file lookups, against a real repository on disk. */
@DisplayName("WorkflowParserService changed files")
class ChangedFilesTest {

  private static final String ZERO = "0000000000000000000000000000000000000000";

  @TempDir Path root;

  private final WorkflowParserService parser = new WorkflowParserService();
  private Git git;
  private Path workTree;

  @BeforeEach
  void setUp() throws Exception {
    ReflectionTestUtils.setField(this.parser, "repoRoot", this.root.toString());
    final var gitDir = this.root.resolve("alice").resolve("app.git");
    this.workTree = this.root.resolve("work");
    Files.createDirectories(this.workTree);
    this.git =
        Git.init()
            .setInitialBranch("main")
            .setDirectory(this.workTree.toFile())
            .setGitDir(gitDir.toFile())
            .call();
  }

  private String commit(final String message, final String... files) throws Exception {
    for (final var file : files) {
      final var path = this.workTree.resolve(file);
      Files.createDirectories(path.getParent());
      Files.writeString(path, message + file);
      this.git.add().addFilepattern(file).call();
    }
    final var who = new PersonIdent("t", "t@example.com");
    return this.git.commit().setMessage(message).setAuthor(who).setCommitter(who).call().name();
  }

  private void delete(final String file) throws IOException, Exception {
    Files.delete(this.workTree.resolve(file));
    this.git.rm().addFilepattern(file).call();
  }

  @Test
  @DisplayName("a push lists the files between the old and the new tip")
  void pushRange() throws Exception {
    final var first = this.commit("one", "src/A.java", "README.md");
    this.commit("two", "src/B.java");
    final var third = this.commit("three", "docs/guide.md");

    assertThat(this.parser.changedFiles("alice", "app", first, third))
        .containsExactlyInAnyOrder("src/B.java", "docs/guide.md");
  }

  @Test
  @DisplayName(
      "without a usable old tip (new branch, unknown) the commit is compared with its parent")
  void newBranchUsesTheParent() throws Exception {
    this.commit("one", "src/A.java");
    final var second = this.commit("two", "docs/a.md");

    assertThat(this.parser.changedFiles("alice", "app", ZERO, second)).containsExactly("docs/a.md");
    assertThat(this.parser.changedFiles("alice", "app", null, second)).containsExactly("docs/a.md");
  }

  @Test
  @DisplayName("the first commit of a repository lists all its files")
  void rootCommit() throws Exception {
    final var first = this.commit("one", "src/A.java", "README.md");

    assertThat(this.parser.changedFiles("alice", "app", null, first))
        .containsExactlyInAnyOrder("src/A.java", "README.md");
  }

  @Test
  @DisplayName("deleted and renamed files are listed by every path involved")
  void deletedFiles() throws Exception {
    final var first = this.commit("one", "src/A.java", "old.txt");
    this.delete("old.txt");
    final var who = new PersonIdent("t", "t@example.com");
    final var second =
        this.git.commit().setMessage("rm").setAuthor(who).setCommitter(who).call().name();

    assertThat(this.parser.changedFiles("alice", "app", first, second)).containsExactly("old.txt");
  }

  @Test
  @DisplayName("an unknown commit or repository gives null so no path filter is applied")
  void unknown() throws Exception {
    this.commit("one", "a.txt");

    assertThat(this.parser.changedFiles("alice", "app", null, "f".repeat(40))).isNull();
    assertThat(this.parser.changedFiles("alice", "nope", null, "f".repeat(40))).isNull();
  }

  @Test
  @DisplayName("a pull request lists what its branch changed since it left the target")
  void pullRequestFiles() throws Exception {
    this.commit("base", "src/A.java");
    this.git.branchCreate().setName("feature").call();
    this.git.checkout().setName("feature").call();
    this.commit("feature work", "src/Feature.java");
    final var head = this.commit("feature docs", "docs/f.md");
    this.git.checkout().setName("main").call();
    this.commit("main moves on", "other/Main.java");

    assertThat(this.parser.changedFilesOfPullRequest("alice", "app", "main", head))
        .containsExactlyInAnyOrder("src/Feature.java", "docs/f.md");
  }

  @Test
  @DisplayName("resolveSha finds branches and tags and returns null for a missing ref")
  void resolve() throws Exception {
    final var sha = this.commit("one", "a.txt");
    this.git.tag().setName("v1").call();

    assertThat(this.parser.resolveSha("alice", "app", "refs/heads/main")).isEqualTo(sha);
    assertThat(this.parser.resolveSha("alice", "app", "refs/tags/v1")).isEqualTo(sha);
    assertThat(this.parser.resolveSha("alice", "app", "refs/heads/gone")).isNull();
  }
}
