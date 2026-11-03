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
package dev.gitnode.os.wiki.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gitnode.os.shared.cache.CachePatternEvictor;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.git.provider.GitProvider;
import dev.gitnode.os.wiki.dtos.WikiPageSummary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("WikiService unit tests")
class WikiServiceTest {

  private static final WikiService.Editor ALICE = new WikiService.Editor("Alice", "a@x.io");
  private static final WikiService.Editor BOB = new WikiService.Editor("Bob", "b@x.io");

  @TempDir Path root;

  private WikiService wiki;

  @BeforeEach
  void setUp() throws IOException {
    final var git = new GitProvider();
    ReflectionTestUtils.setField(git, "repoRoot", this.root.toString());
    final var path = this.root.resolve("alice").resolve("demo.git");
    Files.createDirectories(path);
    git.createJGitRepo(path);
    this.wiki = new WikiService(git, org.mockito.Mockito.mock(CachePatternEvictor.class));
  }

  @Test
  @DisplayName("a repository without wiki commits lists no pages")
  void list_empty() throws IOException {
    assertThat(this.wiki.list("alice", "demo", 0).content()).isEmpty();
  }

  @Test
  @DisplayName("save creates a page that get returns with its author")
  void save_createsPage() throws IOException {
    final var page = this.wiki.save("alice", "demo", "Getting-Started", "# Hi", null, null, ALICE);

    assertThat(page.content()).isEqualTo("# Hi");
    assertThat(page.title()).isEqualTo("Getting Started");
    assertThat(page.lastEditor()).isEqualTo("Alice");
    assertThat(this.wiki.get("alice", "demo", "Getting-Started", null).blobSha())
        .isEqualTo(page.blobSha());
  }

  @Test
  @DisplayName("Home is listed first, the rest alphabetically")
  void list_ordersHomeFirst() throws IOException {
    this.wiki.save("alice", "demo", "Zebra", "z", null, null, ALICE);
    this.wiki.save("alice", "demo", "Apple", "a", null, null, ALICE);
    this.wiki.save("alice", "demo", "Home", "h", null, null, ALICE);

    assertThat(this.wiki.list("alice", "demo", 0).content())
        .extracting(WikiPageSummary::slug)
        .containsExactly("Home", "Apple", "Zebra");
  }

  @Test
  @DisplayName("saving with a stale base is rejected instead of overwriting")
  void save_rejectsStaleBase() throws IOException {
    final var first = this.wiki.save("alice", "demo", "Home", "one", null, null, ALICE);
    this.wiki.save("alice", "demo", "Home", "two", null, first.blobSha(), BOB);

    assertThatThrownBy(
            () -> this.wiki.save("alice", "demo", "Home", "three", null, first.blobSha(), ALICE))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("changed by someone else");
    assertThat(this.wiki.get("alice", "demo", "Home", null).content()).isEqualTo("two");
  }

  @Test
  @DisplayName("creating a page that already exists is rejected")
  void save_rejectsCreateOverExisting() throws IOException {
    this.wiki.save("alice", "demo", "Home", "one", null, null, ALICE);

    assertThatThrownBy(() -> this.wiki.save("alice", "demo", "Home", "two", null, null, BOB))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  @DisplayName("history lists every edit of a page and old revisions stay readable")
  void history_andRevisions() throws IOException {
    final var first = this.wiki.save("alice", "demo", "Home", "one", "first", null, ALICE);
    this.wiki.save("alice", "demo", "Other", "x", null, null, ALICE);
    this.wiki.save("alice", "demo", "Home", "two", "second", first.blobSha(), BOB);

    final var history = this.wiki.history("alice", "demo", "Home", 0).content();

    assertThat(history).extracting("message").containsExactly("second", "first");
    assertThat(history).extracting("author").containsExactly("Bob", "Alice");
    assertThat(this.wiki.get("alice", "demo", "Home", history.get(1).sha()).content())
        .isEqualTo("one");
  }

  @Test
  @DisplayName("unknown revisions and pages are not found")
  void get_notFound() throws IOException {
    this.wiki.save("alice", "demo", "Home", "one", null, null, ALICE);

    assertThatThrownBy(() -> this.wiki.get("alice", "demo", "Missing", null))
        .isInstanceOf(ItemNotFoundException.class);
    assertThatThrownBy(() -> this.wiki.get("alice", "demo", "Home", "0".repeat(40)))
        .isInstanceOf(ItemNotFoundException.class);
    assertThatThrownBy(() -> this.wiki.get("alice", "demo", "Home", "not-a-sha"))
        .isInstanceOf(ItemNotFoundException.class);
  }

  @Test
  @DisplayName("delete removes only that page")
  void delete_removesPage() throws IOException {
    this.wiki.save("alice", "demo", "Home", "h", null, null, ALICE);
    this.wiki.save("alice", "demo", "Other", "o", null, null, ALICE);

    this.wiki.delete("alice", "demo", "Other", null, BOB);

    assertThat(this.wiki.list("alice", "demo", 0).content())
        .extracting(WikiPageSummary::slug)
        .containsExactly("Home");
    assertThatThrownBy(() -> this.wiki.delete("alice", "demo", "Other", null, BOB))
        .isInstanceOf(ItemNotFoundException.class);
  }

  @Test
  @DisplayName("page names that could escape the wiki are rejected")
  void path_rejectsUnsafeSlugs() {
    for (final var slug : new String[] {"../x", "a/b", ".hidden", "", "a b", "x".repeat(101)}) {
      assertThatThrownBy(() -> this.wiki.save("alice", "demo", slug, "c", null, null, ALICE))
          .as(slug)
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Test
  @DisplayName("the wiki branch never shows up among the repository branches")
  void wikiRef_isNotABranch() throws IOException {
    this.wiki.save("alice", "demo", "Home", "h", null, null, ALICE);

    try (final var repo =
        new org.eclipse.jgit.storage.file.FileRepositoryBuilder()
            .setGitDir(this.root.resolve("alice").resolve("demo.git").toFile())
            .build()) {
      assertThat(repo.getRefDatabase().getRefsByPrefix("refs/heads/"))
          .extracting(org.eclipse.jgit.lib.Ref::getName)
          .containsExactly("refs/heads/main");
      assertThat(repo.exactRef(WikiService.REF)).isNotNull();
    }
  }

  @Test
  @DisplayName("lists and history come in fixed-size pages")
  void lists_arePaged() throws IOException {
    for (int i = 0; i < 55; i++) {
      this.wiki.save("alice", "demo", "Page-%02d".formatted(i), "x", null, null, ALICE);
    }

    final var first = this.wiki.list("alice", "demo", 0);
    final var second = this.wiki.list("alice", "demo", 1);

    assertThat(first.content()).hasSize(50);
    assertThat(second.content()).hasSize(5);
    assertThat(first.totalElements()).isEqualTo(55);
    assertThat(first.totalPages()).isEqualTo(2);
    assertThat(this.wiki.list("alice", "demo", 9).content()).isEmpty();

    var base = this.wiki.get("alice", "demo", "Page-00", null).blobSha();
    for (int i = 0; i < 25; i++) {
      base = this.wiki.save("alice", "demo", "Page-00", "v" + i, null, base, BOB).blobSha();
    }
    assertThat(this.wiki.history("alice", "demo", "Page-00", 0).content()).hasSize(20);
    assertThat(this.wiki.history("alice", "demo", "Page-00", 1).content()).hasSize(6);
  }
}
