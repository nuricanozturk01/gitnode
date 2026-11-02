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

package dev.gitnode.os.filecomment.services;

import dev.gitnode.os.events.filecomment.FileCommentedEvent;
import dev.gitnode.os.filecomment.dtos.FileCommentForm;
import dev.gitnode.os.filecomment.dtos.FileCommentInfo;
import dev.gitnode.os.filecomment.entities.FileComment;
import dev.gitnode.os.filecomment.repositories.FileCommentRepository;
import dev.gitnode.os.shared.commit.dtos.AuthorInfo;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.git.provider.GitProvider;
import dev.gitnode.os.shared.repo.entities.Repo;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comments on files, anchored to the commit the file was viewed at. A comment on an older commit of
 * the same path stays visible but is flagged outdated, because its line number may have moved.
 */
@Service
@RequiredArgsConstructor
@NullMarked
public class FileCommentService {

  static final int MAX_COMMENTS = 300;
  static final int MAX_LINE_COMMENT_FILE_BYTES = 1024 * 1024;

  private final FileCommentRepository comments;
  private final RepoRepository repos;
  private final TenantRepository tenants;
  private final GitProvider gitProvider;
  private final ApplicationEventPublisher events;

  @Transactional(readOnly = true)
  public FileCommentInfo.Page list(
      final String owner,
      final String repoName,
      final String branch,
      final String path,
      final @Nullable UUID viewerId)
      throws IOException {
    final var repo = this.findRepo(owner, repoName);
    this.assertValidPath(path);
    final var head = this.headSha(owner, repoName, branch);
    final var viewer = viewerId == null ? null : this.tenants.findById(viewerId).orElse(null);
    final var infos =
        this.comments.findForPath(repo.getId(), path, PageRequest.of(0, MAX_COMMENTS)).stream()
            .map(comment -> this.toInfo(comment, head, repo, viewer))
            .toList();
    return new FileCommentInfo.Page(head, infos);
  }

  @Transactional
  public FileCommentInfo create(
      final String owner, final String repoName, final UUID authorId, final FileCommentForm form)
      throws IOException {
    final var repo = this.findRepo(owner, repoName);
    this.assertValidPath(form.path());
    final var author =
        this.tenants
            .findById(authorId)
            .orElseThrow(() -> new ItemNotFoundException("userNotExist"));
    if (!author.isEnabled()) {
      throw new AccessNotAllowedException("userDisabled");
    }
    final var comment = new FileComment();
    final var head = this.headSha(owner, repoName, form.branch());
    final @Nullable FileComment parent;
    if (form.parentId() == null) {
      this.assertFileExists(owner, repoName, head, form.path(), form.line());
      parent = null;
      comment.setCommitSha(head);
      comment.setPath(form.path());
      comment.setLine(form.line());
    } else {
      parent =
          this.comments
              .findInRepo(form.parentId(), repo.getId())
              .orElseThrow(() -> new BadRequestException("Comment to reply to not found"));
      if (parent.getParentId() != null) {
        throw new BadRequestException("Replies cannot be replied to");
      }
      comment.setParentId(parent.getId());
      comment.setCommitSha(parent.getCommitSha());
      comment.setPath(parent.getPath());
      comment.setLine(parent.getLine());
    }
    comment.setRepoId(repo.getId());
    comment.setBody(form.body().strip());
    comment.setAuthor(author);
    comment.setCreatedAt(Instant.now());
    final var saved = this.comments.save(comment);
    this.notifyParticipants(repo, saved, parent, authorId, form.branch());
    return this.toInfo(saved, head, repo, author);
  }

  private void notifyParticipants(
      final Repo repo,
      final FileComment saved,
      final @Nullable FileComment parent,
      final UUID authorId,
      final String branch) {
    final Set<UUID> recipients = new HashSet<>();
    recipients.add(repo.getOwner().getId());
    if (parent != null) {
      recipients.addAll(this.comments.findThreadParticipantIds(parent.getId()));
    }
    recipients.remove(authorId);
    if (recipients.isEmpty()) {
      return;
    }
    this.events.publishEvent(
        new FileCommentedEvent(
            saved.getId(),
            repo.getId(),
            authorId,
            repo.getOwner().getUsername(),
            repo.getName(),
            branch.strip(),
            saved.getPath(),
            parent != null,
            Set.copyOf(recipients)));
  }

  @Transactional
  public FileCommentInfo update(
      final String owner,
      final String repoName,
      final UUID commentId,
      final UUID editorId,
      final String body) {
    final var repo = this.findRepo(owner, repoName);
    final var comment = this.find(commentId, repo);
    if (!comment.getAuthor().getId().equals(editorId)) {
      throw new AccessNotAllowedException("notAuthorized");
    }
    comment.setBody(body.strip());
    comment.setUpdatedAt(Instant.now());
    return this.toInfo(
        this.comments.save(comment), comment.getCommitSha(), repo, comment.getAuthor());
  }

  @Transactional
  public void delete(
      final String owner, final String repoName, final UUID commentId, final UUID requesterId) {
    final var repo = this.findRepo(owner, repoName);
    final var comment = this.find(commentId, repo);
    if (!this.canDelete(comment, repo, requesterId)) {
      throw new AccessNotAllowedException("notAuthorized");
    }
    this.comments.delete(comment);
  }

  private FileComment find(final UUID commentId, final Repo repo) {
    return this.comments
        .findInRepo(commentId, repo.getId())
        .orElseThrow(() -> new ItemNotFoundException("File comment not found"));
  }

  private Repo findRepo(final String owner, final String repoName) {
    return this.repos
        .findByOwnerUsernameAndName(owner, repoName)
        .orElseThrow(() -> new ItemNotFoundException("Repository not found"));
  }

  private boolean canDelete(final FileComment comment, final Repo repo, final @Nullable UUID user) {
    return user != null
        && (comment.getAuthor().getId().equals(user) || repo.getOwner().getId().equals(user));
  }

  private FileCommentInfo toInfo(
      final FileComment comment,
      final String headSha,
      final Repo repo,
      final @Nullable Tenant viewer) {
    final var author = comment.getAuthor();
    final var viewerId = viewer == null ? null : viewer.getId();
    return new FileCommentInfo(
        comment.getId(),
        comment.getPath(),
        comment.getLine(),
        comment.getParentId(),
        comment.getBody(),
        comment.getCommitSha(),
        !comment.getCommitSha().equals(headSha),
        new AuthorInfo(
            author.getDisplayName() != null ? author.getDisplayName() : author.getUsername(),
            author.getEmail(),
            author.getUsername(),
            author.getAvatarUrl() == null ? "" : author.getAvatarUrl()),
        viewerId != null && author.getId().equals(viewerId),
        this.canDelete(comment, repo, viewerId),
        comment.getCreatedAt(),
        comment.getUpdatedAt());
  }

  private String headSha(final String owner, final String repoName, final String branch)
      throws IOException {
    try (final var git = this.gitProvider.open(owner, repoName)) {
      final var id = git.resolve(branch.strip() + "^{commit}");
      if (id == null) {
        throw new ItemNotFoundException("Branch or commit not found");
      }
      return id.name();
    }
  }

  private void assertFileExists(
      final String owner,
      final String repoName,
      final String sha,
      final String path,
      final @Nullable Integer line)
      throws IOException {
    try (final var git = this.gitProvider.open(owner, repoName);
        final var walk = new RevWalk(git)) {
      final var commit = walk.parseCommit(ObjectId.fromString(sha));
      try (final var tree = TreeWalk.forPath(git, path, commit.getTree())) {
        if (tree == null) {
          throw new ItemNotFoundException("File not found at this commit");
        }
        if (FileMode.TREE.equals(tree.getFileMode(0))) {
          throw new BadRequestException("Comments can only be added to files");
        }
        if (line != null) {
          this.assertLineExists(git, tree.getObjectId(0), line);
        }
      }
    }
  }

  private void assertLineExists(final Repository git, final ObjectId blob, final int line)
      throws IOException {
    final var loader = git.open(blob);
    if (loader.getSize() > MAX_LINE_COMMENT_FILE_BYTES) {
      throw new BadRequestException("File is too large for line comments");
    }
    final var bytes = loader.getBytes();
    if (RawText.isBinary(bytes)) {
      throw new BadRequestException("Binary files cannot have line comments");
    }
    if (line > new RawText(bytes).size()) {
      throw new BadRequestException("Line does not exist in this file");
    }
  }

  private static boolean isDotSegment(final String segment) {
    return segment.isEmpty() || segment.equals(".") || segment.equals("..");
  }

  private void assertValidPath(final String path) {
    if (path.isBlank() || path.startsWith("/") || path.contains("\\") || path.contains("\0")) {
      throw new BadRequestException("Invalid file path");
    }
    for (final var segment : path.split("/", -1)) {
      if (isDotSegment(segment)) {
        throw new BadRequestException("Invalid file path");
      }
    }
  }
}
