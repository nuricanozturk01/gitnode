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

import dev.gitnode.os.shared.branch.BranchProtectionService;
import dev.gitnode.os.shared.cache.RepoCacheInvalidator;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.git.TreeCommitEventPublisher;
import dev.gitnode.os.shared.git.provider.GitProvider;
import dev.gitnode.os.tree.dtos.CommitChangesRequest;
import dev.gitnode.os.tree.dtos.CommitChangesRequest.Change;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;

/** Commits several file additions, edits and deletions to a branch atomically. */
@Service
@RequiredArgsConstructor
@NullMarked
public class ChangeCommitService {

  private final GitProvider gitProvider;
  private final RepoCacheInvalidator cacheInvalidator;
  private final BranchProtectionService branchProtection;
  private final TreeCommitEventPublisher treeCommitEvents;

  public String commit(
      final String owner,
      final String repoName,
      final PersonIdent author,
      final String actorUsername,
      final CommitChangesRequest request)
      throws IOException {
    final var changes = this.validate(request.changes());
    final var branch = request.branch().strip();
    this.branchProtection.assertDirectWriteAllowed(owner, repoName, branch);

    try (final var git = this.gitProvider.open(owner, repoName);
        final var inserter = git.newObjectInserter();
        final var walk = new RevWalk(git)) {
      final var ref = git.exactRef(Constants.R_HEADS + branch);
      if (ref == null) {
        throw new ItemNotFoundException("branchNotFound: " + branch);
      }
      final var head = walk.parseCommit(ref.getObjectId());
      if (!head.getName().equalsIgnoreCase(request.expectedHeadSha())) {
        throw new BadRequestException(
            "The branch changed since you opened the editor. Reload to see the latest version.");
      }

      final var index = DirCache.newInCore();
      final var builder = index.builder();
      final var existing = this.copyUnchanged(git, head, changes, builder);
      this.assertNoPathCollisions(existing, changes);
      this.addChanged(inserter, builder, changes, existing);
      builder.finish();

      final var commit = new CommitBuilder();
      commit.setTreeId(index.writeTree(inserter));
      commit.setParentId(head.getId());
      commit.setAuthor(author);
      commit.setCommitter(author);
      commit.setEncoding(StandardCharsets.UTF_8);
      commit.setMessage(this.message(request));
      final ObjectId commitId = inserter.insert(commit);
      inserter.flush();

      this.moveBranch(git, branch, head, commitId);
      this.cacheInvalidator.evictBranchScoped(owner, repoName, branch);
      this.treeCommitEvents.publish(owner, repoName, branch, actorUsername, commitId.name());
      return commitId.name();
    }
  }

  /** Adds every file of {@code head} that is not touched by the request; returns all file modes. */
  private Map<String, FileMode> copyUnchanged(
      final Repository git,
      final RevCommit head,
      final Map<String, ChangeView> changes,
      final DirCacheBuilder builder)
      throws IOException {
    final var existing = new HashMap<String, FileMode>();
    try (final var treeWalk = new TreeWalk(git)) {
      treeWalk.addTree(head.getTree());
      treeWalk.setRecursive(true);
      while (treeWalk.next()) {
        existing.put(treeWalk.getPathString(), treeWalk.getFileMode(0));
        if (changes.containsKey(treeWalk.getPathString())) {
          continue;
        }
        final var entry = new DirCacheEntry(treeWalk.getPathString());
        entry.setFileMode(treeWalk.getFileMode(0));
        entry.setObjectId(treeWalk.getObjectId(0));
        builder.add(entry);
      }
    }
    return existing;
  }

  private void addChanged(
      final ObjectInserter inserter,
      final DirCacheBuilder builder,
      final Map<String, ChangeView> changes,
      final Map<String, FileMode> existing)
      throws IOException {
    for (final var change : changes.values()) {
      if (change.delete()) {
        continue;
      }
      final var entry = new DirCacheEntry(change.path());
      final var mode = existing.get(change.path());
      entry.setFileMode(mode != null ? mode : FileMode.REGULAR_FILE);
      entry.setObjectId(
          inserter.insert(Constants.OBJ_BLOB, change.content().getBytes(StandardCharsets.UTF_8)));
      builder.add(entry);
    }
  }

  private void moveBranch(
      final Repository git, final String branch, final RevCommit head, final ObjectId commitId)
      throws IOException {
    final var update = git.updateRef(Constants.R_HEADS + branch);
    update.setNewObjectId(commitId);
    update.setExpectedOldObjectId(head.getId());
    if (update.update() != RefUpdate.Result.FAST_FORWARD) {
      throw new BadRequestException(
          "Could not update the branch; it may have changed. Reload and try again.");
    }
  }

  private Map<String, ChangeView> validate(final List<Change> changes) {
    final var result = new HashMap<String, ChangeView>();
    for (final var change : changes) {
      final var path = change.path();
      this.assertValidPath(path);
      if (!change.delete() && change.content() == null) {
        throw new BadRequestException("Content is required for " + path);
      }
      if (result.put(path, new ChangeView(path, change.content(), change.delete())) != null) {
        throw new BadRequestException("Duplicate change for " + path);
      }
    }
    return result;
  }

  private void assertNoPathCollisions(
      final Map<String, FileMode> existing, final Map<String, ChangeView> changes) {
    final Set<String> files = new HashSet<>(existing.keySet());
    changes.values().stream().filter(ChangeView::delete).forEach(c -> files.remove(c.path()));
    for (final var change : changes.values()) {
      if (change.delete()) {
        if (!existing.containsKey(change.path())) {
          throw new BadRequestException("Cannot delete missing file " + change.path());
        }
      } else {
        this.assertFitsTree(files, change.path());
      }
    }
    final var created = changes.values().stream().filter(c -> !c.delete()).map(ChangeView::path);
    created.forEach(
        path ->
            changes.values().stream()
                .filter(other -> !other.delete() && !other.path().equals(path))
                .filter(other -> other.path().startsWith(path + "/"))
                .findAny()
                .ifPresent(
                    other -> {
                      throw new BadRequestException(path + " conflicts with " + other.path());
                    }));
  }

  private void assertFitsTree(final Set<String> files, final String path) {
    final var prefix = path + "/";
    if (files.stream().anyMatch(file -> file.startsWith(prefix))) {
      throw new BadRequestException(path + " is a directory");
    }
    for (var slash = path.indexOf('/'); slash > 0; slash = path.indexOf('/', slash + 1)) {
      final var parent = path.substring(0, slash);
      if (files.contains(parent)) {
        throw new BadRequestException(parent + " is a file, not a directory");
      }
    }
  }

  private String message(final CommitChangesRequest request) {
    final var summary = request.message().strip();
    final var body = request.description();
    final var full = body == null || body.isBlank() ? summary : summary + "\n\n" + body.strip();
    return full + "\n";
  }

  private void assertValidPath(final String path) {
    if (path.startsWith("/") || path.contains("\\") || path.contains("\0")) {
      throw new BadRequestException("Invalid file path");
    }
    for (final var segment : path.split("/", -1)) {
      if (invalidSegment(segment)) {
        throw new BadRequestException("Invalid file path");
      }
    }
  }

  private static boolean invalidSegment(final String segment) {
    return segment.isEmpty()
        || segment.equals(".")
        || segment.equals("..")
        || segment.equalsIgnoreCase(".git");
  }

  private record ChangeView(
      String path, @org.jspecify.annotations.Nullable String content, boolean delete) {}
}
