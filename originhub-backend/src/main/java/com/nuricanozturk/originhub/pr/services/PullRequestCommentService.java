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
package com.nuricanozturk.originhub.pr.services;

import com.nuricanozturk.originhub.pr.dtos.PrCommentForm;
import com.nuricanozturk.originhub.pr.dtos.PrCommentInfo;
import com.nuricanozturk.originhub.pr.dtos.PrCommentUpdateForm;
import com.nuricanozturk.originhub.pr.entities.PullRequestComment;
import com.nuricanozturk.originhub.pr.mappers.PrMapper;
import com.nuricanozturk.originhub.pr.repositories.PrCommentRepository;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
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

    return this.toCommentInfo(this.commentRepository.save(comment));
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
