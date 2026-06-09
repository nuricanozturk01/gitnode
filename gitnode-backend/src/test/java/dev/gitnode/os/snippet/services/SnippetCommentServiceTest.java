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
package dev.gitnode.os.snippet.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.repo.dtos.PageResponse;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import dev.gitnode.os.snippet.dtos.SnippetCommentForm;
import dev.gitnode.os.snippet.dtos.SnippetCommentInfo;
import dev.gitnode.os.snippet.dtos.SnippetOwnerInfo;
import dev.gitnode.os.snippet.entities.Snippet;
import dev.gitnode.os.snippet.entities.SnippetComment;
import dev.gitnode.os.snippet.entities.Visibility;
import dev.gitnode.os.snippet.mappers.SnippetMapper;
import dev.gitnode.os.snippet.repositories.SnippetCommentRepository;
import dev.gitnode.os.snippet.repositories.SnippetRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("SnippetCommentService unit tests")
class SnippetCommentServiceTest {

  @Mock private SnippetCommentRepository commentRepository;
  @Mock private SnippetRepository snippetRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private SnippetMapper snippetMapper;

  @InjectMocks private SnippetCommentService commentService;

  // ──────────────────────────── helpers ────────────────────────────

  private Tenant tenant(UUID id, String username) {
    Tenant t = new Tenant();
    t.setId(id);
    t.setUsername(username);
    return t;
  }

  private Snippet publicSnippet(UUID id, Tenant owner) {
    Snippet s = new Snippet();
    s.setId(id);
    s.setOwner(owner);
    s.setVisibility(Visibility.PUBLIC);
    s.setFiles(new ArrayList<>());
    return s;
  }

  private Snippet privateSnippet(UUID id, Tenant owner) {
    Snippet s = publicSnippet(id, owner);
    s.setVisibility(Visibility.PRIVATE);
    return s;
  }

  private SnippetComment comment(UUID id, Tenant author, Snippet snippet, String body) {
    SnippetComment c = new SnippetComment();
    c.setId(id);
    c.setAuthor(author);
    c.setSnippet(snippet);
    c.setBody(body);
    return c;
  }

  private SnippetCommentInfo commentInfo(UUID id, String body) {
    return SnippetCommentInfo.builder()
        .id(id)
        .body(body)
        .author(
            SnippetOwnerInfo.builder()
                .id(UUID.randomUUID())
                .username("user")
                .email("user@example.com")
                .build())
        .build();
  }

  // ──────────────────────────── listComments ────────────────────────────

  @Nested
  @DisplayName("listComments()")
  class ListComments {

    @Test
    @DisplayName("throws ItemNotFoundException when snippet not found")
    void throws_whenSnippetMissing() {
      UUID snippetId = UUID.randomUUID();
      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> commentService.listComments(snippetId, null, 0, 10))
          .isInstanceOf(ItemNotFoundException.class)
          .hasMessageContaining("snippetNotFound");
    }

    @Test
    @DisplayName("throws ItemNotFoundException for private snippet with anonymous caller")
    void throws_forPrivateSnippet_anonymousCaller() {
      UUID snippetId = UUID.randomUUID();
      Tenant owner = tenant(UUID.randomUUID(), "owner");
      Snippet snippet = privateSnippet(snippetId, owner);

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));

      assertThatThrownBy(() -> commentService.listComments(snippetId, null, 0, 10))
          .isInstanceOf(ItemNotFoundException.class)
          .hasMessageContaining("snippetNotFound");
    }

    @Test
    @DisplayName("throws ItemNotFoundException for private snippet when caller is not owner")
    void throws_forPrivateSnippet_differentCaller() {
      UUID snippetId = UUID.randomUUID();
      Tenant owner = tenant(UUID.randomUUID(), "owner");
      Snippet snippet = privateSnippet(snippetId, owner);

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));

      assertThatThrownBy(() -> commentService.listComments(snippetId, UUID.randomUUID(), 0, 10))
          .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    @DisplayName("returns paged comments for public snippet")
    void returnsPaged_forPublicSnippet() {
      UUID snippetId = UUID.randomUUID();
      Tenant owner = tenant(UUID.randomUUID(), "owner");
      Snippet snippet = publicSnippet(snippetId, owner);

      SnippetComment c1 = comment(UUID.randomUUID(), owner, snippet, "Nice!");
      SnippetCommentInfo info1 = commentInfo(c1.getId(), "Nice!");

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));
      when(commentRepository.findAllBySnippetIdOrderByCreatedAtAsc(
              eq(snippetId), any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of(c1)));
      when(snippetMapper.toCommentInfo(c1)).thenReturn(info1);

      PageResponse<SnippetCommentInfo> result = commentService.listComments(snippetId, null, 0, 10);

      assertThat(result.content()).singleElement().isSameAs(info1);
    }

    @Test
    @DisplayName("allows owner to list comments on their private snippet")
    void allowsOwner_forPrivateSnippet() {
      UUID ownerId = UUID.randomUUID();
      UUID snippetId = UUID.randomUUID();
      Tenant owner = tenant(ownerId, "owner");
      Snippet snippet = privateSnippet(snippetId, owner);

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));
      when(commentRepository.findAllBySnippetIdOrderByCreatedAtAsc(
              eq(snippetId), any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of()));

      PageResponse<SnippetCommentInfo> result =
          commentService.listComments(snippetId, ownerId, 0, 10);

      assertThat(result.content()).isEmpty();
    }

    @Test
    @DisplayName("returns empty page when there are no comments")
    void returnsEmptyPage_whenNoComments() {
      UUID snippetId = UUID.randomUUID();
      Tenant owner = tenant(UUID.randomUUID(), "owner");
      Snippet snippet = publicSnippet(snippetId, owner);

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));
      when(commentRepository.findAllBySnippetIdOrderByCreatedAtAsc(
              eq(snippetId), any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of()));

      PageResponse<SnippetCommentInfo> result = commentService.listComments(snippetId, null, 0, 10);

      assertThat(result.content()).isEmpty();
      assertThat(result.totalElements()).isZero();
    }
  }

  // ──────────────────────────── addComment ────────────────────────────

  @Nested
  @DisplayName("addComment()")
  class AddComment {

    @Test
    @DisplayName("throws ItemNotFoundException when snippet not found")
    void throws_whenSnippetMissing() {
      UUID snippetId = UUID.randomUUID();
      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.empty());

      assertThatThrownBy(
              () ->
                  commentService.addComment(
                      UUID.randomUUID(), snippetId, new SnippetCommentForm("body")))
          .isInstanceOf(ItemNotFoundException.class)
          .hasMessageContaining("snippetNotFound");
    }

    @Test
    @DisplayName("throws ItemNotFoundException when non-owner adds comment to private snippet")
    void throws_whenNonOwnerCommentsOnPrivateSnippet() {
      UUID snippetId = UUID.randomUUID();
      UUID ownerId = UUID.randomUUID();
      UUID otherId = UUID.randomUUID();
      Tenant owner = tenant(ownerId, "owner");
      Snippet snippet = privateSnippet(snippetId, owner);

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));

      assertThatThrownBy(
              () -> commentService.addComment(otherId, snippetId, new SnippetCommentForm("body")))
          .isInstanceOf(ItemNotFoundException.class)
          .hasMessageContaining("snippetNotFound");
    }

    @Test
    @DisplayName("saves comment and increments commentCount")
    void savesCommentAndIncrementsCount() {
      UUID tenantId = UUID.randomUUID();
      UUID snippetId = UUID.randomUUID();
      Tenant author = tenant(tenantId, "alice");
      Snippet snippet = publicSnippet(snippetId, author);

      SnippetComment savedComment = comment(UUID.randomUUID(), author, snippet, "Great snippet!");
      SnippetCommentInfo expectedInfo = commentInfo(savedComment.getId(), "Great snippet!");

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));
      when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(author));
      when(commentRepository.save(any(SnippetComment.class))).thenReturn(savedComment);
      when(snippetMapper.toCommentInfo(savedComment)).thenReturn(expectedInfo);

      SnippetCommentInfo result =
          commentService.addComment(tenantId, snippetId, new SnippetCommentForm("Great snippet!"));

      assertThat(result).isSameAs(expectedInfo);
      verify(snippetRepository).incrementCommentCount(snippetId);

      ArgumentCaptor<SnippetComment> captor = ArgumentCaptor.forClass(SnippetComment.class);
      verify(commentRepository).save(captor.capture());
      assertThat(captor.getValue().getBody()).isEqualTo("Great snippet!");
      assertThat(captor.getValue().getAuthor()).isSameAs(author);
    }

    @Test
    @DisplayName("allows owner to add comment on their own private snippet")
    void allowsOwner_toCommentOnPrivateSnippet() {
      UUID ownerId = UUID.randomUUID();
      UUID snippetId = UUID.randomUUID();
      Tenant owner = tenant(ownerId, "alice");
      Snippet snippet = privateSnippet(snippetId, owner);

      SnippetComment saved = comment(UUID.randomUUID(), owner, snippet, "my note");
      SnippetCommentInfo info = commentInfo(saved.getId(), "my note");

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));
      when(tenantRepository.findById(ownerId)).thenReturn(Optional.of(owner));
      when(commentRepository.save(any())).thenReturn(saved);
      when(snippetMapper.toCommentInfo(saved)).thenReturn(info);

      SnippetCommentInfo result =
          commentService.addComment(ownerId, snippetId, new SnippetCommentForm("my note"));

      assertThat(result).isSameAs(info);
    }
  }

  // ──────────────────────────── deleteComment ────────────────────────────

  @Nested
  @DisplayName("deleteComment()")
  class DeleteComment {

    @Test
    @DisplayName("throws ItemNotFoundException when snippet not found")
    void throws_whenSnippetMissing() {
      UUID snippetId = UUID.randomUUID();
      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.empty());

      assertThatThrownBy(
              () -> commentService.deleteComment(UUID.randomUUID(), snippetId, UUID.randomUUID()))
          .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    @DisplayName("throws ItemNotFoundException when comment not found")
    void throws_whenCommentMissing() {
      UUID snippetId = UUID.randomUUID();
      UUID commentId = UUID.randomUUID();
      Tenant owner = tenant(UUID.randomUUID(), "owner");
      Snippet snippet = publicSnippet(snippetId, owner);

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));
      when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

      assertThatThrownBy(
              () -> commentService.deleteComment(UUID.randomUUID(), snippetId, commentId))
          .isInstanceOf(ItemNotFoundException.class)
          .hasMessageContaining("commentNotFound");
    }

    @Test
    @DisplayName("throws AccessNotAllowedException when caller is neither author nor snippet owner")
    void throws_whenNeitherAuthorNorSnippetOwner() {
      UUID snippetId = UUID.randomUUID();
      UUID commentId = UUID.randomUUID();
      UUID ownerId = UUID.randomUUID();
      UUID authorId = UUID.randomUUID();
      UUID otherId = UUID.randomUUID();

      Tenant owner = tenant(ownerId, "owner");
      Tenant author = tenant(authorId, "commenter");
      Snippet snippet = publicSnippet(snippetId, owner);
      SnippetComment c = comment(commentId, author, snippet, "body");

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));
      when(commentRepository.findById(commentId)).thenReturn(Optional.of(c));

      assertThatThrownBy(() -> commentService.deleteComment(otherId, snippetId, commentId))
          .isInstanceOf(AccessNotAllowedException.class)
          .hasMessageContaining("notCommentAuthorOrSnippetOwner");
    }

    @Test
    @DisplayName("deletes comment when caller is the comment author")
    void deletes_whenCallerIsAuthor() {
      UUID snippetId = UUID.randomUUID();
      UUID commentId = UUID.randomUUID();
      UUID authorId = UUID.randomUUID();
      UUID ownerId = UUID.randomUUID();

      Tenant owner = tenant(ownerId, "owner");
      Tenant author = tenant(authorId, "alice");
      Snippet snippet = publicSnippet(snippetId, owner);
      SnippetComment c = comment(commentId, author, snippet, "body");

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));
      when(commentRepository.findById(commentId)).thenReturn(Optional.of(c));

      commentService.deleteComment(authorId, snippetId, commentId);

      verify(commentRepository).delete(c);
      verify(snippetRepository).decrementCommentCount(snippetId);
    }

    @Test
    @DisplayName("deletes comment when caller is the snippet owner (not author)")
    void deletes_whenCallerIsSnippetOwner() {
      UUID snippetId = UUID.randomUUID();
      UUID commentId = UUID.randomUUID();
      UUID ownerId = UUID.randomUUID();
      UUID authorId = UUID.randomUUID();

      Tenant owner = tenant(ownerId, "owner");
      Tenant author = tenant(authorId, "bob");
      Snippet snippet = publicSnippet(snippetId, owner);
      SnippetComment c = comment(commentId, author, snippet, "rude comment");

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));
      when(commentRepository.findById(commentId)).thenReturn(Optional.of(c));

      commentService.deleteComment(ownerId, snippetId, commentId);

      verify(commentRepository).delete(c);
      verify(snippetRepository).decrementCommentCount(snippetId);
    }
  }
}
