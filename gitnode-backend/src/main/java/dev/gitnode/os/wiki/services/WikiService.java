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

import dev.gitnode.os.repo.dtos.PageResponse;
import dev.gitnode.os.shared.cache.CacheNames;
import dev.gitnode.os.shared.cache.CachePatternEvictor;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.git.provider.GitProvider;
import dev.gitnode.os.wiki.dtos.WikiPage;
import dev.gitnode.os.wiki.dtos.WikiPageSummary;
import dev.gitnode.os.wiki.dtos.WikiRevision;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Reads and writes wiki pages. Not a database feature: every save is a commit on {@value #REF}, so
 * history, authorship and diffs come from Git. Pages are flat files named {@code <slug>.md}.
 */
@Service
@RequiredArgsConstructor
@NullMarked
public class WikiService {

  /** Branch holding the wiki; lives outside {@code refs/heads} so it never shows as a branch. */
  static final String REF = "refs/wiki/main";

  private static final String EXTENSION = ".md";
  private static final int LIST_PAGE_SIZE = 50;
  private static final int HISTORY_PAGE_SIZE = 20;
  private static final int MAX_HISTORY_PAGE = 50;
  private static final int MAX_PAGES = 2000;
  private static final int MAX_BYTES = 512 * 1024;
  private static final Pattern SLUG = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$");
  private static final Pattern SHA = Pattern.compile("^[0-9a-f]{40}$");

  private final GitProvider git;
  private final CachePatternEvictor cacheEvictor;

  /** Who a save is attributed to. */
  public record Editor(String name, String email) {}

  /** One page of page names, Home first. Only tree entries are read, never page contents. */
  @Cacheable(cacheNames = CacheNames.WIKI_PAGES, key = "#owner + ':' + #repoName + ':' + #page")
  public PageResponse<WikiPageSummary> list(
      final String owner, final String repoName, final int page) throws IOException {
    try (final var repo = this.git.open(owner, repoName)) {
      final var head = repo.exactRef(REF);
      if (head == null) {
        return new PageResponse<>(List.of(), 0, LIST_PAGE_SIZE, 0, 0);
      }
      final var pages = new ArrayList<WikiPageSummary>();
      try (final var walk = new RevWalk(repo);
          final var tree = new TreeWalk(repo)) {
        tree.addTree(walk.parseCommit(head.getObjectId()).getTree());
        while (tree.next()) {
          final var name = tree.getNameString();
          if (name.endsWith(EXTENSION) && !FileMode.TREE.equals(tree.getFileMode(0))) {
            final var slug = name.substring(0, name.length() - EXTENSION.length());
            pages.add(new WikiPageSummary(slug, titleOf(slug)));
          }
        }
      }
      pages.sort(
          Comparator.comparing((WikiPageSummary entry) -> !"Home".equals(entry.slug()))
              .thenComparing(entry -> entry.title().toLowerCase(Locale.ROOT)));
      final var from = (int) Math.min((long) Math.max(page, 0) * LIST_PAGE_SIZE, pages.size());
      final var to = Math.min(from + LIST_PAGE_SIZE, pages.size());
      return new PageResponse<>(
          List.copyOf(pages.subList(from, to)),
          Math.max(page, 0),
          LIST_PAGE_SIZE,
          pages.size(),
          (pages.size() + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE);
    }
  }

  /**
   * The page at {@code rev}, or at the latest wiki commit when {@code rev} is null. Only the latest
   * version is cached; saves and deletes evict it.
   */
  @Cacheable(
      cacheNames = CacheNames.WIKI_PAGE,
      key = "#owner + ':' + #repoName + ':' + #slug",
      condition = "#rev == null")
  public WikiPage get(
      final String owner, final String repoName, final String slug, final @Nullable String rev)
      throws IOException {
    final var path = path(slug);
    try (final var repo = this.git.open(owner, repoName);
        final var walk = new RevWalk(repo)) {
      final var start = this.resolve(repo, walk, rev);
      final var blob = blobAt(repo, start, path);
      if (blob == null) {
        throw new ItemNotFoundException("Wiki page not found");
      }
      final var last = lastCommit(repo, start, path);
      final var bytes = repo.open(blob).getCachedBytes(MAX_BYTES);
      return new WikiPage(
          slug,
          titleOf(slug),
          new String(bytes, StandardCharsets.UTF_8),
          blob.name(),
          last.getName(),
          last.getAuthorIdent().getName(),
          last.getAuthorIdent().getWhenAsInstant());
    }
  }

  /** One page of the edits of a page, newest first. */
  public PageResponse<WikiRevision> history(
      final String owner, final String repoName, final String slug, final int page)
      throws IOException {
    final var path = path(slug);
    final var skip = Math.min(Math.max(page, 0), MAX_HISTORY_PAGE) * HISTORY_PAGE_SIZE;
    try (final var repo = this.git.open(owner, repoName);
        final var walk = new RevWalk(repo)) {
      final var head = repo.exactRef(REF);
      if (head == null) {
        throw new ItemNotFoundException("Wiki page not found");
      }
      walk.setTreeFilter(AndTreeFilter.create(PathFilter.create(path), TreeFilter.ANY_DIFF));
      walk.markStart(walk.parseCommit(head.getObjectId()));
      final var slice = collectRevisions(walk, skip);
      if (slice.revisions().isEmpty() && skip == 0) {
        throw new ItemNotFoundException("Wiki page not found");
      }
      final var number = skip / HISTORY_PAGE_SIZE;
      final var total = skip + slice.revisions().size() + (slice.hasMore() ? 1 : 0);
      return new PageResponse<>(
          slice.revisions(),
          number,
          HISTORY_PAGE_SIZE,
          total,
          slice.hasMore() ? number + 2 : number + 1);
    }
  }

  private record RevisionSlice(List<WikiRevision> revisions, boolean hasMore) {}

  /** Walks past {@code skip} commits, then takes one page and notes whether more follow. */
  private static RevisionSlice collectRevisions(final RevWalk walk, final int skip) {
    final var revisions = new ArrayList<WikiRevision>();
    var seen = 0;
    for (final var commit : walk) {
      if (seen++ < skip) {
        continue;
      }
      if (revisions.size() >= HISTORY_PAGE_SIZE) {
        return new RevisionSlice(revisions, true);
      }
      revisions.add(
          new WikiRevision(
              commit.getName(),
              commit.getShortMessage(),
              commit.getAuthorIdent().getName(),
              commit.getAuthorIdent().getWhenAsInstant()));
    }
    return new RevisionSlice(revisions, false);
  }

  /** Creates or updates a page; fails if it changed since {@code baseSha}. */
  public WikiPage save(
      final String owner,
      final String repoName,
      final String slug,
      final String content,
      final @Nullable String message,
      final @Nullable String baseSha,
      final Editor editor)
      throws IOException {
    final var path = path(slug);
    final var bytes = content.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > MAX_BYTES) {
      throw new BadRequestException("Wiki page is too large");
    }
    try (final var repo = this.git.open(owner, repoName)) {
      final var head = repo.exactRef(REF);
      final var parent = head == null ? null : head.getObjectId();
      final var current = parent == null ? null : blobAtCommit(repo, parent, path);
      assertBase(current, baseSha);
      try (final var inserter = repo.newObjectInserter()) {
        final var blob = inserter.insert(Constants.OBJ_BLOB, bytes);
        if (blob.equals(current)) {
          return this.get(owner, repoName, slug, null);
        }
        final var tree = this.treeWith(repo, inserter, parent, path, blob);
        final var defaultMessage = (current == null ? "Create " : "Update ") + titleOf(slug);
        this.commit(repo, inserter, parent, tree, message, defaultMessage, editor);
      }
    }
    this.evict(owner, repoName, slug);
    return this.get(owner, repoName, slug, null);
  }

  public void delete(
      final String owner,
      final String repoName,
      final String slug,
      final @Nullable String message,
      final Editor editor)
      throws IOException {
    final var path = path(slug);
    try (final var repo = this.git.open(owner, repoName)) {
      final var head = repo.exactRef(REF);
      if (head == null || blobAtCommit(repo, head.getObjectId(), path) == null) {
        throw new ItemNotFoundException("Wiki page not found");
      }
      try (final var inserter = repo.newObjectInserter()) {
        final var tree = this.treeWith(repo, inserter, head.getObjectId(), path, null);
        this.commit(
            repo, inserter, head.getObjectId(), tree, message, "Delete " + titleOf(slug), editor);
      }
    }
    this.evict(owner, repoName, slug);
  }

  /** Drops the cached page and every cached page of the page list of this wiki. */
  private void evict(final String owner, final String repoName, final String slug) {
    this.cacheEvictor.evict(CacheNames.WIKI_PAGE, owner + ":" + repoName + ":" + slug);
    this.cacheEvictor.evict(CacheNames.WIKI_PAGES, owner + ":" + repoName + ":*");
  }

  /** Slug to repository path; the slug rules make traversal impossible. */
  static String path(final String slug) {
    if (!SLUG.matcher(slug).matches()) {
      throw new BadRequestException(
          "Page names use letters, digits, dots, dashes and underscores (max 100 characters)");
    }
    return slug + EXTENSION;
  }

  static String titleOf(final String slug) {
    return slug.replace('-', ' ').replace('_', ' ');
  }

  private static void assertBase(final @Nullable ObjectId current, final @Nullable String baseSha) {
    final var currentSha = current == null ? null : current.name();
    if (!Objects.equals(currentSha, baseSha)) {
      throw new BadRequestException(
          currentSha == null
              ? "This page was deleted by someone else; reload before saving"
              : "This page was changed by someone else; reload and merge your changes");
    }
  }

  private RevCommit resolve(final Repository repo, final RevWalk walk, final @Nullable String rev)
      throws IOException {
    final var head = repo.exactRef(REF);
    if (head == null) {
      throw new ItemNotFoundException("Wiki page not found");
    }
    final var headCommit = walk.parseCommit(head.getObjectId());
    if (rev == null) {
      return headCommit;
    }
    if (!SHA.matcher(rev).matches() || !repo.getObjectDatabase().has(ObjectId.fromString(rev))) {
      throw new ItemNotFoundException("Wiki revision not found");
    }
    final var commit = walk.parseCommit(ObjectId.fromString(rev));
    if (!walk.isMergedInto(commit, headCommit)) {
      throw new ItemNotFoundException("Wiki revision not found");
    }
    return commit;
  }

  private static @Nullable ObjectId blobAt(
      final Repository repo, final RevCommit commit, final String path) throws IOException {
    try (final var tree = TreeWalk.forPath(repo, path, commit.getTree())) {
      return tree == null || FileMode.TREE.equals(tree.getFileMode(0)) ? null : tree.getObjectId(0);
    }
  }

  private static @Nullable ObjectId blobAtCommit(
      final Repository repo, final ObjectId commitId, final String path) throws IOException {
    try (final var walk = new RevWalk(repo)) {
      return blobAt(repo, walk.parseCommit(commitId), path);
    }
  }

  private static RevCommit lastCommit(
      final Repository repo, final RevCommit start, final String path) throws IOException {
    try (final var walk = new RevWalk(repo)) {
      walk.setTreeFilter(AndTreeFilter.create(PathFilter.create(path), TreeFilter.ANY_DIFF));
      walk.setRevFilter(RevFilter.ALL);
      walk.markStart(walk.parseCommit(start.getId()));
      final var last = walk.next();
      return last == null ? start : last;
    }
  }

  /** The tree of {@code parent} with {@code path} set to {@code blob}, or removed when null. */
  private ObjectId treeWith(
      final Repository repo,
      final org.eclipse.jgit.lib.ObjectInserter inserter,
      final @Nullable ObjectId parent,
      final String path,
      final @Nullable ObjectId blob)
      throws IOException {
    final var cache = DirCache.newInCore();
    final var builder = cache.builder();
    if (parent != null) {
      try (final var walk = new RevWalk(repo);
          final var tree = new TreeWalk(repo)) {
        tree.addTree(walk.parseCommit(parent).getTree());
        while (tree.next()) {
          if (tree.getNameString().equals(path) || FileMode.TREE.equals(tree.getFileMode(0))) {
            continue;
          }
          builder.add(entry(tree.getPathString(), tree.getObjectId(0)));
        }
      }
    }
    if (blob != null) {
      builder.add(entry(path, blob));
    }
    builder.finish();
    if (cache.getEntryCount() > MAX_PAGES) {
      throw new BadRequestException("A wiki can hold at most " + MAX_PAGES + " pages");
    }
    return cache.writeTree(inserter);
  }

  private static DirCacheEntry entry(final String path, final ObjectId blob) {
    final var entry = new DirCacheEntry(path);
    entry.setFileMode(FileMode.REGULAR_FILE);
    entry.setObjectId(blob);
    return entry;
  }

  private void commit(
      final Repository repo,
      final org.eclipse.jgit.lib.ObjectInserter inserter,
      final @Nullable ObjectId parent,
      final ObjectId tree,
      final @Nullable String message,
      final String defaultMessage,
      final Editor editor)
      throws IOException {
    final var commit = new CommitBuilder();
    final var ident = new PersonIdent(editor.name(), editor.email());
    commit.setAuthor(ident);
    commit.setCommitter(ident);
    commit.setTreeId(tree);
    if (parent != null) {
      commit.setParentId(parent);
    }
    commit.setMessage(message == null || message.isBlank() ? defaultMessage : message.strip());
    final var id = inserter.insert(commit);
    inserter.flush();
    final var update = repo.updateRef(REF);
    update.setNewObjectId(id);
    update.setExpectedOldObjectId(parent == null ? ObjectId.zeroId() : parent);
    update.setRefLogMessage("wiki: " + defaultMessage, false);
    final var result = update.update();
    if (result != RefUpdate.Result.NEW && result != RefUpdate.Result.FAST_FORWARD) {
      throw new BadRequestException("The wiki changed while saving; reload and try again");
    }
  }
}
