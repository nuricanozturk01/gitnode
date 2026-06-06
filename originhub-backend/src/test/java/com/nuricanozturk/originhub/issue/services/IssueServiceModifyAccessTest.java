package com.nuricanozturk.originhub.issue.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.events.issue.IssueDeletedEvent;
import com.nuricanozturk.originhub.issue.api.TaskQueryPort;
import com.nuricanozturk.originhub.issue.dtos.IssueCommentUpdateForm;
import com.nuricanozturk.originhub.issue.dtos.IssueUpdateForm;
import com.nuricanozturk.originhub.issue.entities.Issue;
import com.nuricanozturk.originhub.issue.entities.IssueComment;
import com.nuricanozturk.originhub.issue.mappers.IssueMapper;
import com.nuricanozturk.originhub.issue.repositories.IssueCommentRepository;
import com.nuricanozturk.originhub.issue.repositories.IssueRepository;
import com.nuricanozturk.originhub.shared.collaborator.services.CollaboratorAccessPort;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.AccessNotAllowedException;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("IssueService — modify access control (RAPOR2 bugs #2-4)")
class IssueServiceModifyAccessTest {

  @Mock private IssueRepository issueRepository;
  @Mock private IssueCommentRepository commentRepository;
  @Mock private RepoRepository repoRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private IssueMapper issueMapper;
  @Mock private TaskQueryPort taskQueryPort;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private CollaboratorAccessPort collaboratorAccessPort;

  @InjectMocks private IssueService issueService;

  // -----------------------------------------------------------------------
  // helpers
  // -----------------------------------------------------------------------

  private static Tenant tenant(UUID id, String username) {
    Tenant t = new Tenant();
    t.setId(id);
    t.setUsername(username);
    return t;
  }

  private static Repo repo(UUID id) {
    Repo r = new Repo();
    r.setId(id);
    r.setName("myrepo");
    return r;
  }

  private static Issue issue(UUID id, Tenant author, Repo repo) {
    Issue i = new Issue();
    i.setId(id);
    i.setNumber(1);
    i.setTitle("Issue title");
    i.setStatus("OPEN");
    i.setAuthor(author);
    i.setRepo(repo);
    return i;
  }

  private static IssueComment comment(UUID id, Tenant author, Issue issue) {
    IssueComment c = new IssueComment();
    c.setId(id);
    c.setAuthor(author);
    c.setIssue(issue);
    c.setBody("original body");
    return c;
  }

  // -----------------------------------------------------------------------
  // update() — assertCanModify via issue author / repo owner / stranger
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("update — allowed when requester is issue author")
  void update_allowed_whenRequesterIsAuthor() {
    UUID authorId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    Tenant author = tenant(authorId, "alice");
    Repo r = repo(repoId);
    Issue iss = issue(UUID.randomUUID(), author, r);

    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumber(repoId, 1)).thenReturn(Optional.of(iss));
    when(issueRepository.save(iss)).thenReturn(iss);
    when(commentRepository.countByIssueId(iss.getId())).thenReturn(0);
    when(issueMapper.toDetail(any(), anyInt())).thenReturn(null);

    IssueUpdateForm form = new IssueUpdateForm();
    form.setTitle("Updated");

    issueService.update("alice", "myrepo", 1, form, authorId);
  }

  @Test
  @DisplayName("update — allowed when requester is repo owner")
  void update_allowed_whenRequesterIsRepoOwner() {
    UUID authorId = UUID.randomUUID();
    UUID repoOwnerId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    Tenant author = tenant(authorId, "alice");
    Tenant repoOwner = tenant(repoOwnerId, "owner");
    Repo r = repo(repoId);
    Issue iss = issue(UUID.randomUUID(), author, r);

    when(repoRepository.findByOwnerUsernameAndName("owner", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumber(repoId, 1)).thenReturn(Optional.of(iss));
    when(tenantRepository.findByUsername("owner")).thenReturn(Optional.of(repoOwner));
    when(issueRepository.save(iss)).thenReturn(iss);
    when(commentRepository.countByIssueId(iss.getId())).thenReturn(0);
    when(issueMapper.toDetail(any(), anyInt())).thenReturn(null);

    IssueUpdateForm form = new IssueUpdateForm();
    form.setTitle("Updated");

    issueService.update("owner", "myrepo", 1, form, repoOwnerId);
  }

  @Test
  @DisplayName("update — throws AccessNotAllowedException when requester is stranger")
  void update_throws_whenRequesterIsStranger() {
    UUID authorId = UUID.randomUUID();
    UUID strangerId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    Tenant author = tenant(authorId, "alice");
    Repo r = repo(repoId);
    Issue iss = issue(UUID.randomUUID(), author, r);

    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumber(repoId, 1)).thenReturn(Optional.of(iss));
    when(tenantRepository.findByUsername("alice")).thenReturn(Optional.of(author));

    IssueUpdateForm form = new IssueUpdateForm();
    form.setTitle("Hack");

    assertThatThrownBy(() -> issueService.update("alice", "myrepo", 1, form, strangerId))
        .isInstanceOf(AccessNotAllowedException.class);
  }

  // -----------------------------------------------------------------------
  // delete() — access control + IssueDeletedEvent published (RAPOR2 bug #7)
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("delete — publishes IssueDeletedEvent when author deletes")
  void delete_publishesIssueDeletedEvent_whenAuthorDeletes() {
    UUID authorId = UUID.randomUUID();
    UUID issueId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    Tenant author = tenant(authorId, "alice");
    Repo r = repo(repoId);
    Issue iss = issue(issueId, author, r);

    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumber(repoId, 1)).thenReturn(Optional.of(iss));

    issueService.delete("alice", "myrepo", 1, authorId);

    verify(issueRepository).delete(iss);
    ArgumentCaptor<IssueDeletedEvent> captor = ArgumentCaptor.forClass(IssueDeletedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().issueId()).isEqualTo(issueId);
  }

  @Test
  @DisplayName("delete — throws AccessNotAllowedException when requester is stranger")
  void delete_throws_whenRequesterIsStranger() {
    UUID authorId = UUID.randomUUID();
    UUID strangerId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    Tenant author = tenant(authorId, "alice");
    Repo r = repo(repoId);
    Issue iss = issue(UUID.randomUUID(), author, r);

    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumber(repoId, 1)).thenReturn(Optional.of(iss));
    when(tenantRepository.findByUsername("alice")).thenReturn(Optional.of(author));

    assertThatThrownBy(() -> issueService.delete("alice", "myrepo", 1, strangerId))
        .isInstanceOf(AccessNotAllowedException.class);
  }

  // -----------------------------------------------------------------------
  // updateComment() — comment author / stranger (RAPOR2 bug #4)
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("updateComment — allowed when requester is comment author")
  void updateComment_allowed_whenRequesterIsCommentAuthor() {
    UUID commentAuthorId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    UUID issueId = UUID.randomUUID();
    UUID commentId = UUID.randomUUID();
    Tenant commentAuthor = tenant(commentAuthorId, "alice");
    Repo r = repo(repoId);
    Issue iss = issue(issueId, commentAuthor, r);
    IssueComment c = comment(commentId, commentAuthor, iss);

    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumber(repoId, 1)).thenReturn(Optional.of(iss));
    when(commentRepository.findByIdAndIssueId(commentId, issueId)).thenReturn(Optional.of(c));
    when(commentRepository.save(c)).thenReturn(c);
    when(issueMapper.toCommentInfo(c)).thenReturn(null);

    IssueCommentUpdateForm form = new IssueCommentUpdateForm("new body");

    issueService.updateComment("alice", "myrepo", 1, commentId, form, commentAuthorId);
  }

  @Test
  @DisplayName("updateComment — throws AccessNotAllowedException when requester is stranger")
  void updateComment_throws_whenRequesterIsStranger() {
    UUID commentAuthorId = UUID.randomUUID();
    UUID strangerId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    UUID issueId = UUID.randomUUID();
    UUID commentId = UUID.randomUUID();
    Tenant commentAuthor = tenant(commentAuthorId, "alice");
    Repo r = repo(repoId);
    Issue iss = issue(issueId, commentAuthor, r);
    IssueComment c = comment(commentId, commentAuthor, iss);

    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumber(repoId, 1)).thenReturn(Optional.of(iss));
    when(commentRepository.findByIdAndIssueId(commentId, issueId)).thenReturn(Optional.of(c));
    when(tenantRepository.findByUsername("alice")).thenReturn(Optional.of(commentAuthor));

    assertThatThrownBy(
            () ->
                issueService.updateComment(
                    "alice",
                    "myrepo",
                    1,
                    commentId,
                    new IssueCommentUpdateForm("hack"),
                    strangerId))
        .isInstanceOf(AccessNotAllowedException.class);
  }

  // -----------------------------------------------------------------------
  // deleteComment() — comment author / stranger (RAPOR2 bug #4)
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("deleteComment — allowed when requester is comment author")
  void deleteComment_allowed_whenRequesterIsCommentAuthor() {
    UUID commentAuthorId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    UUID issueId = UUID.randomUUID();
    UUID commentId = UUID.randomUUID();
    Tenant commentAuthor = tenant(commentAuthorId, "alice");
    Repo r = repo(repoId);
    Issue iss = issue(issueId, commentAuthor, r);
    IssueComment c = comment(commentId, commentAuthor, iss);

    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumber(repoId, 1)).thenReturn(Optional.of(iss));
    when(commentRepository.findByIdAndIssueId(commentId, issueId)).thenReturn(Optional.of(c));

    issueService.deleteComment("alice", "myrepo", 1, commentId, commentAuthorId);

    verify(commentRepository).delete(c);
  }

  @Test
  @DisplayName("deleteComment — throws AccessNotAllowedException when requester is stranger")
  void deleteComment_throws_whenRequesterIsStranger() {
    UUID commentAuthorId = UUID.randomUUID();
    UUID strangerId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    UUID issueId = UUID.randomUUID();
    UUID commentId = UUID.randomUUID();
    Tenant commentAuthor = tenant(commentAuthorId, "alice");
    Repo r = repo(repoId);
    Issue iss = issue(issueId, commentAuthor, r);
    IssueComment c = comment(commentId, commentAuthor, iss);

    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumber(repoId, 1)).thenReturn(Optional.of(iss));
    when(commentRepository.findByIdAndIssueId(commentId, issueId)).thenReturn(Optional.of(c));
    when(tenantRepository.findByUsername("alice")).thenReturn(Optional.of(commentAuthor));

    assertThatThrownBy(
            () -> issueService.deleteComment("alice", "myrepo", 1, commentId, strangerId))
        .isInstanceOf(AccessNotAllowedException.class);
  }
}
