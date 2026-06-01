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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class TreeNonTxService {

  private record RawEntry(String name, String entryPath, boolean isTree, String sha, long size) {}

  private final GitProvider gitProvider;

  public TreeResponse getTree(
      final String owner, final String repoName, final String branch, final String path)
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

  public BlobResponse getBlob(
      final String owner, final String repoName, final String branch, final String filePath)
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
      final String owner, final String repoName, final String branch, final String filePath)
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
   * Ensures a branch or tag ref exists so a streaming ZIP response can return 404 before headers
   * are committed.
   */
  public void assertBranchExists(final String owner, final String repoName, final String branch)
      throws IOException {

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {
      if (this.resolveRef(gitRepo, branch) == null) {
        throw new ItemNotFoundException("refNotFound: " + branch);
      }
    }
  }

  /**
   * Writes a ZIP of the branch or tag tip tree to {@code out}. Does not buffer the whole archive in
   * memory. Call {@link #assertBranchExists} first so missing refs surface as 404.
   */
  public void writeBranchZip(
      final String owner, final String repoName, final String branch, final OutputStream out)
      throws IOException {

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {

      final var ref = this.resolveRef(gitRepo, branch);

      if (ref == null) {
        log.warn("Ref not found during zip write: {}", branch);
        return;
      }

      try (final var revWalk = new RevWalk(gitRepo)) {
        final var peeledRef = gitRepo.getRefDatabase().peel(ref);
        final var commitId =
            peeledRef.getPeeledObjectId() != null
                ? peeledRef.getPeeledObjectId()
                : ref.getObjectId();
        final var headCommit = revWalk.parseCommit(commitId);
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

  private @Nullable Ref resolveRef(final Repository gitRepo, final String name) throws IOException {
    final var branchRef = gitRepo.findRef(Constants.R_HEADS + name);
    if (branchRef != null) {
      return branchRef;
    }
    return gitRepo.findRef(Constants.R_TAGS + name);
  }

  private List<TreeEntry> listTree(
      final Repository gitRepo, final RevCommit headCommit, final String path) throws IOException {

    final var rawEntries = this.collectRawEntries(gitRepo, headCommit, path);

    if (rawEntries.isEmpty()) {
      return List.of();
    }

    final var paths = rawEntries.stream().map(RawEntry::entryPath).collect(Collectors.toSet());
    final var lastCommits = this.findLastCommitsForPaths(gitRepo, headCommit, paths);
    final var entries = this.buildTreeEntries(rawEntries, lastCommits);

    entries.sort(
        Comparator.comparing((TreeEntry e) -> e.type() == EntryType.TREE ? 0 : 1)
            .thenComparing(TreeEntry::name));
    return entries;
  }

  private List<RawEntry> collectRawEntries(
      final Repository gitRepo, final RevCommit headCommit, final String path) throws IOException {

    final var rawEntries = new ArrayList<RawEntry>();

    try (final var treeWalk = new TreeWalk(gitRepo)) {
      treeWalk.addTree(headCommit.getTree());
      treeWalk.setRecursive(false);

      if (!path.isEmpty()) {
        this.processForNonEmptyPath(treeWalk, path);
      }

      while (treeWalk.next()) {
        final var entryPath = treeWalk.getPathString();
        final var relativePath = this.resolveRelativePath(entryPath, path);

        if (relativePath == null || relativePath.contains("/")) {
          continue;
        }

        final var isTree = treeWalk.getFileMode(0) == FileMode.TREE;
        final var objectId = treeWalk.getObjectId(0);
        final long size = isTree ? 0L : gitRepo.open(objectId).getSize();
        rawEntries.add(
            new RawEntry(treeWalk.getNameString(), entryPath, isTree, objectId.getName(), size));
      }
    }

    return rawEntries;
  }

  private @Nullable String resolveRelativePath(final String entryPath, final String path) {
    if (path.isEmpty()) {
      return entryPath;
    }
    if (entryPath.equals(path)) {
      return null;
    }
    if (entryPath.startsWith(path + "/")) {
      return entryPath.substring(path.length() + 1);
    }
    return null;
  }

  private ArrayList<TreeEntry> buildTreeEntries(
      final List<RawEntry> rawEntries, final Map<String, RevCommit> lastCommits) {

    final var entries = new ArrayList<TreeEntry>(rawEntries.size());
    for (final var raw : rawEntries) {
      final var lc = lastCommits.get(raw.entryPath());
      entries.add(
          new TreeEntry(
              raw.name(),
              raw.entryPath(),
              raw.isTree() ? EntryType.TREE : EntryType.BLOB,
              raw.sha(),
              raw.size(),
              lc != null ? lc.getName() : null,
              lc != null ? lc.getShortMessage() : null,
              lc != null ? lc.getAuthorIdent().getWhenAsInstant() : null));
    }
    return entries;
  }

  private Map<String, RevCommit> findLastCommitsForPaths(
      final Repository gitRepo, final RevCommit startCommit, final Set<String> paths)
      throws IOException {

    final Map<String, RevCommit> result = HashMap.newHashMap(paths.size());
    if (paths.isEmpty()) return result;

    // Separate RevWalk per path: reset() does not clear SEEN flags on already-parsed commits,
    // so reusing a single walk causes markStart() to silently no-op for commits visited in a
    // prior iteration, returning a stale or null result for subsequent paths.
    for (final String p : paths) {
      try (final var walk = new RevWalk(gitRepo)) {
        walk.setTreeFilter(AndTreeFilter.create(PathFilter.create(p), TreeFilter.ANY_DIFF));
        walk.markStart(walk.parseCommit(startCommit.getId()));
        final var commit = walk.next();
        if (commit != null) {
          result.put(p, commit);
        }
      }
    }

    return result;
  }

  private void processForNonEmptyPath(final TreeWalk treeWalk, final String path)
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

  public BlobResponse updateFile(
      final String owner,
      final String repoName,
      final String branch,
      final String filePath,
      final byte[] newContent,
      final String commitMessage,
      final PersonIdent author)
      throws IOException {

    try (final var gitRepo = this.gitProvider.open(owner, repoName);
        final var inserter = gitRepo.newObjectInserter()) {

      final var branchRef = gitRepo.findRef(Constants.R_HEADS + branch);
      if (branchRef == null) {
        throw new ItemNotFoundException("branchNotFound: " + branch);
      }

      try (final var revWalk = new RevWalk(gitRepo)) {
        final var headCommit = revWalk.parseCommit(branchRef.getObjectId());

        final var newBlobId = inserter.insert(Constants.OBJ_BLOB, newContent);

        final var dirCache = DirCache.newInCore();
        final var builder = dirCache.builder();

        try (final var treeWalk = new TreeWalk(gitRepo)) {
          treeWalk.addTree(headCommit.getTree());
          treeWalk.setRecursive(true);
          while (treeWalk.next()) {
            final var entryPath = treeWalk.getPathString();
            if (entryPath.equals(filePath)) {
              continue;
            }
            final var entry = new DirCacheEntry(entryPath);
            entry.setFileMode(treeWalk.getFileMode(0));
            entry.setObjectId(treeWalk.getObjectId(0));
            builder.add(entry);
          }
        }

        final var updatedEntry = new DirCacheEntry(filePath);
        updatedEntry.setFileMode(FileMode.REGULAR_FILE);
        updatedEntry.setObjectId(newBlobId);
        builder.add(updatedEntry);
        builder.finish();

        final var newTreeId = dirCache.writeTree(inserter);

        final var commitBuilder = new CommitBuilder();
        commitBuilder.setTreeId(newTreeId);
        commitBuilder.setParentId(headCommit.getId());
        commitBuilder.setAuthor(author);
        commitBuilder.setCommitter(author);
        commitBuilder.setMessage(
            commitMessage.endsWith("\n") ? commitMessage : commitMessage + "\n");
        commitBuilder.setEncoding(StandardCharsets.UTF_8);

        final var newCommitId = inserter.insert(commitBuilder);
        inserter.flush();

        final var refUpdate = gitRepo.updateRef(Constants.R_HEADS + branch);
        refUpdate.setNewObjectId(newCommitId);
        refUpdate.setExpectedOldObjectId(headCommit.getId());
        refUpdate.update();

        final var fileName = Path.of(filePath).getFileName().toString();
        final var isBinary = this.isBinaryContent(newContent);

        return BlobResponse.builder()
            .path(filePath)
            .name(fileName)
            .sha(newBlobId.getName())
            .size(newContent.length)
            .content(Base64.getEncoder().encodeToString(newContent))
            .isBinary(isBinary)
            .language(detectLanguage(fileName))
            .lineCount(isBinary ? 0 : this.countLines(newContent))
            .build();
      }
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
