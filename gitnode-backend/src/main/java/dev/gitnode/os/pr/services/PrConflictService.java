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

import dev.gitnode.os.pr.dtos.PrConflictInfo;
import dev.gitnode.os.pr.dtos.PrConflictInfo.ConflictFile;
import dev.gitnode.os.pr.dtos.PrConflictResolveForm;
import dev.gitnode.os.pr.dtos.PrConflictResolveForm.Resolution;
import dev.gitnode.os.pr.entities.PullRequest;
import dev.gitnode.os.shared.branch.BranchProtectionService;
import dev.gitnode.os.shared.cache.RepoCacheInvalidator;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.git.TreeCommitEventPublisher;
import dev.gitnode.os.shared.git.provider.GitProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeFormatter;
import org.eclipse.jgit.merge.MergeResult;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detects pull request merge conflicts and resolves text conflicts from the web UI the way GitHub
 * does: the target branch is merged into the source branch with the user's resolution.
 */
@Service
@RequiredArgsConstructor
@NullMarked
public class PrConflictService {

  static final int MAX_CONFLICT_BYTES = 512 * 1024;
  private static final Pattern MARKER = Pattern.compile("^(<{7}|>{7})( .*)?$", Pattern.MULTILINE);

  private final PrFinder prFinder;
  private final GitProvider gitProvider;
  private final BranchProtectionService branchProtection;
  private final TreeCommitEventPublisher commitEvents;
  private final RepoCacheInvalidator cacheInvalidator;

  /** Exposes the in-core index that {@link ResolveMerger} leaves behind on conflicts. */
  static final class ConflictMerger extends ResolveMerger {
    ConflictMerger(final Repository repo) {
      super(repo, true);
    }

    DirCache index() {
      return this.dircache;
    }
  }

  public PrConflictInfo inspect(final String owner, final String repoName, final int number)
      throws IOException {
    final var repo = this.prFinder.findRepo(owner, repoName);
    final var pr = this.prFinder.findPr(repo.getId(), number);
    if (!"OPEN".equals(pr.getStatus())) {
      throw new BadRequestException("Pull request is not open");
    }
    try (final var git = this.gitProvider.open(owner, repoName);
        final var walk = new RevWalk(git)) {
      final var heads = this.heads(git, walk, pr);
      final var merger = new ConflictMerger(git);
      if (merger.merge(heads.source(), heads.target())) {
        return new PrConflictInfo(true, heads.source().name(), heads.target().name(), List.of());
      }
      return new PrConflictInfo(
          false, heads.source().name(), heads.target().name(), this.describe(merger, pr));
    }
  }

  @Transactional
  public String resolve(
      final String owner,
      final String repoName,
      final int number,
      final UUID actorId,
      final PrConflictResolveForm form)
      throws IOException {
    final var repo = this.prFinder.findRepoForUpdate(owner, repoName);
    final var pr = this.prFinder.findPr(repo.getId(), number);
    if (!"OPEN".equals(pr.getStatus())) {
      throw new BadRequestException("Pull request is not open");
    }
    this.branchProtection.assertDirectWriteAllowed(owner, repoName, pr.getSourceBranch());
    final var actor = this.prFinder.findTenant(actorId);
    try (final var git = this.gitProvider.open(owner, repoName);
        final var walk = new RevWalk(git)) {
      final var heads = this.heads(git, walk, pr);
      final var merger = this.conflictedMerger(heads, form, git);
      final var files = this.describe(merger, pr);
      this.assertAllResolvable(files);
      final Map<String, String> resolved = this.indexResolutions(files, form.files());

      try (final var inserter = git.newObjectInserter()) {
        final var index = this.resolvedIndex(merger, resolved, inserter);
        final var treeId = index.writeTree(inserter);
        final var ident =
            new PersonIdent(
                actor.getDisplayName() != null ? actor.getDisplayName() : actor.getUsername(),
                actor.getEmail(),
                Instant.now(),
                ZoneOffset.UTC);
        final var commit = new CommitBuilder();
        commit.setTreeId(treeId);
        commit.setParentIds(heads.source(), heads.target());
        commit.setAuthor(ident);
        commit.setCommitter(ident);
        commit.setMessage(this.message(form.message(), pr));
        final var commitId = inserter.insert(commit);
        inserter.flush();

        final var update = git.updateRef(Constants.R_HEADS + pr.getSourceBranch());
        update.setNewObjectId(commitId);
        update.setExpectedOldObjectId(heads.source());
        update.setRefLogMessage("merge: resolve conflicts with " + pr.getTargetBranch(), false);
        final var result = update.update(walk);
        if (result != RefUpdate.Result.FAST_FORWARD) {
          throw new BadRequestException("Source branch changed; reload and resolve again");
        }
        this.cacheInvalidator.evictBranchScoped(owner, repoName, pr.getSourceBranch());
        this.commitEvents.publish(
            owner, repoName, pr.getSourceBranch(), actor.getUsername(), commitId.name());
        return commitId.name();
      }
    }
  }

  private ConflictMerger conflictedMerger(
      final Heads heads, final PrConflictResolveForm form, final Repository git)
      throws IOException {
    if (!heads.source().name().equals(form.sourceSha())
        || !heads.target().name().equals(form.targetSha())) {
      throw new BadRequestException("Pull request branches changed; reload and resolve again");
    }
    final var merger = new ConflictMerger(git);
    if (merger.merge(heads.source(), heads.target())) {
      throw new BadRequestException("Pull request has no conflicts");
    }
    return merger;
  }

  private void assertAllResolvable(final List<ConflictFile> files) {
    if (files.stream().anyMatch(f -> !f.resolvable())) {
      throw new BadRequestException(
          "Some conflicts cannot be resolved in the browser; resolve them locally");
    }
  }

  private record Heads(RevCommit source, RevCommit target) {}

  private Heads heads(final Repository git, final RevWalk walk, final PullRequest pr)
      throws IOException {
    final var source = git.exactRef(Constants.R_HEADS + pr.getSourceBranch());
    final var target = git.exactRef(Constants.R_HEADS + pr.getTargetBranch());
    if (source == null || target == null) {
      throw new BadRequestException("Pull request branch not found");
    }
    return new Heads(
        walk.parseCommit(Objects.requireNonNull(source.getObjectId())),
        walk.parseCommit(Objects.requireNonNull(target.getObjectId())));
  }

  @SuppressWarnings("unchecked")
  private List<ConflictFile> describe(final ConflictMerger merger, final PullRequest pr)
      throws IOException {
    final var results = merger.getMergeResults();
    final var files = new ArrayList<ConflictFile>();
    final var names = List.of("base", pr.getSourceBranch(), pr.getTargetBranch());
    final var bothSides = this.pathsPresentOnBothSides(merger);
    for (final var path : merger.getUnmergedPaths()) {
      final var result = results.get(path);
      if (result == null
          || !bothSides.contains(path)
          || !(result.getSequences().get(0) instanceof RawText)) {
        files.add(
            new ConflictFile(
                path,
                false,
                "Binary file, deletion, or rename conflict; resolve it locally",
                null));
        continue;
      }
      final var out = new ByteArrayOutputStream();
      new MergeFormatter()
          .formatMerge(out, (MergeResult<RawText>) result, names, StandardCharsets.UTF_8);
      if (out.size() > MAX_CONFLICT_BYTES) {
        files.add(
            new ConflictFile(path, false, "File is too large to resolve in the browser", null));
      } else {
        files.add(new ConflictFile(path, true, null, out.toString(StandardCharsets.UTF_8)));
      }
    }
    files.sort((a, b) -> a.path().compareTo(b.path()));
    return files;
  }

  /**
   * A path deleted on one side is a delete/modify conflict; only both-sides-present text is
   * web-resolvable.
   */
  private Set<String> pathsPresentOnBothSides(final ConflictMerger merger) {
    final var ours = new HashSet<String>();
    final var theirs = new HashSet<String>();
    final var index = merger.index();
    for (var i = 0; i < index.getEntryCount(); i++) {
      final var entry = index.getEntry(i);
      if (entry.getStage() == DirCacheEntry.STAGE_2) {
        ours.add(entry.getPathString());
      } else if (entry.getStage() == DirCacheEntry.STAGE_3) {
        theirs.add(entry.getPathString());
      }
    }
    ours.retainAll(theirs);
    return ours;
  }

  private Map<String, String> indexResolutions(
      final List<ConflictFile> files, final List<Resolution> submitted) {
    final var expected = new HashSet<String>();
    files.forEach(f -> expected.add(f.path()));
    final var resolved = new HashMap<String, String>();
    for (final var item : submitted) {
      if (!expected.contains(item.path()) || resolved.put(item.path(), item.content()) != null) {
        throw new BadRequestException("Unexpected or duplicate file: " + item.path());
      }
      if (MARKER.matcher(item.content()).find()) {
        throw new BadRequestException("Conflict markers remain in " + item.path());
      }
    }
    final Set<String> missing = new HashSet<>(expected);
    missing.removeAll(resolved.keySet());
    if (!missing.isEmpty()) {
      throw new BadRequestException("Resolve every conflicting file: " + missing.iterator().next());
    }
    return resolved;
  }

  private DirCache resolvedIndex(
      final ConflictMerger merger,
      final Map<String, String> resolved,
      final org.eclipse.jgit.lib.ObjectInserter inserter)
      throws IOException {
    final var source = merger.index();
    final var index = DirCache.newInCore();
    final var builder = index.builder();
    final var modes = new HashMap<String, FileMode>();
    for (var i = 0; i < source.getEntryCount(); i++) {
      final var entry = source.getEntry(i);
      final var path = entry.getPathString();
      if (entry.getStage() == DirCacheEntry.STAGE_0) {
        builder.add(entry);
      } else if (resolved.containsKey(path)) {
        modes.merge(path, entry.getFileMode(), (a, b) -> b);
      }
    }
    for (final var item : resolved.entrySet()) {
      final var entry = new DirCacheEntry(item.getKey());
      entry.setFileMode(modes.getOrDefault(item.getKey(), FileMode.REGULAR_FILE));
      entry.setObjectId(
          inserter.insert(Constants.OBJ_BLOB, item.getValue().getBytes(StandardCharsets.UTF_8)));
      builder.add(entry);
    }
    builder.finish();
    return index;
  }

  private String message(final @Nullable String custom, final PullRequest pr) {
    if (custom != null && !custom.isBlank()) {
      return custom.strip();
    }
    return "Merge branch '" + pr.getTargetBranch() + "' into " + pr.getSourceBranch();
  }
}
