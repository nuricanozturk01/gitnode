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

package dev.gitnode.os.tree.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.shared.branch.BranchProtectionService;
import dev.gitnode.os.shared.cache.RepoCacheInvalidator;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.git.TreeCommitEventPublisher;
import dev.gitnode.os.shared.git.provider.GitProvider;
import dev.gitnode.os.tree.dtos.CommitChangesRequest;
import dev.gitnode.os.tree.dtos.CommitChangesRequest.Change;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ChangeCommitService with a real Git repository")
class ChangeCommitServiceTest {

  @TempDir Path dir;

  private final BranchProtectionService protection = mock(BranchProtectionService.class);
  private final TreeCommitEventPublisher events = mock(TreeCommitEventPublisher.class);
  private final RepoCacheInvalidator cache = mock(RepoCacheInvalidator.class);
  private final PersonIdent author = new PersonIdent("Dev", "dev@example.org");
  private ChangeCommitService service;
  private Git git;
  private String head;

  @BeforeEach
  void setUp() throws Exception {
    git = Git.init().setInitialBranch("main").setDirectory(dir.toFile()).call();
    write("README.md", "hello\n");
    write("src/a.txt", "a\n");
    write("src/b.txt", "b\n");
    head = git.getRepository().resolve("refs/heads/main").name();
    final var provider = mock(GitProvider.class);
    when(provider.open("o", "r")).thenAnswer(i -> Git.open(dir.toFile()).getRepository());
    service = new ChangeCommitService(provider, cache, protection, events);
  }

  private void write(final String file, final String content) throws Exception {
    final var target = dir.resolve(file);
    Files.createDirectories(target.getParent());
    Files.writeString(target, content);
    git.add().addFilepattern(".").call();
    git.commit().setMessage("add " + file).setAuthor("a", "a@example.org").call();
    head = git.getRepository().resolve("refs/heads/main").name();
  }

  private CommitChangesRequest request(final String expected, final Change... changes) {
    return new CommitChangesRequest(
        "main", expected, "Edit files", "More detail", List.of(changes));
  }

  private String contentAt(final String path) throws Exception {
    try (final var repo = Git.open(dir.toFile()).getRepository();
        final var walk = new RevWalk(repo)) {
      final var commit = walk.parseCommit(repo.resolve("refs/heads/main"));
      try (final var tree = TreeWalk.forPath(repo, path, commit.getTree())) {
        return tree == null ? null : new String(repo.open(tree.getObjectId(0)).getBytes());
      }
    }
  }

  @Test
  @DisplayName("edits, creations and deletions land in one commit with the author and message")
  void atomicCommit() throws Exception {
    final var sha =
        service.commit(
            "o",
            "r",
            author,
            "dev",
            request(
                head,
                new Change("README.md", "changed\n", false),
                new Change("docs/new.md", "new\n", false),
                new Change("src/b.txt", null, true)));

    assertThat(contentAt("README.md")).isEqualTo("changed\n");
    assertThat(contentAt("docs/new.md")).isEqualTo("new\n");
    assertThat(contentAt("src/b.txt")).isNull();
    assertThat(contentAt("src/a.txt")).isEqualTo("a\n");
    try (final var repo = Git.open(dir.toFile()).getRepository();
        final var walk = new RevWalk(repo)) {
      final var commit = walk.parseCommit(repo.resolve("refs/heads/main"));
      assertThat(commit.getName()).isEqualTo(sha);
      assertThat(commit.getParent(0).getName()).isEqualTo(head);
      assertThat(commit.getAuthorIdent().getName()).isEqualTo("Dev");
      assertThat(commit.getFullMessage()).isEqualTo("Edit files\n\nMore detail\n");
    }
    verify(events).publish("o", "r", "main", "dev", sha);
    verify(cache).evictBranchScoped("o", "r", "main");
  }

  @Test
  @DisplayName("a branch that moved since the editor opened is refused and nothing is written")
  void staleHead() throws Exception {
    final var stale = head;
    write("late.txt", "late\n");

    assertThatThrownBy(
            () ->
                service.commit(
                    "o", "r", author, "dev", request(stale, new Change("README.md", "x\n", false))))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("changed");
    assertThat(contentAt("README.md")).isEqualTo("hello\n");
  }

  @Test
  @DisplayName("invalid, duplicate and colliding paths are rejected")
  void pathValidation() {
    final var cases =
        List.<Change[]>of(
            new Change[] {new Change("../x", "x", false)},
            new Change[] {new Change("/abs", "x", false)},
            new Change[] {new Change(".git/config", "x", false)},
            new Change[] {new Change("a//b", "x", false)},
            new Change[] {new Change("README.md", "1", false), new Change("README.md", "2", false)},
            new Change[] {new Change("src", "x", false)},
            new Change[] {new Change("README.md/inner", "x", false)},
            new Change[] {new Change("nope.txt", null, true)},
            new Change[] {new Change("README.md", null, false)},
            new Change[] {new Change("n", "x", false), new Change("n/m", "x", false)});
    for (final var changes : cases) {
      assertThatThrownBy(() -> service.commit("o", "r", author, "dev", request(head, changes)))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Test
  @DisplayName("a protected branch and an unknown branch are refused")
  void branchRules() {
    doThrow(new BadRequestException("Protected"))
        .when(protection)
        .assertDirectWriteAllowed("o", "r", "main");
    assertThatThrownBy(
            () ->
                service.commit("o", "r", author, "dev", request(head, new Change("a", "x", false))))
        .isInstanceOf(BadRequestException.class);

    org.mockito.Mockito.reset(protection);
    assertThatThrownBy(
            () ->
                service.commit(
                    "o",
                    "r",
                    author,
                    "dev",
                    new CommitChangesRequest(
                        "ghost", head, "m", null, List.of(new Change("a", "x", false)))))
        .isInstanceOf(ItemNotFoundException.class);
  }
}
