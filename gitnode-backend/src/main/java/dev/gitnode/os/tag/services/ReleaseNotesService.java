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

package dev.gitnode.os.tag.services;

import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.git.provider.GitProvider;
import dev.gitnode.os.tag.dtos.GeneratedReleaseNotes;
import java.io.IOException;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** Builds a "What's Changed" list from the commits between the previous tag and a release. */
@Service
@RequiredArgsConstructor
@NullMarked
public class ReleaseNotesService {

  private static final int SHORT_SHA_LENGTH = 7;

  static final int MAX_COMMITS = 200;

  private final GitProvider gitProvider;

  public GeneratedReleaseNotes generate(
      final String owner,
      final String repoName,
      final String tagName,
      final @Nullable String targetCommitish,
      final @Nullable String previousTag)
      throws IOException {
    try (final var git = this.gitProvider.open(owner, repoName);
        final var walk = new RevWalk(git)) {
      final var headId = this.resolveHead(git, tagName, targetCommitish);
      final var head = walk.parseCommit(headId);
      final var previous = this.resolvePrevious(git, walk, head, tagName, previousTag);

      final var notes = new ArrayList<String>();
      var truncated = false;
      try (final var log = new RevWalk(git)) {
        log.markStart(log.parseCommit(headId));
        if (previous != null) {
          log.markUninteresting(log.parseCommit(previous.commit()));
        }
        for (final RevCommit commit : log) {
          if (commit.getParentCount() > 1) {
            continue;
          }
          if (notes.size() >= MAX_COMMITS) {
            truncated = true;
            break;
          }
          notes.add(this.line(commit));
        }
      }
      return new GeneratedReleaseNotes(
          tagName,
          this.body(notes, previous == null ? null : previous.name(), tagName, truncated),
          previous == null ? null : previous.name(),
          notes.size(),
          truncated);
    }
  }

  private record Previous(String name, ObjectId commit) {}

  private ObjectId resolveHead(
      final Repository git, final String tagName, final @Nullable String targetCommitish)
      throws IOException {
    this.assertRefName(tagName);
    final var existing = git.resolve(Constants.R_TAGS + tagName + "^{commit}");
    if (existing != null) {
      return existing;
    }
    if (targetCommitish == null || targetCommitish.isBlank()) {
      throw new BadRequestException("Tag not found; provide a target branch or commit");
    }
    final var target = git.resolve(targetCommitish.strip() + "^{commit}");
    if (target == null) {
      throw new BadRequestException("Target branch or commit not found");
    }
    return target;
  }

  private @Nullable Previous resolvePrevious(
      final Repository git,
      final RevWalk walk,
      final RevCommit head,
      final String tagName,
      final @Nullable String explicit)
      throws IOException {
    if (explicit != null && !explicit.isBlank()) {
      return this.explicitPrevious(git, explicit.strip());
    }
    return this.newestAncestorTag(git, walk, head, tagName);
  }

  private Previous explicitPrevious(final Repository git, final String name) throws IOException {
    this.assertRefName(name);
    final var id = git.resolve(Constants.R_TAGS + name + "^{commit}");
    if (id == null) {
      throw new BadRequestException("Previous tag not found");
    }
    return new Previous(name, id);
  }

  private @Nullable Previous newestAncestorTag(
      final Repository git, final RevWalk walk, final RevCommit head, final String tagName)
      throws IOException {
    Previous best = null;
    var bestTime = Integer.MIN_VALUE;
    for (final var ref : git.getRefDatabase().getRefsByPrefix(Constants.R_TAGS)) {
      final var name = ref.getName().substring(Constants.R_TAGS.length());
      final var id = git.resolve(ref.getName() + "^{commit}");
      if (name.equals(tagName) || id == null) {
        continue;
      }
      final var commit = walk.parseCommit(id);
      if (walk.isMergedInto(commit, walk.parseCommit(head.getId()))
          && commit.getCommitTime() > bestTime) {
        best = new Previous(name, id);
        bestTime = commit.getCommitTime();
      }
    }
    return best;
  }

  private String line(final RevCommit commit) {
    final var subject = commit.getShortMessage().strip();
    final var author = commit.getAuthorIdent().getName();
    return "* "
        + subject
        + " (`"
        + commit.getName().substring(0, SHORT_SHA_LENGTH)
        + "`) by "
        + author;
  }

  private String body(
      final java.util.List<String> lines,
      final @Nullable String previous,
      final String tagName,
      final boolean truncated) {
    final var out = new StringBuilder("## What's Changed\n\n");
    if (lines.isEmpty()) {
      out.append("No changes since ").append(previous == null ? "the beginning" : previous);
      out.append(".\n");
    } else {
      lines.forEach(line -> out.append(line).append('\n'));
      if (truncated) {
        out.append("* ...and more (showing the latest ").append(MAX_COMMITS).append(" commits)\n");
      }
    }
    if (previous != null) {
      out.append("\n**Full Changelog**: `")
          .append(previous)
          .append("...")
          .append(tagName)
          .append("`\n");
    }
    return out.toString();
  }

  private void assertRefName(final String name) {
    if (!Repository.isValidRefName(Constants.R_TAGS + name)) {
      throw new BadRequestException("Invalid tag name");
    }
  }
}
