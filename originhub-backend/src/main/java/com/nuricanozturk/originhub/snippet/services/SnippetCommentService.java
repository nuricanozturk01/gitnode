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
package com.nuricanozturk.originhub.snippet.services;

import com.nuricanozturk.originhub.shared.errorhandling.exceptions.AccessNotAllowedException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.dtos.PageResponse;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import com.nuricanozturk.originhub.snippet.dtos.SnippetCommentForm;
import com.nuricanozturk.originhub.snippet.dtos.SnippetCommentInfo;
import com.nuricanozturk.originhub.snippet.entities.Snippet;
import com.nuricanozturk.originhub.snippet.entities.SnippetComment;
import com.nuricanozturk.originhub.snippet.entities.Visibility;
import com.nuricanozturk.originhub.snippet.mappers.SnippetMapper;
import com.nuricanozturk.originhub.snippet.repositories.SnippetCommentRepository;
import com.nuricanozturk.originhub.snippet.repositories.SnippetRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
@NullMarked
public class SnippetCommentService {

  private static final String ERR_SNIPPET_NOT_FOUND = "snippetNotFound";

  private final SnippetCommentRepository commentRepository;
  private final SnippetRepository snippetRepository;
  private final TenantRepository tenantRepository;
  private final SnippetMapper snippetMapper;

  public PageResponse<SnippetCommentInfo> listComments(
      final UUID snippetId, final @Nullable UUID callerId, final int page, final int size) {

    final var snippet =
        this.snippetRepository
            .findByIdWithOwner(snippetId)
            .orElseThrow(() -> new ItemNotFoundException(ERR_SNIPPET_NOT_FOUND));

    this.requireSnippetReadAccess(snippet, callerId);

    final var pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending());
    return PageResponse.from(
        this.commentRepository
            .findAllBySnippetIdOrderByCreatedAtAsc(snippetId, pageable)
            .map(this.snippetMapper::toCommentInfo));
  }

  @Transactional
  public SnippetCommentInfo addComment(
      final UUID tenantId, final UUID snippetId, final SnippetCommentForm form) {

    final var snippet =
        this.snippetRepository
            .findByIdWithOwner(snippetId)
            .orElseThrow(() -> new ItemNotFoundException(ERR_SNIPPET_NOT_FOUND));

    this.requireSnippetReadAccess(snippet, tenantId);

    final var author =
        this.tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new ItemNotFoundException("userNotFound"));

    final var comment = new SnippetComment();
    comment.setSnippet(snippet);
    comment.setAuthor(author);
    comment.setBody(form.getBody());

    final var saved = this.commentRepository.save(comment);
    this.snippetRepository.incrementCommentCount(snippetId);

    return this.snippetMapper.toCommentInfo(saved);
  }

  private void requireSnippetReadAccess(final Snippet snippet, final @Nullable UUID callerId) {
    if (snippet.getVisibility() == Visibility.PRIVATE
        && (callerId == null || !callerId.equals(snippet.getOwner().getId()))) {
      throw new ItemNotFoundException(ERR_SNIPPET_NOT_FOUND);
    }
  }

  @Transactional
  public void deleteComment(final UUID tenantId, final UUID snippetId, final UUID commentId) {

    final var snippet =
        this.snippetRepository
            .findByIdWithOwner(snippetId)
            .orElseThrow(() -> new ItemNotFoundException(ERR_SNIPPET_NOT_FOUND));

    final var comment =
        this.commentRepository
            .findById(commentId)
            .orElseThrow(() -> new ItemNotFoundException("commentNotFound"));

    final boolean isAuthor = tenantId.equals(comment.getAuthor().getId());
    final boolean isSnippetOwner = tenantId.equals(snippet.getOwner().getId());

    if (!isAuthor && !isSnippetOwner) {
      throw new AccessNotAllowedException("notCommentAuthorOrSnippetOwner");
    }

    this.commentRepository.delete(comment);
    this.snippetRepository.decrementCommentCount(snippetId);
  }
}
