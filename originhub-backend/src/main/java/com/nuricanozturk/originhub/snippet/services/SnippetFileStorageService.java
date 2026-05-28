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
package com.nuricanozturk.originhub.snippet.services;

import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

@Slf4j
@Service
@NullMarked
public class SnippetFileStorageService {

  @Value("${originhub.git.repo-root}")
  private String repoRoot;

  public void writeFile(
      final String username, final UUID snippetId, final UUID fileId, final String content) {

    final var path = this.snippetFilePath(username, snippetId, fileId);
    try {
      Files.createDirectories(path.getParent());
      Files.writeString(path, content, StandardCharsets.UTF_8);
    } catch (final IOException e) {
      log.error("Failed to write snippet file {}", path, e);
      throw new ErrorOccurredException("Failed to write snippet file");
    }
  }

  public String readFile(final String username, final UUID snippetId, final UUID fileId) {

    final var path = this.snippetFilePath(username, snippetId, fileId);
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (final IOException e) {
      log.error("Failed to read snippet file {}", path, e);
      throw new ErrorOccurredException("Failed to read snippet file");
    }
  }

  public void writeRevisionFile(
      final String username,
      final UUID snippetId,
      final UUID revisionId,
      final UUID revisionFileId,
      final String content) {

    final var path = this.revisionFilePath(username, snippetId, revisionId, revisionFileId);
    try {
      Files.createDirectories(path.getParent());
      Files.writeString(path, content, StandardCharsets.UTF_8);
    } catch (final IOException e) {
      log.error("Failed to write revision file {}", path, e);
      throw new ErrorOccurredException("Failed to write revision file");
    }
  }

  public String readRevisionFile(
      final String username,
      final UUID snippetId,
      final UUID revisionId,
      final UUID revisionFileId) {

    final var path = this.revisionFilePath(username, snippetId, revisionId, revisionFileId);
    try {
      return Files.readString(path, StandardCharsets.UTF_8);
    } catch (final IOException e) {
      log.error("Failed to read revision file {}", path, e);
      throw new ErrorOccurredException("Failed to read revision file");
    }
  }

  public void copySnippetFiles(
      final String fromUsername,
      final UUID fromSnippetId,
      final String toUsername,
      final UUID toSnippetId) {

    final var src = this.snippetDir(fromUsername, fromSnippetId);
    final var dst = this.snippetDir(toUsername, toSnippetId);
    if (!Files.exists(src)) {
      return;
    }
    try {
      Files.createDirectories(dst);
      try (var stream = Files.list(src)) {
        for (final var file : stream.filter(p -> !Files.isDirectory(p)).toList()) {
          Files.copy(file, dst.resolve(file.getFileName()));
        }
      }
    } catch (final IOException e) {
      log.error("Failed to copy snippet files from {} to {}", src, dst, e);
      throw new ErrorOccurredException("Failed to copy snippet files");
    }
  }

  public void deleteCurrentFiles(final String username, final UUID snippetId) {
    final var dir = this.snippetDir(username, snippetId);
    if (!Files.exists(dir)) {
      return;
    }
    try (var stream = Files.list(dir)) {
      for (final var p : stream.filter(Files::isRegularFile).toList()) {
        Files.deleteIfExists(p);
      }
    } catch (final IOException e) {
      log.warn("Failed to delete current files in {}", dir, e);
    }
  }

  public void deleteSnippetDir(final String username, final UUID snippetId) {
    final var path = this.snippetDir(username, snippetId);
    try {
      FileSystemUtils.deleteRecursively(path);
    } catch (final IOException e) {
      log.warn("Failed to delete snippet dir {}", path, e);
    }
  }

  private Path snippetDir(final String username, final UUID snippetId) {
    return Path.of(this.repoRoot, username, "gists", snippetId.toString());
  }

  private Path snippetFilePath(final String username, final UUID snippetId, final UUID fileId) {
    return this.snippetDir(username, snippetId).resolve(fileId.toString());
  }

  private Path revisionFilePath(
      final String username, final UUID snippetId, final UUID revisionId, final UUID revFileId) {
    return this.snippetDir(username, snippetId)
        .resolve("revisions")
        .resolve(revisionId.toString())
        .resolve(revFileId.toString());
  }
}
