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
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.core.PropertyPath;

@ExtendWith(MockitoExtension.class)
@DisplayName("PullRequestCommentService unit tests")
class PullRequestCommentServiceTest {

  @Mock private PrMapper prMapper;
  @Mock private PrFinder prFinder;
  @Mock private dev.gitnode.os.shared.collaborator.services.CollaboratorAccessPort access;
  @Mock private PrCommentRepository commentRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private PullRequestCommentService commentService;

  @Test
  @DisplayName("comment repository paths resolve through pull request and repository")
  void commentRepositoryPaths_resolve() {
    assertThat(PropertyPath.from("prRepoOwnerUsername", PullRequestComment.class).toDotPath())
        .isEqualTo("pr.repo.owner.username");
    assertThat(PropertyPath.from("prRepoName", PullRequestComment.class).toDotPath())
        .isEqualTo("pr.repo.name");
    assertThat(PropertyPath.from("prNumber", PullRequestComment.class).toDotPath())
        .isEqualTo("pr.number");
  }

  @Test
  @DisplayName("addComment saves comment for valid PR")
  void addComment_savesComment_whenValid() {
    UUID authorId = UUID.randomUUID();
    Repo repo = new Repo();
    repo.setId(UUID.randomUUID());
    PullRequest pr = new PullRequest();
    pr.setId(UUID.randomUUID());
    Tenant prAuthor = new Tenant();
    prAuthor.setId(UUID.randomUUID());
    pr.setAuthor(prAuthor);
    Tenant author = new Tenant();
    author.setId(authorId);
    when(prFinder.findRepo("alice", "demo")).thenReturn(repo);
    when(prFinder.findPr(repo.getId(), 1)).thenReturn(pr);
    when(prFinder.findTenant(authorId)).thenReturn(author);
    when(commentRepository.findDistinctCommenterIdsByPrId(pr.getId()))
        .thenReturn(java.util.List.of());
    PullRequestComment saved = new PullRequestComment();
    saved.setId(UUID.randomUUID());
    saved.setBody("Looks good");
    saved.setAuthor(author);
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
    when(commentRepository.findByIdAndPrRepoOwnerUsernameAndPrRepoNameAndPrNumber(
            commentId, "alice", "demo", 1))
        .thenReturn(Optional.empty());
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
    Tenant author = new Tenant();
    author.setId(authorId);
    comment.setAuthor(author);
    when(commentRepository.findByIdAndPrRepoOwnerUsernameAndPrRepoNameAndPrNumber(
            commentId, "alice", "demo", 1))
        .thenReturn(Optional.of(comment));
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
    Tenant author = new Tenant();
    author.setId(authorId);
    comment.setAuthor(author);
    when(commentRepository.findByIdAndPrRepoOwnerUsernameAndPrRepoNameAndPrNumber(
            commentId, "alice", "demo", 1))
        .thenReturn(Optional.of(comment));

    commentService.deleteComment("alice", "demo", 1, commentId, authorId);

    verify(commentRepository).delete(comment);
  }

  @Test
  @DisplayName("deleteComment rejects a requester who is not the author")
  void deleteComment_throws_whenNotAuthor() {
    UUID commentId = UUID.randomUUID();
    PullRequestComment comment = new PullRequestComment();
    Tenant author = new Tenant();
    author.setId(UUID.randomUUID());
    comment.setAuthor(author);
    when(commentRepository.findByIdAndPrRepoOwnerUsernameAndPrRepoNameAndPrNumber(
            commentId, "alice", "demo", 1))
        .thenReturn(Optional.of(comment));

    assertThatThrownBy(
            () -> commentService.deleteComment("alice", "demo", 1, commentId, UUID.randomUUID()))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessage("notAuthorized");
  }

  private static PrCommentInfo sampleCommentInfo(UUID id, AuthorInfo author, String body) {
    Instant now = Instant.now();
    return new PrCommentInfo(id, author, body, null, null, null, null, null, false, now, now);
  }

  private dev.gitnode.os.pr.entities.PullRequestComment lineComment(
      final dev.gitnode.os.shared.tenant.entities.Tenant author,
      final dev.gitnode.os.shared.tenant.entities.Tenant prAuthor,
      final dev.gitnode.os.shared.tenant.entities.Tenant repoOwner,
      final String filePath) {
    final var repo = new dev.gitnode.os.shared.repo.entities.Repo();
    repo.setId(java.util.UUID.randomUUID());
    repo.setOwner(repoOwner);
    final var pr = new dev.gitnode.os.pr.entities.PullRequest();
    pr.setRepo(repo);
    pr.setAuthor(prAuthor);
    final var comment = new dev.gitnode.os.pr.entities.PullRequestComment();
    comment.setPr(pr);
    comment.setAuthor(author);
    comment.setFilePath(filePath);
    org.mockito.Mockito.lenient()
        .when(
            commentRepository.findByIdAndPrRepoOwnerUsernameAndPrRepoNameAndPrNumber(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(java.util.Optional.of(comment));
    org.mockito.Mockito.lenient()
        .when(
            commentRepository.save(
                org.mockito.ArgumentMatchers.any(
                    dev.gitnode.os.pr.entities.PullRequestComment.class)))
        .thenAnswer(i -> i.getArgument(0));
    return comment;
  }

  private dev.gitnode.os.shared.tenant.entities.Tenant person() {
    final var tenant = new dev.gitnode.os.shared.tenant.entities.Tenant();
    tenant.setId(java.util.UUID.randomUUID());
    return tenant;
  }

  @Test
  @DisplayName("the PR author and the owner may resolve a line conversation, strangers may not")
  void setResolved_permissions() {
    final var commenter = person();
    final var prAuthor = person();
    final var owner = person();
    final var stranger = person();
    final var comment = lineComment(commenter, prAuthor, owner, "a.txt");

    commentService.setResolved("o", "r", 1, java.util.UUID.randomUUID(), prAuthor.getId(), true);
    org.assertj.core.api.Assertions.assertThat(comment.isResolved()).isTrue();
    commentService.setResolved("o", "r", 1, java.util.UUID.randomUUID(), owner.getId(), false);
    org.assertj.core.api.Assertions.assertThat(comment.isResolved()).isFalse();
    commentService.setResolved("o", "r", 1, java.util.UUID.randomUUID(), commenter.getId(), true);
    org.assertj.core.api.Assertions.assertThat(comment.isResolved()).isTrue();

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                commentService.setResolved(
                    "o", "r", 1, java.util.UUID.randomUUID(), stranger.getId(), false))
        .isInstanceOf(
            dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException.class);
  }

  @Test
  @DisplayName("general comments cannot be resolved")
  void setResolved_rejectsGeneralComments() {
    final var user = person();
    lineComment(user, user, user, null);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                commentService.setResolved(
                    "o", "r", 1, java.util.UUID.randomUUID(), user.getId(), true))
        .isInstanceOf(dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException.class);
  }
}
