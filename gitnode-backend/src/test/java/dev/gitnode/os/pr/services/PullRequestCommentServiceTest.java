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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.pr.dtos.PrCommentForm;
import dev.gitnode.os.pr.dtos.PrCommentInfo;
import dev.gitnode.os.pr.dtos.PrCommentUpdateForm;
import dev.gitnode.os.pr.entities.PullRequest;
import dev.gitnode.os.pr.entities.PullRequestComment;
import dev.gitnode.os.pr.mappers.PrMapper;
import dev.gitnode.os.pr.repositories.PrCommentRepository;
import dev.gitnode.os.shared.commit.dtos.AuthorInfo;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.repo.entities.Repo;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
@DisplayName("PullRequestCommentService unit tests")
class PullRequestCommentServiceTest {

  @Mock private PrMapper prMapper;
  @Mock private PrFinder prFinder;
  @Mock private PrCommentRepository commentRepository;

  @InjectMocks private PullRequestCommentService commentService;

  @Test
  @DisplayName("addComment saves comment for valid PR")
  void addComment_savesComment_whenValid() {
    UUID authorId = UUID.randomUUID();
    Repo repo = new Repo();
    repo.setId(UUID.randomUUID());
    PullRequest pr = new PullRequest();
    pr.setId(UUID.randomUUID());
    Tenant author = new Tenant();
    author.setId(authorId);
    when(prFinder.findRepo("alice", "demo")).thenReturn(repo);
    when(prFinder.findPr(repo.getId(), 1)).thenReturn(pr);
    when(prFinder.findTenant(authorId)).thenReturn(author);
    PullRequestComment saved = new PullRequestComment();
    saved.setId(UUID.randomUUID());
    saved.setBody("Looks good");
    saved.setAuthor(author);
    when(commentRepository.save(any(PullRequestComment.class))).thenReturn(saved);
    AuthorInfo authorInfo = new AuthorInfo("Alice", "a@test.com", "alice", null);
    when(prMapper.toAuthorInfo(author)).thenReturn(authorInfo);
    PrCommentInfo info = sampleCommentInfo(saved.getId(), authorInfo, "Looks good");
    when(prMapper.toCommentInfo(saved, authorInfo)).thenReturn(info);
    PrCommentForm form = new PrCommentForm("Looks good", null, null, null, null);

    PrCommentInfo result = commentService.addComment("alice", "demo", 1, authorId, form);

    assertThat(result.body()).isEqualTo("Looks good");
  }

  @Test
  @DisplayName("updateComment throws ItemNotFoundException when comment missing")
  void updateComment_throws_whenCommentMissing() {
    UUID commentId = UUID.randomUUID();
    when(commentRepository.findById(commentId)).thenReturn(Optional.empty());
    PrCommentUpdateForm form = new PrCommentUpdateForm("edited");

    assertThatThrownBy(() -> commentService.updateComment(commentId, UUID.randomUUID(), form))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("Comment not found");
  }

  @Test
  @DisplayName("updateComment throws AccessDeniedException when requester is not author")
  void updateComment_throws_whenNotAuthor() {
    UUID commentId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    UUID otherId = UUID.randomUUID();
    PullRequestComment comment = new PullRequestComment();
    Tenant author = new Tenant();
    author.setId(authorId);
    comment.setAuthor(author);
    when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
    PrCommentUpdateForm form = new PrCommentUpdateForm("edited");

    assertThatThrownBy(() -> commentService.updateComment(commentId, otherId, form))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("only edit your own");

    verify(commentRepository, never()).save(any());
  }

  @Test
  @DisplayName("deleteComment removes comment when requester is author")
  void deleteComment_deletes_whenAuthor() {
    UUID commentId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    PullRequestComment comment = new PullRequestComment();
    Tenant author = new Tenant();
    author.setId(authorId);
    comment.setAuthor(author);
    when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

    commentService.deleteComment(commentId, authorId);

    verify(commentRepository).delete(comment);
  }

  @Test
  @DisplayName("deleteComment throws AccessDeniedException when requester is not author")
  void deleteComment_throws_whenNotAuthor() {
    UUID commentId = UUID.randomUUID();
    PullRequestComment comment = new PullRequestComment();
    Tenant author = new Tenant();
    author.setId(UUID.randomUUID());
    comment.setAuthor(author);
    when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

    assertThatThrownBy(() -> commentService.deleteComment(commentId, UUID.randomUUID()))
        .isInstanceOf(AccessDeniedException.class);
  }

  private static PrCommentInfo sampleCommentInfo(UUID id, AuthorInfo author, String body) {
    Instant now = Instant.now();
    return new PrCommentInfo(id, author, body, null, null, null, null, false, now, now);
  }
}
