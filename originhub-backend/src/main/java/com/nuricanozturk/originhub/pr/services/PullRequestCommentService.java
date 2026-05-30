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
import com.nuricanozturk.originhub.pr.entities.PullRequest;
import com.nuricanozturk.originhub.pr.entities.PullRequestComment;
import com.nuricanozturk.originhub.pr.mappers.PrMapper;
import com.nuricanozturk.originhub.pr.repositories.PrCommentRepository;
import com.nuricanozturk.originhub.pr.repositories.PrRepository;
import com.nuricanozturk.originhub.shared.commit.dtos.AuthorInfo;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class PullRequestCommentService {

  private final @NonNull PrMapper prMapper;
  private final @NonNull PrRepository prRepository;
  private final @NonNull PrCommentRepository commentRepository;
  private final @NonNull RepoRepository repoRepository;
  private final @NonNull TenantRepository tenantRepository;

  @Transactional
  public @NonNull PrCommentInfo addComment(
      final @NonNull String owner,
      final @NonNull String repoName,
      final int number,
      final @NonNull UUID authorId,
      final @NonNull PrCommentForm form) {

    final var repo = this.findRepo(owner, repoName);
    final var pr = this.findPr(repo.getId(), number);
    final var author = this.findTenant(authorId);

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
  public @NonNull PrCommentInfo updateComment(
      final @NonNull UUID commentId,
      final @NonNull UUID requesterId,
      final @NonNull PrCommentUpdateForm form) {
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
  public void deleteComment(final @NonNull UUID commentId, final @NonNull UUID requesterId) {
    final var comment =
        this.commentRepository
            .findById(commentId)
            .orElseThrow(() -> new ItemNotFoundException("Comment not found"));

    if (!comment.getAuthor().getId().equals(requesterId)) {
      throw new AccessDeniedException("You can only delete your own comments");
    }

    this.commentRepository.delete(comment);
  }

  public @NonNull List<PrCommentInfo> getComments(
      final @NonNull String owner, final @NonNull String repoName, final int number) {

    final var repo = this.findRepo(owner, repoName);
    final var pr = this.findPr(repo.getId(), number);

    return this.commentRepository.findAllByPrIdOrderByCreatedAtAsc(pr.getId()).stream()
        .map(this::toCommentInfo)
        .toList();
  }

  private @NonNull PrCommentInfo toCommentInfo(final @NonNull PullRequestComment comment) {

    final var author = this.toAuthorInfo(comment.getAuthor());

    return this.prMapper.toCommentInfo(comment, author);
  }

  private @NonNull AuthorInfo toAuthorInfo(final @NonNull Tenant tenant) {
    return new AuthorInfo(
        tenant.getDisplayName(), tenant.getEmail(), tenant.getUsername(), tenant.getAvatarUrl());
  }

  private @NonNull Repo findRepo(final @NonNull String owner, final @NonNull String repoName) {
    return this.repoRepository
        .findByOwnerUsernameAndName(owner, repoName)
        .orElseThrow(() -> new ItemNotFoundException("Repository not found"));
  }

  private @NonNull PullRequest findPr(final @NonNull UUID repoId, final int number) {
    return this.prRepository
        .findByRepoIdAndNumber(repoId, number)
        .orElseThrow(() -> new ItemNotFoundException("Pull request not found: #" + number));
  }

  private @NonNull Tenant findTenant(final @NonNull UUID id) {
    return this.tenantRepository
        .findById(id)
        .orElseThrow(() -> new ItemNotFoundException("User not found"));
  }
}
