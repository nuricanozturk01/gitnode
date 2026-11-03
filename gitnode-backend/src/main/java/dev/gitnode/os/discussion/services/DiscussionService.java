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
package dev.gitnode.os.discussion.services;

import dev.gitnode.os.commit.api.AuthorInfo;
import dev.gitnode.os.discussion.dtos.DiscussionCommentForm;
import dev.gitnode.os.discussion.dtos.DiscussionCommentInfo;
import dev.gitnode.os.discussion.dtos.DiscussionCommentUpdateForm;
import dev.gitnode.os.discussion.dtos.DiscussionDetail;
import dev.gitnode.os.discussion.dtos.DiscussionForm;
import dev.gitnode.os.discussion.dtos.DiscussionInfo;
import dev.gitnode.os.discussion.dtos.DiscussionUpdateForm;
import dev.gitnode.os.discussion.dtos.DiscussionVoteInfo;
import dev.gitnode.os.discussion.entities.Discussion;
import dev.gitnode.os.discussion.entities.DiscussionCategory;
import dev.gitnode.os.discussion.entities.DiscussionComment;
import dev.gitnode.os.discussion.entities.DiscussionVote;
import dev.gitnode.os.discussion.repositories.DiscussionCommentRepository;
import dev.gitnode.os.discussion.repositories.DiscussionRepository;
import dev.gitnode.os.discussion.repositories.DiscussionVoteRepository;
import dev.gitnode.os.discussion.repositories.IdCount;
import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.repo.api.CollaboratorAccessPort;
import dev.gitnode.os.repo.api.CollaboratorPermission;
import dev.gitnode.os.repo.api.RepoCommandPort;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.repo.dtos.PageResponse;
import dev.gitnode.os.shared.audit.annotations.Audited;
import dev.gitnode.os.shared.cache.CacheNames;
import dev.gitnode.os.shared.cache.CachePatternEvictor;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@NullMarked
public class DiscussionService {

  private static final int PAGE_SIZE = 15;
  private static final int COMMENT_PAGE_SIZE = 20;
  private static final String NOT_FOUND = "Discussion #%d not found.";

  private final DiscussionRepository discussions;
  private final DiscussionCommentRepository comments;
  private final DiscussionVoteRepository votes;
  private final RepoLookupPort repoLookup;
  private final RepoCommandPort repoCommands;
  private final TenantQueryPort tenants;
  private final CollaboratorAccessPort access;
  private final CachePatternEvictor cacheEvictor;

  @Audited(
      action = "CREATE_DISCUSSION",
      entityType = "DISCUSSION",
      entityIdSpEL = "#result.info().number().toString()",
      detailsSpEL = "'repo=' + #owner + '/' + #repoName + ', title=' + #form.title")
  @Transactional
  public DiscussionDetail create(
      final String owner, final String repoName, final UUID authorId, final DiscussionForm form) {
    final var repo = this.findRepo(owner, repoName);
    this.assertMayUseCategory(repo, authorId, form.category());
    if (!this.repoCommands.lockForUpdate(repo.id())) {
      throw new ItemNotFoundException("Repo not found.");
    }
    final var discussion = new Discussion();
    discussion.setRepoId(repo.id());
    discussion.setNumber(this.discussions.findMaxNumberByRepoId(repo.id()) + 1);
    discussion.setTitle(form.title().strip());
    discussion.setBody(blankToNull(form.body()));
    discussion.setCategory(form.category().name());
    discussion.setAuthorId(authorId);
    final var saved = this.discussions.save(discussion);
    this.evictLists(owner, repoName);
    return this.toDetail(repo, saved, authorId);
  }

  @Cacheable(
      cacheNames = CacheNames.DISCUSSION_LIST,
      key =
          "#owner + ':' + #repoName + ':' + #category + ':' + #unansweredOnly + ':' + #sort + ':'"
              + " + #page")
  public PageResponse<DiscussionInfo> list(
      final String owner,
      final String repoName,
      final @Nullable DiscussionCategory category,
      final boolean unansweredOnly,
      final String sort,
      final int page) {
    final var repo = this.findRepo(owner, repoName);
    final var order =
        Sort.by(Sort.Direction.DESC, "updated".equals(sort) ? "updatedAt" : "createdAt");
    final var found =
        this.discussions.search(
            repo.id(),
            category != null,
            category == null ? "" : category.name(),
            unansweredOnly,
            PageRequest.of(Math.max(page, 0), PAGE_SIZE, order));
    return PageResponse.from(found.map(new InfoMapper(found.getContent())::toInfo));
  }

  public DiscussionDetail get(
      final String owner, final String repoName, final int number, final @Nullable UUID viewer) {
    final var repo = this.findRepo(owner, repoName);
    return this.toDetail(repo, this.find(repo, number), viewer);
  }

  @Transactional
  public DiscussionDetail update(
      final String owner,
      final String repoName,
      final int number,
      final UUID requesterId,
      final DiscussionUpdateForm form) {
    final var repo = this.findRepo(owner, repoName);
    final var discussion = this.find(repo, number);
    this.assertAuthorOrModerator(repo, discussion.getAuthorId(), requesterId);
    if (form.title() != null) {
      discussion.setTitle(form.title().strip());
    }
    if (form.body() != null) {
      discussion.setBody(blankToNull(form.body()));
    }
    if (form.category() != null && !form.category().name().equals(discussion.getCategory())) {
      this.assertMayUseCategory(repo, requesterId, form.category());
      discussion.setCategory(form.category().name());
      if (form.category() != DiscussionCategory.Q_AND_A) {
        discussion.setAnswerCommentId(null);
      }
    }
    final var saved = this.discussions.save(discussion);
    this.evictLists(owner, repoName);
    return this.toDetail(repo, saved, requesterId);
  }

  @Audited(
      action = "DELETE_DISCUSSION",
      entityType = "DISCUSSION",
      detailsSpEL = "'repo=' + #owner + '/' + #repoName + ', number=' + #number")
  @Transactional
  public void delete(
      final String owner, final String repoName, final int number, final UUID requesterId) {
    final var repo = this.findRepo(owner, repoName);
    final var discussion = this.find(repo, number);
    this.assertAuthorOrModerator(repo, discussion.getAuthorId(), requesterId);
    this.discussions.delete(discussion);
    this.evictLists(owner, repoName);
    this.evictComments(owner, repoName, number);
  }

  @Transactional
  public DiscussionDetail setLocked(
      final String owner,
      final String repoName,
      final int number,
      final UUID requesterId,
      final boolean locked) {
    final var repo = this.findRepo(owner, repoName);
    this.assertModerator(repo, requesterId);
    final var discussion = this.find(repo, number);
    discussion.setLocked(locked);
    return this.toDetail(repo, this.discussions.save(discussion), requesterId);
  }

  @Transactional
  public DiscussionVoteInfo setUpvote(
      final String owner,
      final String repoName,
      final int number,
      final UUID requesterId,
      final boolean upvoted) {
    final var repo = this.findRepo(owner, repoName);
    final var discussion = this.find(repo, number);
    final var id = discussion.getId();
    if (upvoted && !this.votes.existsByDiscussionIdAndTenantId(id, requesterId)) {
      this.votes.save(new DiscussionVote(id, requesterId));
    } else if (!upvoted) {
      this.votes.deleteByDiscussionIdAndTenantId(id, requesterId);
    }
    return new DiscussionVoteInfo((int) this.votes.countByDiscussionId(id), upvoted);
  }

  /** Marks a comment as the answer of a Q&A discussion, or clears the answer when null. */
  @Transactional
  public DiscussionDetail setAnswer(
      final String owner,
      final String repoName,
      final int number,
      final UUID requesterId,
      final @Nullable UUID commentId) {
    final var repo = this.findRepo(owner, repoName);
    final var discussion = this.find(repo, number);
    this.assertAuthorOrModerator(repo, discussion.getAuthorId(), requesterId);
    if (!DiscussionCategory.Q_AND_A.name().equals(discussion.getCategory())) {
      throw new BadRequestException("Only questions can have an answer");
    }
    if (commentId != null) {
      this.findComment(discussion, commentId);
    }
    discussion.setAnswerCommentId(commentId);
    final var saved = this.discussions.save(discussion);
    this.evictLists(owner, repoName);
    this.evictComments(owner, repoName, number);
    return this.toDetail(repo, saved, requesterId);
  }

  /** One page of top-level comments; replies are fetched per comment with {@link #listReplies}. */
  @Cacheable(
      cacheNames = CacheNames.DISCUSSION_COMMENTS,
      key = "#owner + ':' + #repoName + ':' + #number + ':top:' + #page")
  public PageResponse<DiscussionCommentInfo> listComments(
      final String owner, final String repoName, final int number, final int page) {
    final var repo = this.findRepo(owner, repoName);
    final var discussion = this.find(repo, number);
    final var found =
        this.comments.findTopLevel(
            discussion.getId(), PageRequest.of(Math.max(page, 0), COMMENT_PAGE_SIZE));
    return PageResponse.from(this.toCommentPage(found, discussion));
  }

  @Cacheable(
      cacheNames = CacheNames.DISCUSSION_COMMENTS,
      key = "#owner + ':' + #repoName + ':' + #number + ':' + #commentId + ':' + #page")
  public PageResponse<DiscussionCommentInfo> listReplies(
      final String owner,
      final String repoName,
      final int number,
      final UUID commentId,
      final int page) {
    final var repo = this.findRepo(owner, repoName);
    final var discussion = this.find(repo, number);
    this.findComment(discussion, commentId);
    final var found =
        this.comments.findReplies(commentId, PageRequest.of(Math.max(page, 0), COMMENT_PAGE_SIZE));
    return PageResponse.from(this.toCommentPage(found, discussion));
  }

  @Transactional
  public DiscussionCommentInfo addComment(
      final String owner,
      final String repoName,
      final int number,
      final UUID authorId,
      final DiscussionCommentForm form) {
    final var repo = this.findRepo(owner, repoName);
    final var discussion = this.find(repo, number);
    if (discussion.isLocked() && !this.isModerator(repo, authorId)) {
      throw new BadRequestException("This discussion is locked");
    }
    if (form.parentId() != null) {
      final var parent =
          this.comments
              .findByIdAndDiscussionId(form.parentId(), discussion.getId())
              .orElseThrow(() -> new ItemNotFoundException("Comment not found"));
      if (parent.getParentId() != null) {
        throw new BadRequestException("Replies cannot be nested further");
      }
    }
    final var comment = new DiscussionComment();
    comment.setDiscussionId(discussion.getId());
    comment.setParentId(form.parentId());
    comment.setAuthorId(authorId);
    comment.setBody(form.body().strip());
    final var saved = this.comments.save(comment);
    this.touch(discussion);
    this.evictComments(owner, repoName, number);
    return this.toCommentInfo(saved, discussion, this.authors(List.of(authorId)), Map.of());
  }

  @Transactional
  public DiscussionCommentInfo updateComment(
      final String owner,
      final String repoName,
      final int number,
      final UUID commentId,
      final UUID requesterId,
      final DiscussionCommentUpdateForm form) {
    final var repo = this.findRepo(owner, repoName);
    final var discussion = this.find(repo, number);
    final var comment = this.findComment(discussion, commentId);
    this.assertAuthorOrModerator(repo, comment.getAuthorId(), requesterId);
    comment.setBody(form.body().strip());
    this.evictComments(owner, repoName, number);
    final var replies = this.comments.countRepliesByParentIds(List.of(commentId));
    return this.toCommentInfo(
        this.comments.save(comment),
        discussion,
        this.authors(List.of(comment.getAuthorId())),
        counts(replies));
  }

  @Transactional
  public void deleteComment(
      final String owner,
      final String repoName,
      final int number,
      final UUID commentId,
      final UUID requesterId) {
    final var repo = this.findRepo(owner, repoName);
    final var discussion = this.find(repo, number);
    final var comment = this.findComment(discussion, commentId);
    this.assertAuthorOrModerator(repo, comment.getAuthorId(), requesterId);
    this.comments.delete(comment);
    this.evictComments(owner, repoName, number);
  }

  /**
   * Lists carry titles, categories and answer state, so structural changes drop them. Counts and
   * votes change often and are allowed to lag by the short cache lifetime instead.
   */
  private void evictLists(final String owner, final String repoName) {
    this.afterCommit(
        () -> this.cacheEvictor.evict(CacheNames.DISCUSSION_LIST, owner + ":" + repoName + ":*"));
  }

  private void evictComments(final String owner, final String repoName, final int number) {
    this.afterCommit(
        () ->
            this.cacheEvictor.evict(
                CacheNames.DISCUSSION_COMMENTS, owner + ":" + repoName + ":" + number + ":*"));
  }

  /** Evicts once the data is committed, so a concurrent read cannot re-cache the old state. */
  private void afterCommit(final Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            action.run();
          }
        });
  }

  private void touch(final Discussion discussion) {
    discussion.setUpdatedAt(Instant.now());
    this.discussions.save(discussion);
  }

  private RepoRef findRepo(final String owner, final String repoName) {
    return this.repoLookup
        .find(owner, repoName)
        .orElseThrow(() -> new ItemNotFoundException("Repo not found."));
  }

  private Discussion find(final RepoRef repo, final int number) {
    return this.discussions
        .findByRepoIdAndNumber(repo.id(), number)
        .orElseThrow(() -> new ItemNotFoundException(NOT_FOUND.formatted(number)));
  }

  private DiscussionComment findComment(final Discussion discussion, final UUID commentId) {
    return this.comments
        .findByIdAndDiscussionId(commentId, discussion.getId())
        .orElseThrow(() -> new ItemNotFoundException("Comment not found"));
  }

  private boolean isModerator(final RepoRef repo, final UUID userId) {
    return repo.ownerId().equals(userId)
        || this.access.hasPermission(repo.id(), userId, CollaboratorPermission.ISSUE_MANAGE);
  }

  private void assertModerator(final RepoRef repo, final UUID userId) {
    if (!this.isModerator(repo, userId)) {
      throw new AccessNotAllowedException("notAuthorized");
    }
  }

  private void assertAuthorOrModerator(
      final RepoRef repo, final UUID authorId, final UUID requesterId) {
    if (!requesterId.equals(authorId)) {
      this.assertModerator(repo, requesterId);
    }
  }

  /** Only maintainers may post announcements. */
  private void assertMayUseCategory(
      final RepoRef repo, final UUID userId, final DiscussionCategory category) {
    if (category == DiscussionCategory.ANNOUNCEMENTS) {
      this.assertModerator(repo, userId);
    }
  }

  private Map<UUID, TenantRef> authors(final Collection<UUID> ids) {
    return ids.isEmpty() ? Map.of() : this.tenants.findAllByIds(new HashSet<>(ids));
  }

  private static Map<UUID, Integer> counts(final List<IdCount> rows) {
    return rows.stream()
        .collect(Collectors.toMap(IdCount::getId, row -> (int) row.getCount(), Integer::sum));
  }

  private static AuthorInfo author(final Map<UUID, TenantRef> authors, final UUID id) {
    final var tenant = authors.get(id);
    return tenant == null
        ? new AuthorInfo("ghost", "", "ghost", "")
        : new AuthorInfo(
            tenant.nameForDisplay(), tenant.email(), tenant.username(), tenant.avatarUrl());
  }

  /** Maps a page of discussions with three grouped queries instead of two per row. */
  private final class InfoMapper {
    private final Map<UUID, TenantRef> authors;
    private final Map<UUID, Integer> commentCounts;
    private final Map<UUID, Integer> voteCounts;

    InfoMapper(final List<Discussion> page) {
      final var ids = page.stream().map(Discussion::getId).toList();
      this.authors =
          DiscussionService.this.authors(page.stream().map(Discussion::getAuthorId).toList());
      this.commentCounts =
          ids.isEmpty()
              ? Map.of()
              : counts(DiscussionService.this.comments.countByDiscussionIds(ids));
      this.voteCounts =
          ids.isEmpty() ? Map.of() : counts(DiscussionService.this.votes.countByDiscussionIds(ids));
    }

    DiscussionInfo toInfo(final Discussion d) {
      return new DiscussionInfo(
          d.getNumber(),
          d.getTitle(),
          d.getCategory(),
          author(this.authors, d.getAuthorId()),
          this.commentCounts.getOrDefault(d.getId(), 0),
          this.voteCounts.getOrDefault(d.getId(), 0),
          d.getAnswerCommentId() != null,
          d.isLocked(),
          d.getCreatedAt(),
          d.getUpdatedAt());
    }
  }

  private DiscussionDetail toDetail(
      final RepoRef repo, final Discussion d, final @Nullable UUID viewer) {
    final var answer =
        d.getAnswerCommentId() == null
            ? null
            : this.comments
                .findByIdAndDiscussionId(d.getAnswerCommentId(), d.getId())
                .map(
                    comment ->
                        this.toCommentInfo(
                            comment,
                            d,
                            this.authors(List.of(comment.getAuthorId())),
                            counts(
                                this.comments.countRepliesByParentIds(List.of(comment.getId())))))
                .orElse(null);
    return new DiscussionDetail(
        new InfoMapper(List.of(d)).toInfo(d),
        d.getBody(),
        answer,
        viewer != null && this.votes.existsByDiscussionIdAndTenantId(d.getId(), viewer),
        viewer != null && this.isModerator(repo, viewer));
  }

  private org.springframework.data.domain.Page<DiscussionCommentInfo> toCommentPage(
      final org.springframework.data.domain.Page<DiscussionComment> found,
      final Discussion discussion) {
    final var authors = this.authors(found.stream().map(DiscussionComment::getAuthorId).toList());
    final var ids = found.stream().map(DiscussionComment::getId).toList();
    final var replies =
        ids.isEmpty()
            ? Map.<UUID, Integer>of()
            : counts(this.comments.countRepliesByParentIds(ids));
    return found.map(comment -> this.toCommentInfo(comment, discussion, authors, replies));
  }

  private DiscussionCommentInfo toCommentInfo(
      final DiscussionComment comment,
      final Discussion discussion,
      final Map<UUID, TenantRef> authors,
      final Map<UUID, Integer> replyCounts) {
    return new DiscussionCommentInfo(
        comment.getId(),
        comment.getParentId(),
        author(authors, comment.getAuthorId()),
        comment.getBody(),
        comment.getId().equals(discussion.getAnswerCommentId()),
        replyCounts.getOrDefault(comment.getId(), 0),
        comment.getCreatedAt(),
        comment.getUpdatedAt());
  }

  private static @Nullable String blankToNull(final @Nullable String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
