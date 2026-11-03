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

import dev.gitnode.os.commit.api.AuthorInfo;
import dev.gitnode.os.events.pr.PullRequestCommentedEvent;
import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.pr.dtos.PrCommentForm;
import dev.gitnode.os.pr.dtos.PrCommentInfo;
import dev.gitnode.os.pr.dtos.PrCommentUpdateForm;
import dev.gitnode.os.pr.entities.PullRequestComment;
import dev.gitnode.os.pr.mappers.PrMapper;
import dev.gitnode.os.pr.repositories.PrCommentRepository;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
@NullMarked
public class PullRequestCommentService {

  private final PrMapper prMapper;
  private final dev.gitnode.os.repo.api.CollaboratorAccessPort access;
  private final PrFinder prFinder;
  private final PrCommentRepository commentRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public PrCommentInfo addComment(
      final String owner,
      final String repoName,
      final int number,
      final UUID authorId,
      final PrCommentForm form) {

    final var repo = this.prFinder.findRepo(owner, repoName);
    final var pr = this.prFinder.findPr(repo.id(), number);
    final var author = this.prFinder.findTenant(authorId);

    final var comment = new PullRequestComment();
    comment.setPr(pr);
    comment.setAuthorId(author.id());
    comment.setBody(form.getBody());
    if (form.getParentId() == null) {
      comment.setFilePath(form.getFilePath());
      comment.setCommitSha(form.getCommitSha());
      comment.setLineNumber(form.getLineNumber());
      comment.setLineSide(form.getLineSide());
    } else {
      final var parent =
          this.commentRepository
              .findByIdAndPrId(form.getParentId(), pr.getId())
              .orElseThrow(() -> new BadRequestException("Comment to reply to not found"));
      if (parent.getParentId() != null || parent.getFilePath() == null) {
        throw new BadRequestException("Only line conversations can be replied to");
      }
      comment.setParentId(parent.getId());
      comment.setFilePath(parent.getFilePath());
      comment.setCommitSha(parent.getCommitSha());
      comment.setLineNumber(parent.getLineNumber());
      comment.setLineSide(parent.getLineSide());
    }

    final var saved = this.commentRepository.save(comment);

    final Set<UUID> participants =
        new HashSet<>(this.commentRepository.findDistinctCommenterIdsByPrId(pr.getId()));
    participants.add(pr.getAuthorId());
    participants.remove(authorId);

    this.eventPublisher.publishEvent(
        new PullRequestCommentedEvent(
            saved.getId(),
            pr.getId(),
            pr.getNumber(),
            repo.id(),
            authorId,
            pr.getAuthorId(),
            owner,
            repoName,
            Set.copyOf(participants)));
    return this.toCommentInfo(saved);
  }

  @Transactional
  public PrCommentInfo updateComment(
      final String owner,
      final String repoName,
      final int number,
      final UUID commentId,
      final UUID requesterId,
      final PrCommentUpdateForm form) {
    final var comment = this.findComment(owner, repoName, number, commentId);

    if (!comment.getAuthorId().equals(requesterId)) {
      throw new AccessNotAllowedException("notAuthorized");
    }

    comment.setBody(form.getBody());
    return this.toCommentInfo(this.commentRepository.save(comment));
  }

  /**
   * The PR author, the comment author, the repository owner and merge-permitted users may resolve.
   */
  @Transactional
  public PrCommentInfo setResolved(
      final String owner,
      final String repoName,
      final int number,
      final UUID commentId,
      final UUID requesterId,
      final boolean resolved) {
    final var comment = this.findComment(owner, repoName, number, commentId);
    if (comment.getFilePath() == null || comment.getParentId() != null) {
      throw new BadRequestException("Only line comments can be resolved");
    }
    final var repo = this.prFinder.findRepo(owner, repoName);
    final var allowed =
        requesterId.equals(comment.getAuthorId())
            || requesterId.equals(comment.getPr().getAuthorId())
            || requesterId.equals(repo.ownerId())
            || this.access.hasPermission(
                repo.id(),
                requesterId,
                dev.gitnode.os.repo.api.CollaboratorPermission.PULL_REQUEST_MERGE);
    if (!allowed) {
      throw new AccessNotAllowedException("notAuthorized");
    }
    comment.setResolved(resolved);
    return this.toCommentInfo(this.commentRepository.save(comment));
  }

  @Transactional
  public void deleteComment(
      final String owner,
      final String repoName,
      final int number,
      final UUID commentId,
      final UUID requesterId) {
    final var comment = this.findComment(owner, repoName, number, commentId);

    if (!comment.getAuthorId().equals(requesterId)) {
      throw new AccessNotAllowedException("notAuthorized");
    }

    this.commentRepository.delete(comment);
  }

  public Page<PrCommentInfo> getComments(
      final String owner, final String repoName, final int number, final int page, final int size) {

    final var repo = this.prFinder.findRepo(owner, repoName);
    final var pr = this.prFinder.findPr(repo.id(), number);
    final var pageable = PageRequest.of(page, size);

    final var comments =
        this.commentRepository.findAllByPrIdOrderByCreatedAtAsc(pr.getId(), pageable);
    final var authors =
        this.prFinder.findTenants(
            comments.stream().map(PullRequestComment::getAuthorId).collect(Collectors.toSet()));
    return comments.map(comment -> this.toCommentInfo(comment, authors));
  }

  /** A comment of the pull request #number of the repository; absent ones are "not found". */
  private PullRequestComment findComment(
      final String owner, final String repoName, final int number, final UUID commentId) {
    final var repo =
        this.prFinder
            .findRepoOrNull(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException("Comment not found"));
    final var pr =
        this.prFinder
            .findPrOrNull(repo.id(), number)
            .orElseThrow(() -> new ItemNotFoundException("Comment not found"));
    return this.commentRepository
        .findByIdAndPrId(commentId, pr.getId())
        .orElseThrow(() -> new ItemNotFoundException("Comment not found"));
  }

  private PrCommentInfo toCommentInfo(final PullRequestComment comment) {
    return this.toCommentInfo(comment, this.prFinder.findTenants(Set.of(comment.getAuthorId())));
  }

  private PrCommentInfo toCommentInfo(
      final PullRequestComment comment, final Map<UUID, TenantRef> authors) {
    final var author = authors.get(comment.getAuthorId());
    return this.prMapper.toCommentInfo(
        comment,
        author == null
            ? new AuthorInfo("ghost", "", "ghost", "")
            : this.prMapper.toAuthorInfo(author));
  }
}
