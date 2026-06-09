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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.gitnode.os.shared.cache.RepoCacheInvalidator;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.git.provider.GitProvider;
import dev.gitnode.os.tree.dtos.BlobResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TreeNonTxService unit tests")
class TreeNonTxServiceTest {

  @TempDir Path workDir;

  @Mock private GitProvider gitProvider;

  @Mock private RepoCacheInvalidator cacheInvalidator;

  @InjectMocks private TreeNonTxService service;

  private Git git;
  private Path gitDir;
  private String branch;

  @BeforeEach
  void setUp() throws Exception {
    git = Git.init().setDirectory(workDir.toFile()).call();
    gitDir = workDir.resolve(".git");
    branch = git.getRepository().getBranch();
    when(gitProvider.open(any(), any()))
        .thenAnswer(
            inv ->
                new FileRepositoryBuilder().setGitDir(gitDir.toFile()).readEnvironment().build());
  }

  @AfterEach
  void tearDown() {
    git.close();
  }

  private void writeAndCommit(String message, String... fileContents) throws Exception {
    for (int i = 0; i < fileContents.length; i += 2) {
      Files.writeString(workDir.resolve(fileContents[i]), fileContents[i + 1]);
      git.add().addFilepattern(fileContents[i]).call();
    }
    git.commit()
        .setMessage(message)
        .setAuthor("Author", "author@test.com")
        .setCommitter("Author", "author@test.com")
        .call();
  }

  @Nested
  @DisplayName("getTree()")
  class GetTree {

    @Test
    @DisplayName("all entries carry lastCommitMessage in a single-commit repo")
    void allEntriesHaveLastCommitMessage_singleInitialCommit() throws Exception {
      writeAndCommit("Initial commit", "README.md", "# hello", "Main.java", "class Main {}");

      final var result = service.getTree("owner", "repo", branch, "");

      assertThat(result.entries()).hasSize(2);
      assertThat(result.entries())
          .extracting(e -> e.lastCommitMessage())
          .doesNotContainNull()
          .containsOnly("Initial commit");
    }

    @Test
    @DisplayName("second entry also carries lastCommitMessage (RevCommit flag-sharing fix)")
    void secondEntry_alsoHasLastCommitMessage_afterFix() throws Exception {
      writeAndCommit(
          "Add files",
          "alpha.txt",
          "alpha content",
          "beta.txt",
          "beta content",
          "gamma.txt",
          "gamma content");

      final var result = service.getTree("owner", "repo", branch, "");

      assertThat(result.entries()).hasSize(3);
      // Without the fix, only the first entry had a non-null lastCommitMessage.
      final var nullCount =
          result.entries().stream().filter(e -> e.lastCommitMessage() == null).count();
      assertThat(nullCount).isZero();
    }

    @Test
    @DisplayName("each file shows its own most recent commit message after separate commits")
    void eachFile_showsOwnCommitMessage_afterSeparateCommits() throws Exception {
      writeAndCommit("Add alpha", "alpha.txt", "v1");
      writeAndCommit("Update beta", "beta.txt", "v1");

      final var result = service.getTree("owner", "repo", branch, "");

      assertThat(result.entries()).hasSize(2);
      final var alphaMsg =
          result.entries().stream()
              .filter(e -> e.name().equals("alpha.txt"))
              .findFirst()
              .orElseThrow()
              .lastCommitMessage();
      final var betaMsg =
          result.entries().stream()
              .filter(e -> e.name().equals("beta.txt"))
              .findFirst()
              .orElseThrow()
              .lastCommitMessage();
      assertThat(alphaMsg).isEqualTo("Add alpha");
      assertThat(betaMsg).isEqualTo("Update beta");
    }

    @Test
    @DisplayName("throws ItemNotFoundException when branch does not exist")
    void throwsItemNotFoundException_whenBranchMissing() {
      assertThatThrownBy(() -> service.getTree("owner", "repo", "ghost", ""))
          .isInstanceOf(ItemNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("updateFile()")
  class UpdateFile {

    @Test
    @DisplayName("returns BlobResponse with updated content after commit")
    void returnsUpdatedBlobResponse_afterCommit() throws Exception {
      writeAndCommit("Initial commit", "README.md", "# original");

      final var newContent = "# updated".getBytes(StandardCharsets.UTF_8);
      final var author =
          new PersonIdent("Editor", "editor@test.com", Instant.now(), ZoneOffset.UTC);

      final var result =
          service.updateFile(
              "owner", "repo", branch, "README.md", newContent, "Update README", author);

      assertThat(result.path()).isEqualTo("README.md");
      assertThat(result.name()).isEqualTo("README.md");
      assertThat(result.isBinary()).isFalse();
      assertThat(Base64.getDecoder().decode(result.content())).isEqualTo(newContent);
    }

    @Test
    @DisplayName("advances branch HEAD to new commit with correct message and parent")
    void advancesBranchHead_withCorrectParent() throws Exception {
      writeAndCommit("Initial commit", "README.md", "# original");

      final ObjectId initialHead;
      try (Repository r =
          new FileRepositoryBuilder().setGitDir(gitDir.toFile()).readEnvironment().build()) {
        initialHead = r.findRef(Constants.R_HEADS + branch).getObjectId().copy();
      }

      final var author =
          new PersonIdent("Editor", "editor@test.com", Instant.now(), ZoneOffset.UTC);
      service.updateFile(
          "owner",
          "repo",
          branch,
          "README.md",
          "# updated".getBytes(StandardCharsets.UTF_8),
          "Update README",
          author);

      try (Repository r =
              new FileRepositoryBuilder().setGitDir(gitDir.toFile()).readEnvironment().build();
          RevWalk rw = new RevWalk(r)) {
        final var newHead = rw.parseCommit(r.findRef(Constants.R_HEADS + branch).getObjectId());
        assertThat(newHead.getShortMessage()).isEqualTo("Update README");
        assertThat(newHead.getParentCount()).isEqualTo(1);
        assertThat(newHead.getParent(0).getId()).isEqualTo(initialHead);
      }
    }

    @Test
    @DisplayName("can create a new file that did not exist before")
    void createsNewFile_whenFileWasNotPreviouslyInTree() throws Exception {
      writeAndCommit("Initial commit", "existing.txt", "exists");

      final var author =
          new PersonIdent("Editor", "editor@test.com", Instant.now(), ZoneOffset.UTC);
      final BlobResponse result =
          service.updateFile(
              "owner",
              "repo",
              branch,
              "new-file.txt",
              "brand new".getBytes(StandardCharsets.UTF_8),
              "Add new-file.txt",
              author);

      assertThat(result.path()).isEqualTo("new-file.txt");
      assertThat(Base64.getDecoder().decode(result.content()))
          .isEqualTo("brand new".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("throws ItemNotFoundException when branch does not exist")
    void throwsItemNotFoundException_whenBranchMissing() {
      final var author =
          new PersonIdent("Editor", "editor@test.com", Instant.now(), ZoneOffset.UTC);

      assertThatThrownBy(
              () ->
                  service.updateFile(
                      "owner", "repo", "ghost", "README.md", "content".getBytes(), "msg", author))
          .isInstanceOf(ItemNotFoundException.class)
          .hasMessageContaining("branchNotFound");
    }
  }

  @Nested
  @DisplayName("getBlob()")
  class GetBlob {

    @Test
    @DisplayName("returns BlobResponse with base64 content for existing file")
    void returnsBlobResponse_forExistingFile() throws Exception {
      writeAndCommit("Initial commit", "hello.txt", "hello world");

      final BlobResponse result = service.getBlob("owner", "repo", branch, "hello.txt");

      assertThat(result.path()).isEqualTo("hello.txt");
      assertThat(result.isBinary()).isFalse();
      assertThat(new String(Base64.getDecoder().decode(result.content()), StandardCharsets.UTF_8))
          .isEqualTo("hello world");
    }

    @Test
    @DisplayName("throws ItemNotFoundException for file that does not exist")
    void throwsItemNotFoundException_whenFileMissing() throws Exception {
      writeAndCommit("Initial commit", "existing.txt", "content");

      assertThatThrownBy(() -> service.getBlob("owner", "repo", branch, "missing.txt"))
          .isInstanceOf(ItemNotFoundException.class);
    }
  }
}
