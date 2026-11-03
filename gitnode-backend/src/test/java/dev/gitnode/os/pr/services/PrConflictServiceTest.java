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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.branch.api.BranchProtectionPort;
import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.pr.dtos.PrConflictResolveForm;
import dev.gitnode.os.pr.dtos.PrConflictResolveForm.Resolution;
import dev.gitnode.os.pr.entities.PullRequest;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.repo.cache.RepoCacheInvalidator;
import dev.gitnode.os.repo.events.TreeCommitEventPublisher;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.git.provider.GitProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("PrConflictService with a real Git repository")
class PrConflictServiceTest {

  @TempDir Path dir;

  private final PrFinder finder = mock(PrFinder.class);
  private final GitProvider provider = mock(GitProvider.class);
  private final BranchProtectionPort protection = mock(BranchProtectionPort.class);
  private final TreeCommitEventPublisher events = mock(TreeCommitEventPublisher.class);
  private final RepoCacheInvalidator cache = mock(RepoCacheInvalidator.class);
  private PrConflictService service;
  private Git git;
  private PullRequest pr;
  private final TenantRef actor =
      new TenantRef(UUID.randomUUID(), "dev", "dev@example.org", null, null, true);

  @BeforeEach
  void setUp() throws Exception {
    git = Git.init().setInitialBranch("main").setDirectory(dir.toFile()).call();
    final var repo = new RepoRef(UUID.randomUUID(), UUID.randomUUID(), "o", "r", false);
    pr = new PullRequest();
    pr.setStatus("OPEN");
    pr.setSourceBranch("feature");
    pr.setTargetBranch("main");
    final Repository gitRepo = git.getRepository();
    when(finder.findRepo("o", "r")).thenReturn(repo);
    when(finder.findRepoForUpdate("o", "r")).thenReturn(repo);
    when(finder.findPr(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(pr);
    when(finder.findTenant(any())).thenReturn(actor);
    when(provider.open("o", "r")).thenAnswer(i -> Git.open(dir.toFile()).getRepository());
    assertThat(gitRepo).isNotNull();
    service = new PrConflictService(finder, provider, protection, events, cache);
  }

  private void write(final String name, final String body, final String message) throws Exception {
    Files.writeString(dir.resolve(name), body);
    git.add().addFilepattern(".").call();
    git.commit().setMessage(message).setAuthor("a", "a@example.org").call();
  }

  private void divergeOnSameLine() throws Exception {
    write("a.txt", "one\ntwo\nthree\n", "base");
    git.checkout().setCreateBranch(true).setName("feature").call();
    write("a.txt", "one\nFEATURE\nthree\n", "feature change");
    git.checkout().setName("main").call();
    write("a.txt", "one\nMAIN\nthree\n", "main change");
  }

  @Test
  @DisplayName("clean branches are mergeable")
  void cleanIsMergeable() throws Exception {
    write("a.txt", "one\n", "base");
    git.checkout().setCreateBranch(true).setName("feature").call();
    write("b.txt", "b\n", "feature");
    git.checkout().setName("main").call();
    write("c.txt", "c\n", "main");

    final var info = service.inspect("o", "r", 1);

    assertThat(info.mergeable()).isTrue();
    assertThat(info.files()).isEmpty();
  }

  @Test
  @DisplayName("same-line edits are reported with conflict markers")
  void reportsConflict() throws Exception {
    divergeOnSameLine();

    final var info = service.inspect("o", "r", 1);

    assertThat(info.mergeable()).isFalse();
    assertThat(info.files()).hasSize(1);
    final var file = info.files().get(0);
    assertThat(file.path()).isEqualTo("a.txt");
    assertThat(file.resolvable()).isTrue();
    assertThat(file.content())
        .contains("<<<<<<< feature", "FEATURE", "=======", "MAIN", ">>>>>>> main");
  }

  @Test
  @DisplayName("resolving writes a merge commit on the source branch with both parents")
  void resolvesIntoMergeCommit() throws Exception {
    divergeOnSameLine();
    final var info = service.inspect("o", "r", 1);

    final var sha =
        service.resolve(
            "o",
            "r",
            1,
            UUID.randomUUID(),
            new PrConflictResolveForm(
                info.sourceSha(),
                info.targetSha(),
                List.of(new Resolution("a.txt", "one\nBOTH\nthree\n")),
                null));

    try (final var repo = git.getRepository();
        final var walk = new org.eclipse.jgit.revwalk.RevWalk(repo)) {
      final var head = walk.parseCommit(repo.resolve("refs/heads/feature"));
      assertThat(head.getName()).isEqualTo(sha);
      assertThat(head.getParentCount()).isEqualTo(2);
      assertThat(head.getParent(0).getName()).isEqualTo(info.sourceSha());
      assertThat(head.getParent(1).getName()).isEqualTo(info.targetSha());
      assertThat(head.getFullMessage()).isEqualTo("Merge branch 'main' into feature");
    }
    git.checkout().setName("feature").call();
    assertThat(Files.readString(dir.resolve("a.txt"))).isEqualTo("one\nBOTH\nthree\n");
    assertThat(service.inspect("o", "r", 1).mergeable()).isTrue();
    verify(events).publish("o", "r", "feature", "dev", sha);
  }

  @Test
  @DisplayName("remaining markers, missing files and stale heads are rejected")
  void rejectsInvalidResolutions() throws Exception {
    divergeOnSameLine();
    final var info = service.inspect("o", "r", 1);
    final var content = info.files().get(0).content();

    assertThatThrownBy(
            () ->
                service.resolve(
                    "o",
                    "r",
                    1,
                    UUID.randomUUID(),
                    new PrConflictResolveForm(
                        info.sourceSha(),
                        info.targetSha(),
                        List.of(new Resolution("a.txt", content)),
                        null)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Conflict markers");
    assertThatThrownBy(
            () ->
                service.resolve(
                    "o",
                    "r",
                    1,
                    UUID.randomUUID(),
                    new PrConflictResolveForm(
                        info.sourceSha(),
                        info.targetSha(),
                        List.of(new Resolution("other.txt", "x")),
                        null)))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(
            () ->
                service.resolve(
                    "o",
                    "r",
                    1,
                    UUID.randomUUID(),
                    new PrConflictResolveForm(
                        "0".repeat(40),
                        info.targetSha(),
                        List.of(new Resolution("a.txt", "ok\n")),
                        null)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("changed");
  }

  @Test
  @DisplayName("protected source branch rejects web resolution")
  void protectedSourceIsRejected() throws Exception {
    divergeOnSameLine();
    final var info = service.inspect("o", "r", 1);
    doThrow(new BadRequestException("Protected"))
        .when(protection)
        .assertDirectWriteAllowed("o", "r", "feature");

    assertThatThrownBy(
            () ->
                service.resolve(
                    "o",
                    "r",
                    1,
                    UUID.randomUUID(),
                    new PrConflictResolveForm(
                        info.sourceSha(),
                        info.targetSha(),
                        List.of(new Resolution("a.txt", "ok\n")),
                        null)))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  @DisplayName("delete/modify conflicts are not resolvable in the browser")
  void deleteModifyNotResolvable() throws Exception {
    write("a.txt", "one\ntwo\n", "base");
    git.checkout().setCreateBranch(true).setName("feature").call();
    write("a.txt", "one\nchanged\n", "feature modifies");
    git.checkout().setName("main").call();
    git.rm().addFilepattern("a.txt").call();
    git.commit().setMessage("main deletes").setAuthor("a", "a@example.org").call();

    final var info = service.inspect("o", "r", 1);

    assertThat(info.mergeable()).isFalse();
    assertThat(info.files()).hasSize(1);
    assertThat(info.files().get(0).resolvable()).isFalse();
    assertThat(info.files().get(0).reason()).contains("locally");
  }
}
