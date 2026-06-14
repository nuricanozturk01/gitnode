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

import dev.gitnode.os.events.pr.PullRequestCommentedEvent;
import dev.gitnode.os.pr.dtos.PrCommentForm;
import dev.gitnode.os.pr.dtos.PrCommentInfo;
import dev.gitnode.os.pr.dtos.PrCommentUpdateForm;
import dev.gitnode.os.pr.entities.PullRequestComment;
import dev.gitnode.os.pr.mappers.PrMapper;
import dev.gitnode.os.pr.repositories.PrCommentRepository;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
@NullMarked
public class PullRequestCommentService {

  private final PrMapper prMapper;
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
    final var pr = this.prFinder.findPr(repo.getId(), number);
    final var author = this.prFinder.findTenant(authorId);

    final var comment = new PullRequestComment();
    comment.setPr(pr);
    comment.setAuthor(author);
    comment.setBody(form.getBody());
    comment.setFilePath(form.getFilePath());
    comment.setCommitSha(form.getCommitSha());
    comment.setLineNumber(form.getLineNumber());
    comment.setLineSide(form.getLineSide());

    final var saved = this.commentRepository.save(comment);

    final Set<UUID> participants =
        new HashSet<>(this.commentRepository.findDistinctCommenterIdsByPrId(pr.getId()));
    participants.add(pr.getAuthor().getId());
    participants.remove(authorId);

    this.eventPublisher.publishEvent(
        new PullRequestCommentedEvent(
            saved.getId(),
            pr.getId(),
            pr.getNumber(),
            repo.getId(),
            authorId,
            pr.getAuthor().getId(),
            owner,
            repoName,
            Set.copyOf(participants)));
    return this.toCommentInfo(saved);
  }

  @Transactional
  public PrCommentInfo updateComment(
      final UUID commentId, final UUID requesterId, final PrCommentUpdateForm form) {
    final var comment =
        this.commentRepository
            .findById(commentId)
            .orElseThrow(() -> new ItemNotFoundException("Comment not found"));

    if (!comment.getAuthor().getId().equals(requesterId)) {
      throw new AccessDeniedException("You can only edit your own comments");
    }

    comment.setBody(form.getBody());
    return this.toCommentInfo(this.commentRepository.save(comment));
  }

  @Transactional
  public void deleteComment(final UUID commentId, final UUID requesterId) {
    final var comment =
        this.commentRepository
            .findById(commentId)
            .orElseThrow(() -> new ItemNotFoundException("Comment not found"));

    if (!comment.getAuthor().getId().equals(requesterId)) {
      throw new AccessDeniedException("You can only delete your own comments");
    }

    this.commentRepository.delete(comment);
  }

  public Page<PrCommentInfo> getComments(
      final String owner, final String repoName, final int number, final int page, final int size) {

    final var repo = this.prFinder.findRepo(owner, repoName);
    final var pr = this.prFinder.findPr(repo.getId(), number);
    final var pageable = PageRequest.of(page, size);

    return this.commentRepository
        .findAllByPrIdOrderByCreatedAtAsc(pr.getId(), pageable)
        .map(this::toCommentInfo);
  }

  private PrCommentInfo toCommentInfo(final PullRequestComment comment) {
    return this.prMapper.toCommentInfo(comment, this.prMapper.toAuthorInfo(comment.getAuthor()));
  }
}
