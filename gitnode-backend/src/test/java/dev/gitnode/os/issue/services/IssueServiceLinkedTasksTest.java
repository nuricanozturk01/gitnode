package dev.gitnode.os.issue.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.issue.api.LinkedTaskData;
import dev.gitnode.os.issue.api.TaskQueryPort;
import dev.gitnode.os.issue.dtos.IssueLinkedTaskInfo;
import dev.gitnode.os.issue.entities.Issue;
import dev.gitnode.os.issue.mappers.IssueMapper;
import dev.gitnode.os.issue.repositories.IssueCommentRepository;
import dev.gitnode.os.issue.repositories.IssueRepository;
import dev.gitnode.os.repo.api.RepoCommandPort;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.util.List;
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
@DisplayName("IssueService — getLinkedTasks")
class IssueServiceLinkedTasksTest {

  @Mock private IssueRepository issueRepository;
  @Mock private IssueCommentRepository commentRepository;
  @Mock private RepoLookupPort repoRepository;
  @Mock private RepoCommandPort repoCommands;
  @Mock private TenantQueryPort tenantRepository;
  @Mock private IssueMapper issueMapper;
  @Mock private TaskQueryPort taskQueryPort;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private IssueService issueService;

  // -----------------------------------------------------------------------
  // helpers
  // -----------------------------------------------------------------------

  private static RepoRef repo(UUID id, String owner, String name) {
    return new RepoRef(id, UUID.randomUUID(), owner, name, false);
  }

  private static Issue issue(UUID id, RepoRef repo, int number) {
    Issue i = new Issue();
    i.setId(id);
    i.setRepoId(repo.id());
    i.setNumber(number);
    i.setTitle("Issue #" + number);
    i.setStatus("OPEN");
    return i;
  }

  private static LinkedTaskData taskData(
      String code, String title, String status, String projectCode, String projectName) {
    return new LinkedTaskData(code, title, status, projectCode, projectName, "project-owner");
  }

  // -----------------------------------------------------------------------
  // happy path
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("returns mapped IssueLinkedTaskInfo for each task linked to the issue")
  void getLinkedTasks_returnsMappedList_whenTasksExist() {
    UUID repoId = UUID.randomUUID();
    UUID issueId = UUID.randomUUID();
    RepoRef r = repo(repoId, "alice", "myrepo");
    Issue iss = issue(issueId, r, 1);
    LinkedTaskData t1 = taskData("OH-1", "Fix login bug", "IN_PROGRESS", "OH", "GitNode");
    LinkedTaskData t2 = taskData("OH-3", "Add dark mode", "NOT_STARTED", "OH", "GitNode");

    when(repoRepository.find("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumber(repoId, 1)).thenReturn(Optional.of(iss));
    when(taskQueryPort.findByLinkedIssueId(issueId, null)).thenReturn(List.of(t1, t2));

    List<IssueLinkedTaskInfo> result = issueService.getLinkedTasks("alice", "myrepo", 1, null);

    assertThat(result).hasSize(2);

    IssueLinkedTaskInfo first = result.get(0);
    assertThat(first.taskCode()).isEqualTo("OH-1");
    assertThat(first.taskTitle()).isEqualTo("Fix login bug");
    assertThat(first.taskStatus()).isEqualTo("IN_PROGRESS");
    assertThat(first.projectCode()).isEqualTo("OH");
    assertThat(first.projectName()).isEqualTo("GitNode");
    assertThat(first.projectOwner()).isEqualTo("project-owner");

    IssueLinkedTaskInfo second = result.get(1);
    assertThat(second.taskCode()).isEqualTo("OH-3");
    assertThat(second.taskStatus()).isEqualTo("NOT_STARTED");
  }

  @Test
  @DisplayName("returns empty list when no tasks are linked to the issue")
  void getLinkedTasks_returnsEmptyList_whenNoTasksLinked() {
    UUID repoId = UUID.randomUUID();
    UUID issueId = UUID.randomUUID();
    RepoRef r = repo(repoId, "bob", "repo");
    Issue iss = issue(issueId, r, 5);

    when(repoRepository.find("bob", "repo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumber(repoId, 5)).thenReturn(Optional.of(iss));
    when(taskQueryPort.findByLinkedIssueId(issueId, null)).thenReturn(List.of());

    List<IssueLinkedTaskInfo> result = issueService.getLinkedTasks("bob", "repo", 5, null);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("includes tasks from multiple different projects")
  void getLinkedTasks_supportsTasksFromDifferentProjects() {
    UUID repoId = UUID.randomUUID();
    UUID issueId = UUID.randomUUID();
    RepoRef r = repo(repoId, "alice", "myrepo");
    Issue iss = issue(issueId, r, 2);
    LinkedTaskData tA = taskData("AAA-1", "Alpha task", "COMPLETED", "AAA", "Project Alpha");
    LinkedTaskData tB = taskData("BB-7", "Beta task", "IN_PROGRESS", "BB", "Project Beta");

    when(repoRepository.find("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumber(repoId, 2)).thenReturn(Optional.of(iss));
    when(taskQueryPort.findByLinkedIssueId(issueId, null)).thenReturn(List.of(tA, tB));

    List<IssueLinkedTaskInfo> result = issueService.getLinkedTasks("alice", "myrepo", 2, null);

    assertThat(result).extracting(IssueLinkedTaskInfo::projectCode).containsExactly("AAA", "BB");
  }

  // -----------------------------------------------------------------------
  // error cases
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("throws ItemNotFoundException when repository not found")
  void getLinkedTasks_throws_whenRepoNotFound() {
    when(repoRepository.find("nobody", "missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> issueService.getLinkedTasks("nobody", "missing", 1, null))
        .isInstanceOf(ItemNotFoundException.class);
  }

  @Test
  @DisplayName("throws ItemNotFoundException when issue number not found in repo")
  void getLinkedTasks_throws_whenIssueNotFound() {
    UUID repoId = UUID.randomUUID();
    RepoRef r = repo(repoId, "alice", "myrepo");

    when(repoRepository.find("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumber(repoId, 999)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> issueService.getLinkedTasks("alice", "myrepo", 999, null))
        .isInstanceOf(ItemNotFoundException.class);
  }

  @Test
  @DisplayName("mapped info preserves CLOSED task status correctly")
  void getLinkedTasks_preservesClosedStatus() {
    UUID repoId = UUID.randomUUID();
    UUID issueId = UUID.randomUUID();
    RepoRef r = repo(repoId, "alice", "myrepo");
    Issue iss = issue(issueId, r, 3);
    LinkedTaskData t = taskData("ZZ-99", "Old task", "COMPLETED", "ZZ", "Done Project");

    when(repoRepository.find("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumber(repoId, 3)).thenReturn(Optional.of(iss));
    when(taskQueryPort.findByLinkedIssueId(issueId, null)).thenReturn(List.of(t));

    List<IssueLinkedTaskInfo> result = issueService.getLinkedTasks("alice", "myrepo", 3, null);

    assertThat(result)
        .singleElement()
        .satisfies(
            info -> {
              assertThat(info.taskStatus()).isEqualTo("COMPLETED");
              assertThat(info.taskCode()).isEqualTo("ZZ-99");
            });
  }
}
