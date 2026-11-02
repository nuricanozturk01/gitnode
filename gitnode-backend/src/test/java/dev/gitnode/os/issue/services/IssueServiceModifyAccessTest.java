package dev.gitnode.os.issue.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.events.issue.IssueDeletedEvent;
import dev.gitnode.os.issue.api.TaskQueryPort;
import dev.gitnode.os.issue.dtos.IssueCommentUpdateForm;
import dev.gitnode.os.issue.dtos.IssueUpdateForm;
import dev.gitnode.os.issue.entities.Issue;
import dev.gitnode.os.issue.entities.IssueComment;
import dev.gitnode.os.issue.entities.IssueLabel;
import dev.gitnode.os.issue.entities.IssueMilestone;
import dev.gitnode.os.issue.mappers.IssueMapper;
import dev.gitnode.os.issue.repositories.IssueCommentRepository;
import dev.gitnode.os.issue.repositories.IssueLabelRepository;
import dev.gitnode.os.issue.repositories.IssueMilestoneRepository;
import dev.gitnode.os.issue.repositories.IssueRepository;
import dev.gitnode.os.shared.collaborator.services.CollaboratorAccessPort;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.repo.entities.Repo;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
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
  @Mock private IssueLabelRepository labelRepository;
  @Mock private IssueMilestoneRepository milestoneRepository;
  @Mock private RepoRepository repoRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private IssueMapper issueMapper;
  @Mock private TaskQueryPort taskQueryPort;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private CollaboratorAccessPort collaboratorAccessPort;

  @InjectMocks private IssueService issueService;

  @Test
  void updateAssignsMilestoneOnlyFromIssueRepository() {
    var authorId = UUID.randomUUID();
    var repoId = UUID.randomUUID();
    var author = tenant(authorId, "alice");
    var issue = issue(UUID.randomUUID(), author, repo(repoId));
    var milestone = new IssueMilestone();
    milestone.setId(UUID.randomUUID());
    var form = new IssueUpdateForm();
    form.setMilestoneId(milestone.getId());
    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo"))
        .thenReturn(Optional.of(issue.getRepo()));
    when(issueRepository.findByRepoIdAndNumberForUpdate(repoId, 1)).thenReturn(Optional.of(issue));
    when(milestoneRepository.findByIdAndRepoId(milestone.getId(), repoId))
        .thenReturn(Optional.of(milestone));
    when(issueRepository.save(issue)).thenReturn(issue);
    issueService.update("alice", "myrepo", 1, form, authorId);
    assertThat(issue.getMilestone()).isSameAs(milestone);
  }

  @Test
  void updateRejectsMilestoneFromAnotherRepository() {
    var authorId = UUID.randomUUID();
    var repoId = UUID.randomUUID();
    var issue = issue(UUID.randomUUID(), tenant(authorId, "alice"), repo(repoId));
    var form = new IssueUpdateForm();
    form.setMilestoneId(UUID.randomUUID());
    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo"))
        .thenReturn(Optional.of(issue.getRepo()));
    when(issueRepository.findByRepoIdAndNumberForUpdate(repoId, 1)).thenReturn(Optional.of(issue));
    assertThatThrownBy(() -> issueService.update("alice", "myrepo", 1, form, authorId))
        .isInstanceOf(ItemNotFoundException.class);
  }

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
    when(issueRepository.findByRepoIdAndNumberForUpdate(repoId, 1)).thenReturn(Optional.of(iss));
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
    when(issueRepository.findByRepoIdAndNumberForUpdate(repoId, 1)).thenReturn(Optional.of(iss));
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
    when(issueRepository.findByRepoIdAndNumberForUpdate(repoId, 1)).thenReturn(Optional.of(iss));
    when(tenantRepository.findByUsername("alice")).thenReturn(Optional.of(author));

    IssueUpdateForm form = new IssueUpdateForm();
    form.setTitle("Hack");

    assertThatThrownBy(() -> issueService.update("alice", "myrepo", 1, form, strangerId))
        .isInstanceOf(AccessNotAllowedException.class);
  }

  @Test
  void update_canAssignAndClearWithoutChangingAssignmentOnUnrelatedUpdates() {
    UUID authorId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    Tenant author = tenant(authorId, "alice");
    Tenant assignee = tenant(UUID.randomUUID(), "bob");
    Repo r = repo(repoId);
    Issue iss = issue(UUID.randomUUID(), author, r);
    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumberForUpdate(repoId, 1)).thenReturn(Optional.of(iss));
    when(issueRepository.save(iss)).thenReturn(iss);
    when(tenantRepository.findById(assignee.getId())).thenReturn(Optional.of(assignee));

    IssueUpdateForm assign = new IssueUpdateForm();
    assign.setAssigneeId(assignee.getId());
    issueService.update("alice", "myrepo", 1, assign, authorId);
    assertThat(iss.getAssignee()).isSameAs(assignee);

    IssueUpdateForm title = new IssueUpdateForm();
    title.setTitle("Updated");
    issueService.update("alice", "myrepo", 1, title, authorId);
    assertThat(iss.getAssignee()).isSameAs(assignee);

    IssueUpdateForm clear = new IssueUpdateForm();
    clear.setClearAssignee(true);
    issueService.update("alice", "myrepo", 1, clear, authorId);
    assertThat(iss.getAssignee()).isNull();
    assertThat(iss.getAssignees()).isEmpty();
  }

  @Test
  void update_rejectsConflictingAssignmentOperations() {
    UUID authorId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    Tenant author = tenant(authorId, "alice");
    Repo r = repo(repoId);
    Issue iss = issue(UUID.randomUUID(), author, r);
    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumberForUpdate(repoId, 1)).thenReturn(Optional.of(iss));
    IssueUpdateForm form = new IssueUpdateForm();
    form.setAssigneeId(UUID.randomUUID());
    form.setClearAssignee(true);

    assertThatThrownBy(() -> issueService.update("alice", "myrepo", 1, form, authorId))
        .isInstanceOf(BadRequestException.class);
    verify(issueRepository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  void update_assignsAnotherUserByUsername() {
    UUID authorId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    Tenant author = tenant(authorId, "alice");
    Tenant assignee = tenant(UUID.randomUUID(), "bob");
    Repo r = repo(repoId);
    Issue iss = issue(UUID.randomUUID(), author, r);
    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumberForUpdate(repoId, 1)).thenReturn(Optional.of(iss));
    when(tenantRepository.findByUsername("bob")).thenReturn(Optional.of(assignee));
    when(issueRepository.save(iss)).thenReturn(iss);

    IssueUpdateForm form = new IssueUpdateForm();
    form.setAssigneeUsername(" bob ");
    issueService.update("alice", "myrepo", 1, form, authorId);
    assertThat(iss.getAssignee()).isSameAs(assignee);
  }

  @Test
  void update_rejectsUsernameTogetherWithOtherAssignmentOperations() {
    UUID authorId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    Tenant author = tenant(authorId, "alice");
    Repo r = repo(repoId);
    Issue iss = issue(UUID.randomUUID(), author, r);
    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumberForUpdate(repoId, 1)).thenReturn(Optional.of(iss));
    IssueUpdateForm form = new IssueUpdateForm();
    form.setAssigneeUsername("bob");
    form.setAssigneeId(UUID.randomUUID());

    assertThatThrownBy(() -> issueService.update("alice", "myrepo", 1, form, authorId))
        .isInstanceOf(BadRequestException.class);
    verify(issueRepository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  void update_rejectsUnknownAssigneeUsername() {
    UUID authorId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    Tenant author = tenant(authorId, "alice");
    Repo r = repo(repoId);
    Issue iss = issue(UUID.randomUUID(), author, r);
    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumberForUpdate(repoId, 1)).thenReturn(Optional.of(iss));
    when(tenantRepository.findByUsername("missing")).thenReturn(Optional.empty());
    IssueUpdateForm form = new IssueUpdateForm();
    form.setAssigneeUsername("missing");

    assertThatThrownBy(() -> issueService.update("alice", "myrepo", 1, form, authorId))
        .isInstanceOf(ItemNotFoundException.class);
    verify(issueRepository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  void update_addsMultipleAssigneesAndRemovesOnlySelectedUser() {
    UUID authorId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    Tenant author = tenant(authorId, "alice");
    Tenant bob = tenant(UUID.randomUUID(), "bob");
    Tenant carol = tenant(UUID.randomUUID(), "carol");
    Repo r = repo(repoId);
    Issue iss = issue(UUID.randomUUID(), author, r);
    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumberForUpdate(repoId, 1)).thenReturn(Optional.of(iss));
    when(tenantRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
    when(tenantRepository.findByUsername("carol")).thenReturn(Optional.of(carol));
    when(issueRepository.save(iss)).thenReturn(iss);

    IssueUpdateForm addBob = new IssueUpdateForm();
    addBob.setAddAssigneeUsername("bob");
    issueService.update("alice", "myrepo", 1, addBob, authorId);
    issueService.update("alice", "myrepo", 1, addBob, authorId);
    IssueUpdateForm addCarol = new IssueUpdateForm();
    addCarol.setAddAssigneeUsername("carol");
    issueService.update("alice", "myrepo", 1, addCarol, authorId);
    assertThat(iss.getAssignees()).containsExactlyInAnyOrder(bob, carol);
    assertThat(iss.getAssignee()).isSameAs(bob);

    IssueUpdateForm removeBob = new IssueUpdateForm();
    removeBob.setRemoveAssigneeUsername("bob");
    issueService.update("alice", "myrepo", 1, removeBob, authorId);
    assertThat(iss.getAssignees()).containsExactly(carol);
    assertThat(iss.getAssignee()).isSameAs(carol);
  }

  @Test
  void update_rejectsUnauthorizedMultiAssigneeChange() {
    UUID authorId = UUID.randomUUID();
    UUID strangerId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    Tenant author = tenant(authorId, "alice");
    Repo r = repo(repoId);
    Issue iss = issue(UUID.randomUUID(), author, r);
    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumberForUpdate(repoId, 1)).thenReturn(Optional.of(iss));
    when(tenantRepository.findByUsername("alice")).thenReturn(Optional.of(author));
    IssueUpdateForm form = new IssueUpdateForm();
    form.setAddAssigneeUsername("bob");

    assertThatThrownBy(() -> issueService.update("alice", "myrepo", 1, form, strangerId))
        .isInstanceOf(AccessNotAllowedException.class);
    verify(tenantRepository, org.mockito.Mockito.never()).findByUsername("bob");
    verify(issueRepository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  void updateAddsAndRemovesRepositoryLabel() {
    UUID authorId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    Tenant author = tenant(authorId, "alice");
    Repo r = repo(repoId);
    Issue iss = issue(UUID.randomUUID(), author, r);
    IssueLabel label = new IssueLabel();
    label.setId(UUID.randomUUID());
    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumberForUpdate(repoId, 1)).thenReturn(Optional.of(iss));
    when(labelRepository.findByIdAndRepoId(label.getId(), repoId)).thenReturn(Optional.of(label));
    when(issueRepository.save(iss)).thenReturn(iss);
    IssueUpdateForm add = new IssueUpdateForm();
    add.setAddLabelId(label.getId());
    issueService.update("alice", "myrepo", 1, add, authorId);
    assertThat(iss.getLabels()).containsExactly(label);
    IssueUpdateForm remove = new IssueUpdateForm();
    remove.setRemoveLabelId(label.getId());
    issueService.update("alice", "myrepo", 1, remove, authorId);
    assertThat(iss.getLabels()).isEmpty();
  }

  @Test
  void updateRejectsLabelFromAnotherRepository() {
    UUID authorId = UUID.randomUUID();
    UUID repoId = UUID.randomUUID();
    UUID labelId = UUID.randomUUID();
    Tenant author = tenant(authorId, "alice");
    Repo r = repo(repoId);
    Issue iss = issue(UUID.randomUUID(), author, r);
    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumberForUpdate(repoId, 1)).thenReturn(Optional.of(iss));
    when(labelRepository.findByIdAndRepoId(labelId, repoId)).thenReturn(Optional.empty());
    IssueUpdateForm form = new IssueUpdateForm();
    form.setAddLabelId(labelId);
    assertThatThrownBy(() -> issueService.update("alice", "myrepo", 1, form, authorId))
        .isInstanceOf(ItemNotFoundException.class);
    verify(issueRepository, org.mockito.Mockito.never()).save(any());
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
