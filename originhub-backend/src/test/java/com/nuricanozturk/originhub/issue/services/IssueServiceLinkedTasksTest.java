package com.nuricanozturk.originhub.issue.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.issue.dtos.IssueLinkedTaskInfo;
import com.nuricanozturk.originhub.issue.entities.Issue;
import com.nuricanozturk.originhub.issue.mappers.IssueMapper;
import com.nuricanozturk.originhub.issue.repositories.IssueCommentRepository;
import com.nuricanozturk.originhub.issue.repositories.IssueRepository;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import com.nuricanozturk.originhub.task.entities.Project;
import com.nuricanozturk.originhub.task.entities.Task;
import com.nuricanozturk.originhub.task.repositories.TaskRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("IssueService — getLinkedTasks")
class IssueServiceLinkedTasksTest {

  @Mock private IssueRepository issueRepository;
  @Mock private IssueCommentRepository commentRepository;
  @Mock private RepoRepository repoRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private IssueMapper issueMapper;
  @Mock private TaskRepository taskRepository;

  @InjectMocks private IssueService issueService;

  // -----------------------------------------------------------------------
  // helpers
  // -----------------------------------------------------------------------

  private static Repo repo(UUID id, String owner, String name) {
    Repo r = new Repo();
    r.setId(id);
    r.setName(name);
    return r;
  }

  private static Issue issue(UUID id, Repo repo, int number) {
    Issue i = new Issue();
    i.setId(id);
    i.setRepo(repo);
    i.setNumber(number);
    i.setTitle("Issue #" + number);
    i.setStatus("OPEN");
    return i;
  }

  private static Task task(String code, String title, String status, Project project) {
    Task t = new Task();
    t.setId(UUID.randomUUID());
    t.setCode(code);
    t.setTitle(title);
    t.setStatus(status);
    t.setProject(project);
    return t;
  }

  private static Project project(String codePrefix, String name) {
    Project p = new Project();
    p.setId(UUID.randomUUID());
    p.setCodePrefix(codePrefix);
    p.setName(name);
    return p;
  }

  // -----------------------------------------------------------------------
  // happy path
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("returns mapped IssueLinkedTaskInfo for each task linked to the issue")
  void getLinkedTasks_returnsMappedList_whenTasksExist() {
    UUID repoId = UUID.randomUUID();
    UUID issueId = UUID.randomUUID();
    Repo r = repo(repoId, "alice", "myrepo");
    Issue iss = issue(issueId, r, 1);
    Project proj = project("OH", "OriginHub");
    Task t1 = task("OH-1", "Fix login bug", "IN_PROGRESS", proj);
    Task t2 = task("OH-3", "Add dark mode", "NOT_STARTED", proj);

    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumber(repoId, 1)).thenReturn(Optional.of(iss));
    when(taskRepository.findByLinkedIssueId(issueId)).thenReturn(List.of(t1, t2));

    List<IssueLinkedTaskInfo> result = issueService.getLinkedTasks("alice", "myrepo", 1);

    assertThat(result).hasSize(2);

    IssueLinkedTaskInfo first = result.get(0);
    assertThat(first.taskCode()).isEqualTo("OH-1");
    assertThat(first.taskTitle()).isEqualTo("Fix login bug");
    assertThat(first.taskStatus()).isEqualTo("IN_PROGRESS");
    assertThat(first.projectCode()).isEqualTo("OH");
    assertThat(first.projectName()).isEqualTo("OriginHub");

    IssueLinkedTaskInfo second = result.get(1);
    assertThat(second.taskCode()).isEqualTo("OH-3");
    assertThat(second.taskStatus()).isEqualTo("NOT_STARTED");
  }

  @Test
  @DisplayName("returns empty list when no tasks are linked to the issue")
  void getLinkedTasks_returnsEmptyList_whenNoTasksLinked() {
    UUID repoId = UUID.randomUUID();
    UUID issueId = UUID.randomUUID();
    Repo r = repo(repoId, "bob", "repo");
    Issue iss = issue(issueId, r, 5);

    when(repoRepository.findByOwnerUsernameAndName("bob", "repo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumber(repoId, 5)).thenReturn(Optional.of(iss));
    when(taskRepository.findByLinkedIssueId(issueId)).thenReturn(List.of());

    List<IssueLinkedTaskInfo> result = issueService.getLinkedTasks("bob", "repo", 5);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("includes tasks from multiple different projects")
  void getLinkedTasks_supportsTasksFromDifferentProjects() {
    UUID repoId = UUID.randomUUID();
    UUID issueId = UUID.randomUUID();
    Repo r = repo(repoId, "alice", "myrepo");
    Issue iss = issue(issueId, r, 2);
    Project projA = project("AAA", "Project Alpha");
    Project projB = project("BB", "Project Beta");
    Task tA = task("AAA-1", "Alpha task", "COMPLETED", projA);
    Task tB = task("BB-7", "Beta task", "IN_PROGRESS", projB);

    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumber(repoId, 2)).thenReturn(Optional.of(iss));
    when(taskRepository.findByLinkedIssueId(issueId)).thenReturn(List.of(tA, tB));

    List<IssueLinkedTaskInfo> result = issueService.getLinkedTasks("alice", "myrepo", 2);

    assertThat(result).extracting(IssueLinkedTaskInfo::projectCode).containsExactly("AAA", "BB");
  }

  // -----------------------------------------------------------------------
  // error cases
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("throws ItemNotFoundException when repository not found")
  void getLinkedTasks_throws_whenRepoNotFound() {
    when(repoRepository.findByOwnerUsernameAndName("nobody", "missing"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> issueService.getLinkedTasks("nobody", "missing", 1))
        .isInstanceOf(ItemNotFoundException.class);
  }

  @Test
  @DisplayName("throws ItemNotFoundException when issue number not found in repo")
  void getLinkedTasks_throws_whenIssueNotFound() {
    UUID repoId = UUID.randomUUID();
    Repo r = repo(repoId, "alice", "myrepo");

    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumber(repoId, 999)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> issueService.getLinkedTasks("alice", "myrepo", 999))
        .isInstanceOf(ItemNotFoundException.class);
  }

  @Test
  @DisplayName("mapped info preserves CLOSED task status correctly")
  void getLinkedTasks_preservesClosedStatus() {
    UUID repoId = UUID.randomUUID();
    UUID issueId = UUID.randomUUID();
    Repo r = repo(repoId, "alice", "myrepo");
    Issue iss = issue(issueId, r, 3);
    Project proj = project("ZZ", "Done Project");
    Task t = task("ZZ-99", "Old task", "COMPLETED", proj);

    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));
    when(issueRepository.findByRepoIdAndNumber(repoId, 3)).thenReturn(Optional.of(iss));
    when(taskRepository.findByLinkedIssueId(issueId)).thenReturn(List.of(t));

    List<IssueLinkedTaskInfo> result = issueService.getLinkedTasks("alice", "myrepo", 3);

    assertThat(result)
        .singleElement()
        .satisfies(
            info -> {
              assertThat(info.taskStatus()).isEqualTo("COMPLETED");
              assertThat(info.taskCode()).isEqualTo("ZZ-99");
            });
  }
}
