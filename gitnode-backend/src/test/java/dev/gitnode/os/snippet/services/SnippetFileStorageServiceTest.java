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
package dev.gitnode.os.snippet.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("SnippetFileStorageService unit tests")
class SnippetFileStorageServiceTest {

  @TempDir Path tempDir;

  private SnippetFileStorageService storageService;

  @BeforeEach
  void setUp() {
    storageService = new SnippetFileStorageService();
    ReflectionTestUtils.setField(storageService, "repoRoot", tempDir.toString());
  }

  // ──────────────────────────── writeFile / readFile ────────────────────────────

  @Nested
  @DisplayName("writeFile() and readFile()")
  class WriteReadFile {

    @Test
    @DisplayName("writes content to disk and reads it back correctly")
    void writesAndReadsContent() {
      UUID snippetId = UUID.randomUUID();
      UUID fileId = UUID.randomUUID();
      String content = "print('hello world')";

      storageService.writeFile("alice", snippetId, fileId, content);
      String result = storageService.readFile("alice", snippetId, fileId);

      assertThat(result).isEqualTo(content);
    }

    @Test
    @DisplayName("creates intermediate directories automatically")
    void createsDirectories() {
      UUID snippetId = UUID.randomUUID();
      UUID fileId = UUID.randomUUID();

      storageService.writeFile("bob", snippetId, fileId, "content");

      Path expected = tempDir.resolve("bob/gists/" + snippetId + "/" + fileId);
      assertThat(expected).exists();
    }

    @Test
    @DisplayName("overwrites existing content on second write")
    void overwritesContent() {
      UUID snippetId = UUID.randomUUID();
      UUID fileId = UUID.randomUUID();

      storageService.writeFile("alice", snippetId, fileId, "version 1");
      storageService.writeFile("alice", snippetId, fileId, "version 2");
      String result = storageService.readFile("alice", snippetId, fileId);

      assertThat(result).isEqualTo("version 2");
    }

    @Test
    @DisplayName("preserves unicode content (Turkish characters)")
    void preservesUnicode() {
      UUID snippetId = UUID.randomUUID();
      UUID fileId = UUID.randomUUID();
      String content = "// Türkçe yorum: değişken adı";

      storageService.writeFile("alice", snippetId, fileId, content);
      String result = storageService.readFile("alice", snippetId, fileId);

      assertThat(result).isEqualTo(content);
    }

    @Test
    @DisplayName("throws ErrorOccurredException when reading non-existent file")
    void throws_whenFileNotFound() {
      UUID snippetId = UUID.randomUUID();
      UUID fileId = UUID.randomUUID();

      assertThatThrownBy(() -> storageService.readFile("alice", snippetId, fileId))
          .isInstanceOf(ErrorOccurredException.class)
          .hasMessageContaining("Failed to read snippet file");
    }

    @Test
    @DisplayName("handles multiline content correctly")
    void handlesMultilineContent() {
      UUID snippetId = UUID.randomUUID();
      UUID fileId = UUID.randomUUID();
      String content = "line1\nline2\nline3\n";

      storageService.writeFile("alice", snippetId, fileId, content);
      String result = storageService.readFile("alice", snippetId, fileId);

      assertThat(result).isEqualTo(content);
    }
  }

  // ──────────────────────────── revision files ────────────────────────────

  @Nested
  @DisplayName("writeRevisionFile() and readRevisionFile()")
  class WriteReadRevisionFile {

    @Test
    @DisplayName("writes and reads revision file content correctly")
    void writesAndReadsRevisionContent() {
      UUID snippetId = UUID.randomUUID();
      UUID revisionId = UUID.randomUUID();
      UUID revFileId = UUID.randomUUID();
      String content = "const x = 42;";

      storageService.writeRevisionFile("alice", snippetId, revisionId, revFileId, content);
      String result = storageService.readRevisionFile("alice", snippetId, revisionId, revFileId);

      assertThat(result).isEqualTo(content);
    }

    @Test
    @DisplayName("stores revision files under revisions/ subdir")
    void storesUnderRevisionsSubdir() {
      UUID snippetId = UUID.randomUUID();
      UUID revisionId = UUID.randomUUID();
      UUID revFileId = UUID.randomUUID();

      storageService.writeRevisionFile("alice", snippetId, revisionId, revFileId, "x");

      Path expected =
          tempDir.resolve(
              "alice/gists/" + snippetId + "/revisions/" + revisionId + "/" + revFileId);
      assertThat(expected).exists();
    }

    @Test
    @DisplayName("different revisions of same snippet are independent")
    void differentRevisions_areIndependent() {
      UUID snippetId = UUID.randomUUID();
      UUID rev1 = UUID.randomUUID();
      UUID rev2 = UUID.randomUUID();
      UUID fileId = UUID.randomUUID();

      storageService.writeRevisionFile("alice", snippetId, rev1, fileId, "v1 content");
      storageService.writeRevisionFile("alice", snippetId, rev2, fileId, "v2 content");

      assertThat(storageService.readRevisionFile("alice", snippetId, rev1, fileId))
          .isEqualTo("v1 content");
      assertThat(storageService.readRevisionFile("alice", snippetId, rev2, fileId))
          .isEqualTo("v2 content");
    }

    @Test
    @DisplayName("throws ErrorOccurredException when revision file not found")
    void throws_whenRevisionFileMissing() {
      UUID snippetId = UUID.randomUUID();
      UUID revisionId = UUID.randomUUID();
      UUID revFileId = UUID.randomUUID();

      assertThatThrownBy(
              () -> storageService.readRevisionFile("alice", snippetId, revisionId, revFileId))
          .isInstanceOf(ErrorOccurredException.class)
          .hasMessageContaining("Failed to read revision file");
    }
  }

  // ──────────────────────────── deleteCurrentFiles ────────────────────────────

  @Nested
  @DisplayName("deleteCurrentFiles()")
  class DeleteCurrentFiles {

    @Test
    @DisplayName("removes all regular files in snippet dir but keeps revisions/ subdir")
    void deletesFiles_keepsRevisionsDir() throws IOException {
      UUID snippetId = UUID.randomUUID();
      UUID fileId1 = UUID.randomUUID();
      UUID fileId2 = UUID.randomUUID();
      UUID revisionId = UUID.randomUUID();
      UUID revFileId = UUID.randomUUID();

      storageService.writeFile("alice", snippetId, fileId1, "content1");
      storageService.writeFile("alice", snippetId, fileId2, "content2");
      storageService.writeRevisionFile("alice", snippetId, revisionId, revFileId, "rev content");

      storageService.deleteCurrentFiles("alice", snippetId);

      Path snippetDir = tempDir.resolve("alice/gists/" + snippetId);
      Path revDir = snippetDir.resolve("revisions");
      Path revFile = revDir.resolve(revisionId.toString()).resolve(revFileId.toString());

      assertThat(snippetDir.resolve(fileId1.toString())).doesNotExist();
      assertThat(snippetDir.resolve(fileId2.toString())).doesNotExist();
      assertThat(revFile).exists();
    }

    @Test
    @DisplayName("does nothing when snippet directory does not exist")
    void doesNothing_whenDirMissing() {
      UUID snippetId = UUID.randomUUID();

      // should not throw
      storageService.deleteCurrentFiles("alice", snippetId);
    }
  }

  // ──────────────────────────── deleteSnippetDir ────────────────────────────

  @Nested
  @DisplayName("deleteSnippetDir()")
  class DeleteSnippetDir {

    @Test
    @DisplayName("removes snippet directory including all files and revisions")
    void removesEntireDir() throws IOException {
      UUID snippetId = UUID.randomUUID();
      UUID fileId = UUID.randomUUID();
      UUID revId = UUID.randomUUID();
      UUID revFileId = UUID.randomUUID();

      storageService.writeFile("alice", snippetId, fileId, "code");
      storageService.writeRevisionFile("alice", snippetId, revId, revFileId, "old code");

      storageService.deleteSnippetDir("alice", snippetId);

      Path snippetDir = tempDir.resolve("alice/gists/" + snippetId);
      assertThat(snippetDir).doesNotExist();
    }

    @Test
    @DisplayName("does nothing when snippet directory does not exist")
    void doesNothing_whenDirMissing() {
      UUID snippetId = UUID.randomUUID();

      // should not throw
      storageService.deleteSnippetDir("alice", snippetId);
    }
  }

  // ──────────────────────────── copySnippetFiles ────────────────────────────

  @Nested
  @DisplayName("copySnippetFiles()")
  class CopySnippetFiles {

    @Test
    @DisplayName("copies all regular files to target snippet directory")
    void copiesFiles_toTargetDir() throws IOException {
      UUID srcId = UUID.randomUUID();
      UUID dstId = UUID.randomUUID();
      UUID file1 = UUID.randomUUID();
      UUID file2 = UUID.randomUUID();

      storageService.writeFile("alice", srcId, file1, "file one");
      storageService.writeFile("alice", srcId, file2, "file two");

      storageService.copySnippetFiles("alice", srcId, "bob", dstId);

      Path dst = tempDir.resolve("bob/gists/" + dstId);
      assertThat(dst.resolve(file1.toString())).exists();
      assertThat(dst.resolve(file2.toString())).exists();

      assertThat(Files.readString(dst.resolve(file1.toString()), StandardCharsets.UTF_8))
          .isEqualTo("file one");
      assertThat(Files.readString(dst.resolve(file2.toString()), StandardCharsets.UTF_8))
          .isEqualTo("file two");
    }

    @Test
    @DisplayName("does not copy revisions/ subdir during fork")
    void doesNotCopy_revisionsDir() {
      UUID srcId = UUID.randomUUID();
      UUID dstId = UUID.randomUUID();
      UUID fileId = UUID.randomUUID();
      UUID revId = UUID.randomUUID();
      UUID revFileId = UUID.randomUUID();

      storageService.writeFile("alice", srcId, fileId, "code");
      storageService.writeRevisionFile("alice", srcId, revId, revFileId, "old");

      storageService.copySnippetFiles("alice", srcId, "bob", dstId);

      Path dstRevisions = tempDir.resolve("bob/gists/" + dstId + "/revisions");
      assertThat(dstRevisions).doesNotExist();
    }

    @Test
    @DisplayName("does nothing when source directory does not exist")
    void doesNothing_whenSrcMissing() {
      UUID srcId = UUID.randomUUID();
      UUID dstId = UUID.randomUUID();

      // should not throw
      storageService.copySnippetFiles("alice", srcId, "bob", dstId);
    }

    @Test
    @DisplayName("source files are unchanged after copy")
    void sourceFilesUnchanged_afterCopy() {
      UUID srcId = UUID.randomUUID();
      UUID dstId = UUID.randomUUID();
      UUID fileId = UUID.randomUUID();

      storageService.writeFile("alice", srcId, fileId, "original");
      storageService.copySnippetFiles("alice", srcId, "bob", dstId);

      assertThat(storageService.readFile("alice", srcId, fileId)).isEqualTo("original");
    }
  }
}
