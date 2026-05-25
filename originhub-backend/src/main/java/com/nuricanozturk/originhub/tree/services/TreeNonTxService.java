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
package com.nuricanozturk.originhub.tree.services;

import static com.nuricanozturk.originhub.tree.utils.LanguageExtensionUtils.detectLanguage;

import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.git.provider.GitProvider;
import com.nuricanozturk.originhub.tree.dtos.BlobResponse;
import com.nuricanozturk.originhub.tree.dtos.EntryType;
import com.nuricanozturk.originhub.tree.dtos.TreeEntry;
import com.nuricanozturk.originhub.tree.dtos.TreeResponse;
import com.nuricanozturk.originhub.tree.utils.ArchivePathSupport;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TreeNonTxService {

  private final @NonNull GitProvider gitProvider;

  public @NonNull TreeResponse getTree(
      final @NonNull String owner,
      final @NonNull String repoName,
      final @NonNull String branch,
      final @NonNull String path)
      throws IOException {

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {

      final var ref = gitRepo.findRef(Constants.R_HEADS + branch);

      if (ref == null) {
        throw new ItemNotFoundException("branchNotFound: " + branch);
      }

      try (final var revWalk = new RevWalk(gitRepo)) {
        final var headCommit = revWalk.parseCommit(ref.getObjectId());
        final var entries = this.listTree(gitRepo, headCommit, path);
        return new TreeResponse(branch, path, headCommit.getName(), entries);
      }
    }
  }

  public @NonNull BlobResponse getBlob(
      final @NonNull String owner,
      final @NonNull String repoName,
      final @NonNull String branch,
      final @NonNull String filePath)
      throws IOException {

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {

      final var ref = gitRepo.findRef(Constants.R_HEADS + branch);

      if (ref == null) {
        throw new ItemNotFoundException("branchNotFound: " + branch);
      }

      try (final var revWalk = new RevWalk(gitRepo)) {
        final var headCommit = revWalk.parseCommit(ref.getObjectId());

        try (final var treeWalk = TreeWalk.forPath(gitRepo, filePath, headCommit.getTree())) {

          if (treeWalk == null) {
            throw new ItemNotFoundException("fileNotFound: " + filePath);
          }

          final var objectId = treeWalk.getObjectId(0);
          final var loader = gitRepo.open(objectId);
          final var bytes = loader.getBytes();
          final var isBinary = this.isBinaryContent(bytes);
          final var content = Base64.getEncoder().encodeToString(bytes);
          final var fileName = Path.of(filePath).getFileName().toString();
          final var lineCount = isBinary ? 0 : this.countLines(bytes);

          return BlobResponse.builder()
              .path(filePath)
              .name(fileName)
              .sha(objectId.getName())
              .size(loader.getSize())
              .content(content)
              .isBinary(isBinary)
              .language(detectLanguage(fileName))
              .lineCount(lineCount)
              .build();
        }
      }
    }
  }

  public byte[] getRawContent(
      final @NonNull String owner,
      final @NonNull String repoName,
      final @NonNull String branch,
      final @NonNull String filePath)
      throws IOException {

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {

      final var ref = gitRepo.findRef(Constants.R_HEADS + branch);

      if (ref == null) {
        throw new ItemNotFoundException("Branch not found: " + branch);
      }

      try (final var revWalk = new RevWalk(gitRepo)) {
        final var headCommit = revWalk.parseCommit(ref.getObjectId());

        try (final var treeWalk = TreeWalk.forPath(gitRepo, filePath, headCommit.getTree())) {

          if (treeWalk == null) {
            throw new ItemNotFoundException("File not found: " + filePath);
          }

          return gitRepo.open(treeWalk.getObjectId(0)).getBytes();
        }
      }
    }
  }

  /**
   * Ensures {@code refs/heads/{branch}} exists so a streaming ZIP response can return 404 before
   * headers are committed.
   */
  public void assertBranchExists(
      final @NonNull String owner, final @NonNull String repoName, final @NonNull String branch)
      throws IOException {

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {
      if (gitRepo.findRef(Constants.R_HEADS + branch) == null) {
        throw new ItemNotFoundException("branchNotFound: " + branch);
      }
    }
  }

  /**
   * Writes a ZIP of the branch tip tree to {@code out}. Does not buffer the whole archive in
   * memory. Call {@link #assertBranchExists} first so missing branches surface as 404.
   */
  public void writeBranchZip(
      final @NonNull String owner,
      final @NonNull String repoName,
      final @NonNull String branch,
      final @NonNull OutputStream out)
      throws IOException {

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {

      final var ref = gitRepo.findRef(Constants.R_HEADS + branch);

      if (ref == null) {
        log.warn("Branch not found during zip write: {}", branch);
        return;
      }

      try (final var revWalk = new RevWalk(gitRepo)) {
        final var headCommit = revWalk.parseCommit(ref.getObjectId());
        final var prefix = ArchivePathSupport.archiveTreePrefix(owner, repoName, branch);

        try (final var git = Git.wrap(gitRepo)) {
          git.archive()
              .setFormat("zip")
              .setTree(headCommit.getTree())
              .setPrefix(prefix)
              .setOutputStream(out)
              .call();
        } catch (final GitAPIException e) {
          throw new IOException(e.getMessage(), e);
        }
      }
    }
  }

  private @NonNull List<TreeEntry> listTree(
      final @NonNull Repository gitRepo,
      final @NonNull RevCommit headCommit,
      final @NonNull String path)
      throws IOException {

    try (final var treeWalk = new TreeWalk(gitRepo)) {
      treeWalk.addTree(headCommit.getTree());
      treeWalk.setRecursive(false);

      if (!path.isEmpty()) {
        this.processForNonEmptyPath(treeWalk, path);
      }

      final var entries = new ArrayList<TreeEntry>();

      this.walkInTree(treeWalk, entries, path, gitRepo, headCommit);

      entries.sort(
          Comparator.comparing((TreeEntry e) -> e.type() == EntryType.TREE ? 0 : 1)
              .thenComparing(TreeEntry::name));

      return entries;
    }
  }

  @SuppressWarnings("java:S135")
  private void walkInTree(
      final @NonNull TreeWalk treeWalk,
      final @NonNull List<TreeEntry> entries,
      final @NonNull String path,
      final @NonNull Repository gitRepo,
      final @NonNull RevCommit headCommit)
      throws IOException {

    while (treeWalk.next()) {
      final var entryPath = treeWalk.getPathString();

      final String relativePath;

      if (path.isEmpty()) {
        relativePath = entryPath;
      } else if (entryPath.equals(path)) {
        // The directory itself — skip
        continue;
      } else if (entryPath.startsWith(path + "/")) {
        relativePath = entryPath.substring(path.length() + 1);
      } else {
        // Unrelated path — skip
        continue;
      }

      // Only direct children — no slashes in relative path
      if (relativePath.contains("/")) {
        continue;
      }

      this.addToTreeList(treeWalk, gitRepo, headCommit, entryPath, entries);
    }
  }

  private void addToTreeList(
      final @NonNull TreeWalk treeWalk,
      final @NonNull Repository gitRepo,
      final @NonNull RevCommit headCommit,
      final @NonNull String entryPath,
      final @NonNull List<TreeEntry> entries)
      throws IOException {

    final var isTree = treeWalk.getFileMode(0) == FileMode.TREE;
    final var objectId = treeWalk.getObjectId(0);
    final var sha = objectId.getName();
    final var name = treeWalk.getNameString();
    final var lastCommit = this.findLastCommitForPath(gitRepo, headCommit, entryPath);
    final long size = isTree ? 0L : gitRepo.open(objectId).getSize();

    entries.add(
        new TreeEntry(
            name,
            entryPath,
            isTree ? EntryType.TREE : EntryType.BLOB,
            sha,
            size,
            lastCommit != null ? lastCommit.getName() : null,
            lastCommit != null ? lastCommit.getShortMessage() : null,
            lastCommit != null ? lastCommit.getAuthorIdent().getWhenAsInstant() : null));
  }

  private void processForNonEmptyPath(final TreeWalk treeWalk, final @NonNull String path)
      throws IOException {

    treeWalk.setFilter(PathFilter.create(path));

    boolean found = false;
    while (treeWalk.next()) {
      if (treeWalk.isSubtree()) {
        final var currentPath = treeWalk.getPathString();
        treeWalk.enterSubtree();
        if (currentPath.equals(path)) {
          found = true;
          break;
        }
      }
    }

    if (!found) {
      throw new ItemNotFoundException("pathNotFound: " + path);
    }

    treeWalk.setFilter(TreeFilter.ALL);
  }

  private @Nullable RevCommit findLastCommitForPath(
      final @NonNull Repository gitRepo,
      final @NonNull RevCommit startCommit,
      final @NonNull String filePath)
      throws IOException {

    try (final var walk = new RevWalk(gitRepo)) {
      walk.markStart(startCommit);
      walk.setTreeFilter(AndTreeFilter.create(PathFilter.create(filePath), TreeFilter.ANY_DIFF));
      return walk.next();
    }
  }

  private boolean isBinaryContent(final byte[] bytes) {
    final int checkLength = Math.min(bytes.length, 8192);
    for (int i = 0; i < checkLength; i++) {
      if (bytes[i] == 0) return true;
    }
    return false;
  }

  private int countLines(final byte[] bytes) {
    int count = 1;

    for (final byte b : bytes) if (b == '\n') count++;

    return bytes.length == 0 ? 0 : count;
  }
}
