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
package com.nuricanozturk.originhub.shared.util;

import com.nuricanozturk.originhub.shared.commit.dtos.DiffHunk;
import com.nuricanozturk.originhub.shared.commit.dtos.DiffLine;
import com.nuricanozturk.originhub.shared.commit.dtos.FileDiff;
import com.nuricanozturk.originhub.shared.commit.dtos.HunkResult;
import com.nuricanozturk.originhub.shared.commit.dtos.LineType;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@UtilityClass
public class FileDiffParser {

  private static final long MAX_FILE_SIZE = 512 * 1024;
  private static final int MAX_LINES_PER_FILE = 500;

  public static @NonNull FileDiff parseFileDiff(
      final @NonNull Repository gitRepo,
      final @NonNull DiffFormatter formatter,
      final @NonNull DiffEntry entry) {

    try {
      if (isOversized(gitRepo, entry.getOldId()) || isOversized(gitRepo, entry.getNewId())) {
        return new FileDiff(
            entry.getOldPath(), entry.getNewPath(), entry.getChangeType(), 0, 0, List.of(), true);
      }

      final var oldRaw = loadRawText(gitRepo, entry.getOldId());
      final var newRaw = loadRawText(gitRepo, entry.getNewId());
      final var editList = formatter.toFileHeader(entry).toEditList();

      return buildFileDiff(entry, oldRaw, newRaw, editList);

    } catch (final IOException e) {
      throw new ErrorOccurredException("Failed to parse diff: " + e.getMessage());
    }
  }

  private static @NonNull FileDiff buildFileDiff(
      final @NonNull DiffEntry entry,
      final @NonNull RawText oldRaw,
      final @NonNull RawText newRaw,
      final @NonNull List<Edit> editList) {

    final var hunks = new ArrayList<DiffHunk>();
    int additions = 0;
    int deletions = 0;
    int totalLines = 0;
    boolean isTruncated = false;

    for (final var edit : editList) {
      if (totalLines >= MAX_LINES_PER_FILE) {
        isTruncated = true;
        break;
      }

      final var result = buildHunk(edit, oldRaw, newRaw, totalLines);
      totalLines += result.lineCount();
      additions += result.additions();
      deletions += result.deletions();
      isTruncated |= result.truncated();

      if (!result.lines().isEmpty()) {
        hunks.add(toHunk(edit, result.lines()));
      }

      if (isTruncated) break;
    }

    return new FileDiff(
        entry.getOldPath(),
        entry.getNewPath(),
        entry.getChangeType(),
        additions,
        deletions,
        hunks,
        isTruncated);
  }

  private static @NonNull HunkResult buildHunk(
      final @NonNull Edit edit,
      final @NonNull RawText oldRaw,
      final @NonNull RawText newRaw,
      final int totalLinesBefore) {

    final var lines = new ArrayList<DiffLine>();
    int additions = 0;
    int deletions = 0;
    int totalLines = totalLinesBefore;
    boolean truncated = false;

    int oldLine = edit.getBeginA() + 1;
    for (int i = edit.getBeginA(); i < edit.getEndA(); i++) {
      if (totalLines >= MAX_LINES_PER_FILE) {
        truncated = true;
        break;
      }
      lines.add(new DiffLine(LineType.DELETE, oldLine++, 0, oldRaw.getString(i)));
      deletions++;
      totalLines++;
    }

    int newLine = edit.getBeginB() + 1;
    for (int i = edit.getBeginB(); i < edit.getEndB(); i++) {
      if (totalLines >= MAX_LINES_PER_FILE) {
        truncated = true;
        break;
      }
      lines.add(new DiffLine(LineType.ADD, 0, newLine++, newRaw.getString(i)));
      additions++;
      totalLines++;
    }

    return new HunkResult(lines, additions, deletions, totalLines - totalLinesBefore, truncated);
  }

  private static @NonNull DiffHunk toHunk(
      final @NonNull Edit edit, final @NonNull List<DiffLine> lines) {

    return new DiffHunk(
        edit.getBeginA() + 1,
        edit.getEndA() - edit.getBeginA(),
        edit.getBeginB() + 1,
        edit.getEndB() - edit.getBeginB(),
        String.format(
            "@@ -%d,%d +%d,%d @@",
            edit.getBeginA() + 1,
            edit.getEndA() - edit.getBeginA(),
            edit.getBeginB() + 1,
            edit.getEndB() - edit.getBeginB()),
        lines);
  }

  private static boolean isOversized(
      final @NonNull Repository repo, final @Nullable AbbreviatedObjectId id) {

    if (id == null || id.name().startsWith("0000000")) {
      return false;
    }

    try {
      final var fullId = repo.resolve(id.name());

      if (fullId == null) {
        return false;
      }

      try (final var reader = repo.newObjectReader()) {
        return reader.open(fullId).getSize() > MAX_FILE_SIZE;
      }
    } catch (final IOException e) {
      return true;
    }
  }

  private static @NonNull RawText loadRawText(
      final @NonNull Repository repo, final @Nullable AbbreviatedObjectId id) throws IOException {

    if (id == null || id.name().startsWith("0000000")) {
      return new RawText(new byte[0]);
    }

    final var fullId = repo.resolve(id.name());

    if (fullId == null) {
      return new RawText(new byte[0]);
    }

    try (final var reader = repo.newObjectReader()) {
      final var loader = reader.open(fullId);

      if (loader.getSize() > MAX_FILE_SIZE) {
        return new RawText(new byte[0]);
      }

      return new RawText(loader.getBytes());
    }
  }

  public static @NonNull AbstractTreeIterator prepareTreeParser(
      final @NonNull Repository repo, final @NonNull ObjectId commitId) throws IOException {

    try (final var walk = new RevWalk(repo)) {

      final var commit = walk.parseCommit(commitId);
      final var tree = walk.parseTree(commit.getTree().getId());
      final var treeParser = new CanonicalTreeParser();

      try (final var reader = repo.newObjectReader()) {
        treeParser.reset(reader, tree.getId());
      }

      return treeParser;
    }
  }
}
