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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.events.pr.PullRequestStatusChangedEvent;
import dev.gitnode.os.pr.dtos.MergeStrategy;
import dev.gitnode.os.pr.dtos.PrDetail;
import dev.gitnode.os.pr.dtos.PrInfo;
import dev.gitnode.os.pr.dtos.PrMergeForm;
import dev.gitnode.os.pr.dtos.PrUpdateForm;
import dev.gitnode.os.pr.entities.PrStatus;
import dev.gitnode.os.pr.entities.PullRequest;
import dev.gitnode.os.pr.mappers.PrMapper;
import dev.gitnode.os.pr.repositories.PrCommentRepository;
import dev.gitnode.os.pr.repositories.PrRepository;
import dev.gitnode.os.shared.commit.dtos.AuthorInfo;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.git.provider.GitProvider;
import dev.gitnode.os.shared.repo.entities.Repo;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("PullRequestService unit tests")
class PullRequestServiceTest {

  @Mock private PrRepository prRepository;
  @Mock private PrCommentRepository commentRepository;
  @Mock private PrFinder prFinder;
  @Mock private GitProvider gitProvider;
  @Mock private PrMapper prMapper;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private PullRequestService pullRequestService;

  @Test
  @DisplayName("get throws via prFinder when repository not found")
  void get_throws_whenRepoMissing() {
    when(prFinder.findRepo("alice", "missing"))
        .thenThrow(new ItemNotFoundException("Repository not found"));

    assertThatThrownBy(() -> pullRequestService.get("alice", "missing", 1))
        .isInstanceOf(ItemNotFoundException.class);
  }

  @Test
  @DisplayName("get returns PR detail when found")
  void get_returnsDetail_whenPrExists() {
    Repo repo = repo();
    PullRequest pr = openPr(repo);
    PrDetail detail = sampleDetail(pr.getId(), "Fix bug");
    when(prFinder.findRepo("alice", "demo")).thenReturn(repo);
    when(prFinder.findPr(repo.getId(), 1)).thenReturn(pr);
    when(commentRepository.countByPrId(pr.getId())).thenReturn(0L);
    when(prMapper.toAuthorInfo(pr.getAuthor())).thenReturn(authorInfo());
    when(prMapper.toDetail(eq(pr), eq(0), any(), eq(null))).thenReturn(detail);

    PrDetail result = pullRequestService.get("alice", "demo", 1);

    assertThat(result.title()).isEqualTo("Fix bug");
  }

  @Test
  @DisplayName("update throws ErrorOccurredException when PR is not open")
  void update_throws_whenPrClosed() {
    Repo repo = repo();
    PullRequest pr = openPr(repo);
    pr.setStatus(PrStatus.CLOSED.name());
    when(prFinder.findRepo("alice", "demo")).thenReturn(repo);
    when(prFinder.findPr(repo.getId(), 1)).thenReturn(pr);
    PrUpdateForm form = new PrUpdateForm("New title", null, null);

    assertThatThrownBy(() -> pullRequestService.update("alice", "demo", 1, form))
        .isInstanceOf(ErrorOccurredException.class)
        .hasMessageContaining("Cannot update a closed or merged");

    verify(prRepository, never()).save(any());
  }

  @Test
  @DisplayName("update saves changes when PR is open")
  void update_saves_whenPrOpen() {
    Repo repo = repo();
    PullRequest pr = openPr(repo);
    PrDetail detail = sampleDetail(pr.getId(), "New title");
    when(prFinder.findRepo("alice", "demo")).thenReturn(repo);
    when(prFinder.findPr(repo.getId(), 1)).thenReturn(pr);
    when(prRepository.save(pr)).thenReturn(pr);
    when(commentRepository.countByPrId(pr.getId())).thenReturn(0L);
    when(prMapper.toAuthorInfo(pr.getAuthor())).thenReturn(authorInfo());
    when(prMapper.toDetail(eq(pr), eq(0), any(), eq(null))).thenReturn(detail);
    PrUpdateForm form = new PrUpdateForm("New title", null, null);

    PrDetail result = pullRequestService.update("alice", "demo", 1, form);

    assertThat(result.title()).isEqualTo("New title");
    assertThat(pr.getTitle()).isEqualTo("New title");
  }

  @Test
  @DisplayName("close throws ErrorOccurredException when PR already closed")
  void close_throws_whenAlreadyClosed() {
    Repo repo = repo();
    PullRequest pr = openPr(repo);
    pr.setStatus(PrStatus.MERGED.name());
    when(prFinder.findRepo("alice", "demo")).thenReturn(repo);
    when(prFinder.findPr(repo.getId(), 1)).thenReturn(pr);

    assertThatThrownBy(() -> pullRequestService.close("alice", "demo", 1, UUID.randomUUID()))
        .isInstanceOf(ErrorOccurredException.class)
        .hasMessageContaining("already closed or merged");
  }

  @Test
  @DisplayName("close marks PR closed and publishes status event")
  void close_closesPr_whenOpen() {
    Repo repo = repo();
    PullRequest pr = openPr(repo);
    UUID closedById = UUID.randomUUID();
    when(prFinder.findRepo("alice", "demo")).thenReturn(repo);
    when(prFinder.findPr(repo.getId(), 1)).thenReturn(pr);
    when(prRepository.save(pr)).thenReturn(pr);
    when(commentRepository.findDistinctCommenterIdsByPrId(pr.getId()))
        .thenReturn(java.util.List.of());

    pullRequestService.close("alice", "demo", 1, closedById);

    assertThat(pr.getStatus()).isEqualTo(PrStatus.CLOSED.name());
    assertThat(pr.getClosedAt()).isNotNull();
    verify(eventPublisher).publishEvent(any(PullRequestStatusChangedEvent.class));
  }

  @Test
  @DisplayName("get throws ItemNotFoundException when pull request missing")
  void get_throws_whenPrMissing() {
    Repo repo = repo();
    when(prFinder.findRepo("alice", "demo")).thenReturn(repo);
    when(prFinder.findPr(repo.getId(), 99))
        .thenThrow(new ItemNotFoundException("Pull request not found: #99"));

    assertThatThrownBy(() -> pullRequestService.get("alice", "demo", 99))
        .isInstanceOf(ItemNotFoundException.class);
  }

  @Test
  @DisplayName("merge throws ErrorOccurredException when pull request is not open")
  void merge_throws_whenPrNotOpen() {
    Repo repo = repo();
    PullRequest pr = openPr(repo);
    pr.setStatus(PrStatus.CLOSED.name());
    when(prFinder.findRepo("alice", "demo")).thenReturn(repo);
    when(prFinder.findPr(repo.getId(), 1)).thenReturn(pr);
    when(prFinder.findTenant(any())).thenReturn(pr.getAuthor());
    PrMergeForm form = new PrMergeForm(MergeStrategy.MERGE_COMMIT, null);

    assertThatThrownBy(
            () -> pullRequestService.merge("alice", "demo", 1, pr.getAuthor().getId(), form))
        .isInstanceOf(ErrorOccurredException.class)
        .hasMessageContaining("Pull request is not open");
  }

  @Test
  @DisplayName("update applies description and draft flag when PR is open")
  void update_appliesDescriptionAndDraft_whenOpen() {
    Repo repo = repo();
    PullRequest pr = openPr(repo);
    PrDetail detail = sampleDetail(pr.getId(), "Fix bug");
    when(prFinder.findRepo("alice", "demo")).thenReturn(repo);
    when(prFinder.findPr(repo.getId(), 1)).thenReturn(pr);
    when(prRepository.save(pr)).thenReturn(pr);
    when(commentRepository.countByPrId(pr.getId())).thenReturn(0L);
    when(prMapper.toAuthorInfo(pr.getAuthor())).thenReturn(authorInfo());
    when(prMapper.toDetail(eq(pr), eq(0), any(), eq(null))).thenReturn(detail);
    PrUpdateForm form = new PrUpdateForm(null, "Updated body", true);

    pullRequestService.update("alice", "demo", 1, form);

    assertThat(pr.getDescription()).isEqualTo("Updated body");
    assertThat(pr.isDraft()).isTrue();
  }

  @Test
  @DisplayName("getAll returns page of PR infos")
  void getAll_returnsPage_whenRepoExists() {
    Repo repo = repo();
    PullRequest pr = openPr(repo);
    when(prFinder.findRepo("alice", "demo")).thenReturn(repo);
    Page<PullRequest> page = new PageImpl<>(java.util.List.of(pr), PageRequest.of(0, 10), 1);
    when(prRepository.findAllByRepoIdAndStatusOrderByCreatedAtDesc(
            eq(repo.getId()), eq(PrStatus.OPEN.name()), any()))
        .thenReturn(page);
    when(commentRepository.countByPrId(pr.getId())).thenReturn(2L);
    when(prMapper.toAuthorInfo(pr.getAuthor())).thenReturn(authorInfo());
    PrInfo info = sampleInfo(pr.getId(), "Fix");
    when(prMapper.toInfo(eq(pr), eq(2), any(), eq(null))).thenReturn(info);

    Page<PrInfo> result = pullRequestService.getAll("alice", "demo", PrStatus.OPEN.name(), 0, 10);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().getFirst().title()).isEqualTo("Fix");
  }

  private static Repo repo() {
    Repo repo = new Repo();
    repo.setId(UUID.randomUUID());
    repo.setName("demo");
    Tenant owner = new Tenant();
    owner.setUsername("alice");
    repo.setOwner(owner);
    return repo;
  }

  private static PullRequest openPr(Repo repo) {
    Tenant author = new Tenant();
    author.setId(UUID.randomUUID());
    author.setUsername("bob");
    PullRequest pr = new PullRequest();
    pr.setId(UUID.randomUUID());
    pr.setRepo(repo);
    pr.setNumber(1);
    pr.setTitle("Fix bug");
    pr.setStatus(PrStatus.OPEN.name());
    pr.setSourceBranch("feature");
    pr.setTargetBranch("main");
    pr.setAuthor(author);
    pr.setCreatedAt(Instant.now());
    pr.setUpdatedAt(Instant.now());
    return pr;
  }

  private static AuthorInfo authorInfo() {
    return new AuthorInfo("Bob", "bob@test.com", "bob", null);
  }

  private static PrDetail sampleDetail(UUID id, String title) {
    AuthorInfo author = authorInfo();
    Instant now = Instant.now();
    return PrDetail.builder()
        .id(id)
        .number(1)
        .title(title)
        .status(PrStatus.OPEN.name())
        .isDraft(false)
        .author(author)
        .sourceBranch("feature")
        .targetBranch("main")
        .commentCount(0)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  private static PrInfo sampleInfo(UUID id, String title) {
    AuthorInfo author = authorInfo();
    Instant now = Instant.now();
    return new PrInfo(
        id,
        1,
        title,
        PrStatus.OPEN.name(),
        false,
        author,
        null,
        "feature",
        "main",
        null,
        0,
        now,
        now,
        null,
        null);
  }
}
