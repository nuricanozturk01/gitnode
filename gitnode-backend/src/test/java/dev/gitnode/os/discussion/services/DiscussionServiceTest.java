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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.discussion.dtos.DiscussionCommentForm;
import dev.gitnode.os.discussion.dtos.DiscussionForm;
import dev.gitnode.os.discussion.entities.Discussion;
import dev.gitnode.os.discussion.entities.DiscussionCategory;
import dev.gitnode.os.discussion.entities.DiscussionComment;
import dev.gitnode.os.discussion.repositories.DiscussionCommentRepository;
import dev.gitnode.os.discussion.repositories.DiscussionRepository;
import dev.gitnode.os.discussion.repositories.DiscussionVoteRepository;
import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.repo.api.CollaboratorAccessPort;
import dev.gitnode.os.repo.api.CollaboratorPermission;
import dev.gitnode.os.repo.api.RepoCommandPort;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiscussionService unit tests")
class DiscussionServiceTest {

  @Mock private DiscussionRepository discussions;
  @Mock private DiscussionCommentRepository comments;
  @Mock private DiscussionVoteRepository votes;
  @Mock private RepoLookupPort repoLookup;
  @Mock private RepoCommandPort repoCommands;
  @Mock private TenantQueryPort tenants;
  @Mock private CollaboratorAccessPort access;
  @Mock private dev.gitnode.os.shared.cache.CachePatternEvictor cacheEvictor;

  @InjectMocks private DiscussionService service;

  private final UUID ownerId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private RepoRef repo;
  private Discussion discussion;

  @BeforeEach
  void setUp() {
    this.repo = new RepoRef(UUID.randomUUID(), this.ownerId, "alice", "demo", false);
    this.discussion = new Discussion();
    this.discussion.setId(UUID.randomUUID());
    this.discussion.setRepoId(this.repo.id());
    this.discussion.setNumber(3);
    this.discussion.setTitle("How?");
    this.discussion.setCategory(DiscussionCategory.Q_AND_A.name());
    this.discussion.setAuthorId(this.ownerId);
    when(this.repoLookup.find("alice", "demo")).thenReturn(Optional.of(this.repo));
  }

  private void discussionExists() {
    when(this.discussions.findByRepoIdAndNumber(this.repo.id(), 3))
        .thenReturn(Optional.of(this.discussion));
  }

  private void tenantsKnown() {
    when(this.tenants.findAllByIds(any()))
        .thenReturn(
            Map.of(
                this.ownerId,
                new TenantRef(this.ownerId, "alice", "a@x.io", null, null, true),
                this.userId,
                new TenantRef(this.userId, "bob", "b@x.io", null, null, true)));
  }

  @Test
  @DisplayName("only maintainers may post announcements")
  void create_announcement_needsModerator() {
    when(this.access.hasPermission(
            this.repo.id(), this.userId, CollaboratorPermission.ISSUE_MANAGE))
        .thenReturn(false);

    assertThatThrownBy(
            () ->
                this.service.create(
                    "alice",
                    "demo",
                    this.userId,
                    new DiscussionForm("News", null, DiscussionCategory.ANNOUNCEMENTS)))
        .isInstanceOf(AccessNotAllowedException.class);
    verify(this.discussions, never()).save(any());
  }

  @Test
  @DisplayName("anyone with access can start a general discussion and gets the next number")
  void create_assignsNextNumber() {
    when(this.repoCommands.lockForUpdate(this.repo.id())).thenReturn(true);
    when(this.discussions.findMaxNumberByRepoId(this.repo.id())).thenReturn(4);
    when(this.discussions.save(any(Discussion.class))).thenAnswer(call -> call.getArgument(0));
    tenantsKnown();

    final var created =
        this.service.create(
            "alice",
            "demo",
            this.userId,
            new DiscussionForm("  Hello  ", "  body ", DiscussionCategory.GENERAL));

    assertThat(created.info().number()).isEqualTo(5);
    assertThat(created.info().title()).isEqualTo("Hello");
    assertThat(created.body()).isEqualTo("body");
  }

  @Test
  @DisplayName("a locked discussion rejects comments from non-maintainers")
  void addComment_rejectsWhenLocked() {
    discussionExists();
    this.discussion.setLocked(true);
    when(this.access.hasPermission(
            this.repo.id(), this.userId, CollaboratorPermission.ISSUE_MANAGE))
        .thenReturn(false);

    assertThatThrownBy(
            () ->
                this.service.addComment(
                    "alice", "demo", 3, this.userId, new DiscussionCommentForm("hi", null)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("locked");
  }

  @Test
  @DisplayName("replies cannot be nested more than one level")
  void addComment_rejectsNestedReply() {
    discussionExists();
    final var reply = new DiscussionComment();
    reply.setId(UUID.randomUUID());
    reply.setParentId(UUID.randomUUID());
    when(this.comments.findByIdAndDiscussionId(reply.getId(), this.discussion.getId()))
        .thenReturn(Optional.of(reply));

    assertThatThrownBy(
            () ->
                this.service.addComment(
                    "alice",
                    "demo",
                    3,
                    this.ownerId,
                    new DiscussionCommentForm("nested", reply.getId())))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("nested");
  }

  @Test
  @DisplayName("only questions can have an answer")
  void setAnswer_rejectsNonQuestions() {
    discussionExists();
    this.discussion.setCategory(DiscussionCategory.GENERAL.name());

    assertThatThrownBy(
            () -> this.service.setAnswer("alice", "demo", 3, this.ownerId, UUID.randomUUID()))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("questions");
  }

  @Test
  @DisplayName("the asker marks a comment as the answer")
  void setAnswer_storesComment() {
    discussionExists();
    final var answer = new DiscussionComment();
    answer.setId(UUID.randomUUID());
    answer.setAuthorId(this.userId);
    answer.setBody("Use X");
    when(this.comments.findByIdAndDiscussionId(answer.getId(), this.discussion.getId()))
        .thenReturn(Optional.of(answer));
    when(this.discussions.save(this.discussion)).thenReturn(this.discussion);
    tenantsKnown();

    final var detail = this.service.setAnswer("alice", "demo", 3, this.ownerId, answer.getId());

    assertThat(detail.answer().id()).isEqualTo(answer.getId());
    assertThat(detail.info().answered()).isTrue();
  }

  @Test
  @DisplayName("somebody else cannot mark the answer or delete the discussion")
  void moderationNeedsAuthorOrMaintainer() {
    discussionExists();
    when(this.access.hasPermission(
            this.repo.id(), this.userId, CollaboratorPermission.ISSUE_MANAGE))
        .thenReturn(false);

    assertThatThrownBy(
            () -> this.service.setAnswer("alice", "demo", 3, this.userId, UUID.randomUUID()))
        .isInstanceOf(AccessNotAllowedException.class);
    assertThatThrownBy(() -> this.service.delete("alice", "demo", 3, this.userId))
        .isInstanceOf(AccessNotAllowedException.class);
    verify(this.discussions, never()).delete(any());
  }

  @Test
  @DisplayName("upvoting twice stores one vote and removing deletes it")
  void setUpvote_toggles() {
    discussionExists();
    final var id = this.discussion.getId();
    when(this.votes.existsByDiscussionIdAndTenantId(id, this.userId)).thenReturn(false, true);
    when(this.votes.countByDiscussionId(id)).thenReturn(1L, 1L, 0L);

    assertThat(this.service.setUpvote("alice", "demo", 3, this.userId, true).upvotes())
        .isEqualTo(1);
    assertThat(this.service.setUpvote("alice", "demo", 3, this.userId, true).upvotes())
        .isEqualTo(1);
    assertThat(this.service.setUpvote("alice", "demo", 3, this.userId, false).upvotes()).isZero();

    verify(this.votes, times(1)).save(any());
    verify(this.votes).deleteByDiscussionIdAndTenantId(id, this.userId);
  }

  @Test
  @DisplayName("a page of discussions costs a fixed number of count queries, not two per row")
  void list_batchesCounts() {
    final var second = new Discussion();
    second.setId(UUID.randomUUID());
    second.setAuthorId(this.userId);
    second.setTitle("Second");
    second.setCategory(DiscussionCategory.GENERAL.name());
    when(this.discussions.search(any(), anyBoolean(), any(), anyBoolean(), any()))
        .thenReturn(new PageImpl<>(List.of(this.discussion, second)));
    when(this.comments.countByDiscussionIds(any())).thenReturn(List.of());
    when(this.votes.countByDiscussionIds(any())).thenReturn(List.of());
    tenantsKnown();

    final var page = this.service.list("alice", "demo", null, false, "latest", 0);

    assertThat(page.content()).hasSize(2);
    verify(this.comments, times(1)).countByDiscussionIds(any());
    verify(this.votes, times(1)).countByDiscussionIds(any());
    verify(this.tenants, times(1)).findAllByIds(any());
  }
}
