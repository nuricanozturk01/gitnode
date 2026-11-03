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

import dev.gitnode.os.commit.api.AuthorInfo;
import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.pr.dtos.PrCommentForm;
import dev.gitnode.os.pr.dtos.PrCommentInfo;
import dev.gitnode.os.pr.dtos.PrCommentUpdateForm;
import dev.gitnode.os.pr.entities.PullRequest;
import dev.gitnode.os.pr.entities.PullRequestComment;
import dev.gitnode.os.pr.mappers.PrMapper;
import dev.gitnode.os.pr.repositories.PrCommentRepository;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.core.PropertyPath;

@ExtendWith(MockitoExtension.class)
@DisplayName("PullRequestCommentService unit tests")
class PullRequestCommentServiceTest {

  @Mock private PrMapper prMapper;
  @Mock private PrFinder prFinder;
  @Mock private dev.gitnode.os.repo.api.CollaboratorAccessPort access;
  @Mock private PrCommentRepository commentRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private PullRequestCommentService commentService;

  @Test
  @DisplayName("comment repository paths resolve through the pull request id")
  void commentRepositoryPaths_resolve() {
    assertThat(PropertyPath.from("prId", PullRequestComment.class).toDotPath()).isEqualTo("pr.id");
    assertThat(PropertyPath.from("authorId", PullRequestComment.class).toDotPath())
        .isEqualTo("authorId");
  }

  @Test
  @DisplayName("addComment saves comment for valid PR")
  void addComment_savesComment_whenValid() {
    UUID authorId = UUID.randomUUID();
    RepoRef repo = new RepoRef(UUID.randomUUID(), UUID.randomUUID(), "alice", "demo", false);
    PullRequest pr = new PullRequest();
    pr.setId(UUID.randomUUID());
    pr.setAuthorId(UUID.randomUUID());
    TenantRef author = new TenantRef(authorId, "alice", "a@test.com", null, null, true);
    when(prFinder.findRepo("alice", "demo")).thenReturn(repo);
    when(prFinder.findPr(repo.id(), 1)).thenReturn(pr);
    when(prFinder.findTenant(authorId)).thenReturn(author);
    when(prFinder.findTenants(any())).thenReturn(java.util.Map.of(authorId, author));
    when(commentRepository.findDistinctCommenterIdsByPrId(pr.getId()))
        .thenReturn(java.util.List.of());
    PullRequestComment saved = new PullRequestComment();
    saved.setId(UUID.randomUUID());
    saved.setBody("Looks good");
    saved.setAuthorId(author.id());
    when(commentRepository.save(any(PullRequestComment.class))).thenReturn(saved);
    AuthorInfo authorInfo = new AuthorInfo("Alice", "a@test.com", "alice", null);
    when(prMapper.toAuthorInfo(author)).thenReturn(authorInfo);
    PrCommentInfo info = sampleCommentInfo(saved.getId(), authorInfo, "Looks good");
    when(prMapper.toCommentInfo(saved, authorInfo)).thenReturn(info);
    PrCommentForm form = new PrCommentForm("Looks good", null, null, null, null, null);

    PrCommentInfo result = commentService.addComment("alice", "demo", 1, authorId, form);

    assertThat(result.body()).isEqualTo("Looks good");
  }

  @Test
  @DisplayName("updateComment throws ItemNotFoundException when comment missing")
  void updateComment_throws_whenCommentMissing() {
    UUID commentId = UUID.randomUUID();
    PrCommentUpdateForm form = new PrCommentUpdateForm("edited");

    assertThatThrownBy(
            () ->
                commentService.updateComment(
                    "alice", "demo", 1, commentId, UUID.randomUUID(), form))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("Comment not found");
  }

  @Test
  @DisplayName("updateComment rejects a requester who is not the author")
  void updateComment_throws_whenNotAuthor() {
    UUID commentId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    UUID otherId = UUID.randomUUID();
    PullRequestComment comment = new PullRequestComment();
    comment.setAuthorId(authorId);
    stubComment(comment);
    PrCommentUpdateForm form = new PrCommentUpdateForm("edited");

    assertThatThrownBy(
            () -> commentService.updateComment("alice", "demo", 1, commentId, otherId, form))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessage("notAuthorized");

    verify(commentRepository, never()).save(any());
  }

  @Test
  @DisplayName("deleteComment removes comment when requester is author")
  void deleteComment_deletes_whenAuthor() {
    UUID commentId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    PullRequestComment comment = new PullRequestComment();
    comment.setAuthorId(authorId);
    stubComment(comment);

    commentService.deleteComment("alice", "demo", 1, commentId, authorId);

    verify(commentRepository).delete(comment);
  }

  @Test
  @DisplayName("deleteComment rejects a requester who is not the author")
  void deleteComment_throws_whenNotAuthor() {
    UUID commentId = UUID.randomUUID();
    PullRequestComment comment = new PullRequestComment();
    comment.setAuthorId(UUID.randomUUID());
    stubComment(comment);

    assertThatThrownBy(
            () -> commentService.deleteComment("alice", "demo", 1, commentId, UUID.randomUUID()))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessage("notAuthorized");
  }

  private static PrCommentInfo sampleCommentInfo(UUID id, AuthorInfo author, String body) {
    Instant now = Instant.now();
    return new PrCommentInfo(id, author, body, null, null, null, null, null, false, now, now);
  }

  /** Wires repository, pull request and comment lookups for a comment of any PR. */
  private PullRequest stubComment(final PullRequestComment comment) {
    return this.stubComment(comment, UUID.randomUUID());
  }

  private PullRequest stubComment(final PullRequestComment comment, final UUID repoOwnerId) {
    final var repo = new RepoRef(UUID.randomUUID(), repoOwnerId, "o", "r", false);
    final var pr = comment.getPr() != null ? comment.getPr() : new PullRequest();
    if (pr.getId() == null) {
      pr.setId(UUID.randomUUID());
    }
    comment.setPr(pr);
    org.mockito.Mockito.lenient()
        .when(
            prFinder.findRepoOrNull(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(Optional.of(repo));
    org.mockito.Mockito.lenient()
        .when(
            prFinder.findRepo(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(repo);
    org.mockito.Mockito.lenient()
        .when(
            prFinder.findPrOrNull(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(Optional.of(pr));
    org.mockito.Mockito.lenient()
        .when(
            commentRepository.findByIdAndPrId(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(Optional.of(comment));
    org.mockito.Mockito.lenient()
        .when(commentRepository.save(org.mockito.ArgumentMatchers.any(PullRequestComment.class)))
        .thenAnswer(i -> i.getArgument(0));
    org.mockito.Mockito.lenient()
        .when(prFinder.findTenants(org.mockito.ArgumentMatchers.any()))
        .thenReturn(java.util.Map.of());
    return pr;
  }

  private PullRequestComment lineComment(
      final UUID author, final UUID prAuthor, final UUID repoOwner, final String filePath) {
    final var pr = new PullRequest();
    pr.setAuthorId(prAuthor);
    final var comment = new PullRequestComment();
    comment.setPr(pr);
    comment.setAuthorId(author);
    comment.setFilePath(filePath);
    stubComment(comment, repoOwner);
    return comment;
  }

  private UUID person() {
    return UUID.randomUUID();
  }

  @Test
  @DisplayName("the PR author and the owner may resolve a line conversation, strangers may not")
  void setResolved_permissions() {
    final var commenter = person();
    final var prAuthor = person();
    final var owner = person();
    final var stranger = person();
    final var comment = lineComment(commenter, prAuthor, owner, "a.txt");

    commentService.setResolved("o", "r", 1, java.util.UUID.randomUUID(), prAuthor, true);
    org.assertj.core.api.Assertions.assertThat(comment.isResolved()).isTrue();
    commentService.setResolved("o", "r", 1, java.util.UUID.randomUUID(), owner, false);
    org.assertj.core.api.Assertions.assertThat(comment.isResolved()).isFalse();
    commentService.setResolved("o", "r", 1, java.util.UUID.randomUUID(), commenter, true);
    org.assertj.core.api.Assertions.assertThat(comment.isResolved()).isTrue();

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                commentService.setResolved(
                    "o", "r", 1, java.util.UUID.randomUUID(), stranger, false))
        .isInstanceOf(
            dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException.class);
  }

  @Test
  @DisplayName("general comments cannot be resolved")
  void setResolved_rejectsGeneralComments() {
    final var user = person();
    lineComment(user, user, user, null);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> commentService.setResolved("o", "r", 1, java.util.UUID.randomUUID(), user, true))
        .isInstanceOf(dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException.class);
  }
}
