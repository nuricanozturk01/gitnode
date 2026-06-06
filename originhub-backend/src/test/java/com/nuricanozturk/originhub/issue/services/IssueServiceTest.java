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
package com.nuricanozturk.originhub.issue.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.events.issue.IssueCommentedEvent;
import com.nuricanozturk.originhub.events.issue.IssueCreatedEvent;
import com.nuricanozturk.originhub.events.issue.IssueStatusChangedEvent;
import com.nuricanozturk.originhub.events.issue.IssueUpdatedEvent;
import com.nuricanozturk.originhub.issue.api.IssueData;
import com.nuricanozturk.originhub.issue.api.TaskQueryPort;
import com.nuricanozturk.originhub.issue.dtos.IssueCommentForm;
import com.nuricanozturk.originhub.issue.dtos.IssueCommentInfo;
import com.nuricanozturk.originhub.issue.dtos.IssueDetail;
import com.nuricanozturk.originhub.issue.dtos.IssueForm;
import com.nuricanozturk.originhub.issue.dtos.IssueUpdateForm;
import com.nuricanozturk.originhub.issue.entities.Issue;
import com.nuricanozturk.originhub.issue.entities.IssueComment;
import com.nuricanozturk.originhub.issue.entities.IssueStatus;
import com.nuricanozturk.originhub.issue.mappers.IssueMapper;
import com.nuricanozturk.originhub.issue.repositories.IssueCommentRepository;
import com.nuricanozturk.originhub.issue.repositories.IssueRepository;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("IssueService unit tests")
class IssueServiceTest {

  @Mock private IssueRepository issueRepository;
  @Mock private IssueCommentRepository commentRepository;
  @Mock private RepoRepository repoRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private IssueMapper issueMapper;
  @Mock private TaskQueryPort taskQueryPort;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private IssueService issueService;

  @Test
  @DisplayName("create throws ItemNotFoundException when repository missing")
  void create_throws_whenRepoMissing() {
    UUID authorId = UUID.randomUUID();
    IssueForm form = new IssueForm("Bug", null, null);
    when(repoRepository.findByOwnerUsernameAndName("alice", "missing"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> issueService.create("alice", "missing", authorId, form))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("Repo not found");
  }

  @Test
  @DisplayName("create throws ItemNotFoundException when author missing")
  void create_throws_whenAuthorMissing() {
    UUID authorId = UUID.randomUUID();
    Repo repo = repo();
    IssueForm form = new IssueForm("Bug", null, null);
    when(repoRepository.findByOwnerUsernameAndName("alice", "demo")).thenReturn(Optional.of(repo));
    when(tenantRepository.findById(authorId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> issueService.create("alice", "demo", authorId, form))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("Author not found");
  }

  @Test
  @DisplayName("create saves issue with next number and publishes event")
  void create_savesIssue_whenValid() {
    UUID authorId = UUID.randomUUID();
    Repo repo = repo();
    Tenant author = tenant(authorId, "alice");
    IssueForm form = new IssueForm("Bug", "details", null);
    when(repoRepository.findByOwnerUsernameAndName("alice", "demo")).thenReturn(Optional.of(repo));
    when(tenantRepository.findById(authorId)).thenReturn(Optional.of(author));
    when(issueRepository.findMaxNumberByRepoId(repo.getId())).thenReturn(3);
    Issue saved = new Issue();
    saved.setId(UUID.randomUUID());
    saved.setNumber(4);
    saved.setTitle("Bug");
    saved.setStatus(IssueStatus.OPEN.name());
    when(issueRepository.save(any(Issue.class))).thenReturn(saved);
    IssueDetail detail = IssueDetail.builder().number(4).title("Bug").build();
    when(issueMapper.toDetail(saved, 0)).thenReturn(detail);

    IssueDetail result = issueService.create("alice", "demo", authorId, form);

    assertThat(result.number()).isEqualTo(4);
    verify(eventPublisher).publishEvent(any(IssueCreatedEvent.class));
  }

  @Test
  @DisplayName("create throws ItemNotFoundException when assignee missing")
  void create_throws_whenAssigneeMissing() {
    UUID authorId = UUID.randomUUID();
    UUID assigneeId = UUID.randomUUID();
    Repo repo = repo();
    Tenant author = tenant(authorId, "alice");
    IssueForm form = new IssueForm("Bug", null, assigneeId);
    when(repoRepository.findByOwnerUsernameAndName("alice", "demo")).thenReturn(Optional.of(repo));
    when(tenantRepository.findById(authorId)).thenReturn(Optional.of(author));
    when(issueRepository.findMaxNumberByRepoId(repo.getId())).thenReturn(0);
    when(tenantRepository.findById(assigneeId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> issueService.create("alice", "demo", authorId, form))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("Assignee not found");
  }

  @Test
  @DisplayName("get throws ItemNotFoundException when issue number not found")
  void get_throws_whenIssueMissing() {
    Repo repo = repo();
    when(repoRepository.findByOwnerUsernameAndName("alice", "demo")).thenReturn(Optional.of(repo));
    when(issueRepository.findByRepoIdAndNumber(repo.getId(), 42)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> issueService.get("alice", "demo", 42))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("Issue #42 not found");
  }

  @Test
  @DisplayName("getAll throws ErrorOccurredException for invalid status filter")
  void getAll_throws_whenStatusInvalid() {
    Repo repo = repo();
    when(repoRepository.findByOwnerUsernameAndName("alice", "demo")).thenReturn(Optional.of(repo));

    assertThatThrownBy(() -> issueService.getAll("alice", "demo", "NOT_A_STATUS", 0))
        .isInstanceOf(ErrorOccurredException.class)
        .hasMessageContaining("Invalid status");
  }

  @Test
  @DisplayName("findById returns IssueData when issue exists")
  void findById_returnsData_whenPresent() {
    UUID issueId = UUID.randomUUID();
    Issue issue = new Issue();
    issue.setId(issueId);
    issue.setNumber(7);
    issue.setTitle("Bug");
    issue.setStatus(IssueStatus.OPEN.name());
    when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));

    Optional<IssueData> result = issueService.findById(issueId);

    assertThat(result)
        .hasValueSatisfying(
            data -> {
              assertThat(data.id()).isEqualTo(issueId);
              assertThat(data.number()).isEqualTo(7);
              assertThat(data.title()).isEqualTo("Bug");
            });
  }

  @Test
  @DisplayName("addComment saves comment and publishes IssueCommentedEvent")
  void addComment_publishesEvent_whenValid() {
    UUID authorId = UUID.randomUUID();
    Repo repo = repo();
    Tenant author = tenant(authorId, "bob");
    Issue issue = openIssue(repo, author, 1);
    when(repoRepository.findByOwnerUsernameAndName("alice", "demo")).thenReturn(Optional.of(repo));
    when(issueRepository.findByRepoIdAndNumber(repo.getId(), 1)).thenReturn(Optional.of(issue));
    when(tenantRepository.findById(authorId)).thenReturn(Optional.of(author));
    IssueComment saved = new IssueComment();
    saved.setId(UUID.randomUUID());
    saved.setBody("Looks good");
    when(commentRepository.save(any(IssueComment.class))).thenReturn(saved);
    when(issueMapper.toCommentInfo(saved))
        .thenReturn(IssueCommentInfo.builder().id(saved.getId()).body("Looks good").build());

    issueService.addComment("alice", "demo", 1, authorId, new IssueCommentForm("Looks good"));

    verify(eventPublisher).publishEvent(any(IssueCommentedEvent.class));
  }

  @Test
  @DisplayName("update sets closedAt when status changes to CLOSED")
  void update_setsClosedAt_whenStatusClosed() {
    UUID requesterId = UUID.randomUUID();
    Repo repo = repo();
    Tenant author = tenant(requesterId, "alice");
    Issue issue = openIssue(repo, author, 1);
    when(repoRepository.findByOwnerUsernameAndName("alice", "demo")).thenReturn(Optional.of(repo));
    when(issueRepository.findByRepoIdAndNumber(repo.getId(), 1)).thenReturn(Optional.of(issue));
    when(issueRepository.save(issue)).thenReturn(issue);
    when(commentRepository.countByIssueId(issue.getId())).thenReturn(0);
    when(issueMapper.toDetail(issue, 0)).thenReturn(IssueDetail.builder().number(1).build());

    issueService.update(
        "alice",
        "demo",
        1,
        new IssueUpdateForm(null, null, IssueStatus.CLOSED.name(), null),
        requesterId);

    assertThat(issue.getStatus()).isEqualTo(IssueStatus.CLOSED.name());
    assertThat(issue.getClosedAt()).isNotNull();
    verify(eventPublisher).publishEvent(any(IssueStatusChangedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(IssueUpdatedEvent.class));
  }

  @Test
  @DisplayName("update clears closedAt when status changes back to OPEN")
  void update_clearsClosedAt_whenReopened() {
    UUID requesterId = UUID.randomUUID();
    Repo repo = repo();
    Tenant author = tenant(requesterId, "alice");
    Issue issue = openIssue(repo, author, 1);
    issue.setStatus(IssueStatus.CLOSED.name());
    issue.setClosedAt(Instant.now());
    when(repoRepository.findByOwnerUsernameAndName("alice", "demo")).thenReturn(Optional.of(repo));
    when(issueRepository.findByRepoIdAndNumber(repo.getId(), 1)).thenReturn(Optional.of(issue));
    when(issueRepository.save(issue)).thenReturn(issue);
    when(commentRepository.countByIssueId(issue.getId())).thenReturn(0);
    when(issueMapper.toDetail(issue, 0)).thenReturn(IssueDetail.builder().number(1).build());

    issueService.update(
        "alice",
        "demo",
        1,
        new IssueUpdateForm(null, null, IssueStatus.OPEN.name(), null),
        requesterId);

    assertThat(issue.getStatus()).isEqualTo(IssueStatus.OPEN.name());
    assertThat(issue.getClosedAt()).isNull();
  }

  private static Issue openIssue(Repo repo, Tenant author, int number) {
    Issue issue = new Issue();
    issue.setId(UUID.randomUUID());
    issue.setRepo(repo);
    issue.setNumber(number);
    issue.setTitle("Issue");
    issue.setStatus(IssueStatus.OPEN.name());
    issue.setAuthor(author);
    return issue;
  }

  private static Repo repo() {
    Repo repo = new Repo();
    repo.setId(UUID.randomUUID());
    repo.setName("demo");
    return repo;
  }

  private static Tenant tenant(UUID id, String username) {
    Tenant t = new Tenant();
    t.setId(id);
    t.setUsername(username);
    return t;
  }
}
