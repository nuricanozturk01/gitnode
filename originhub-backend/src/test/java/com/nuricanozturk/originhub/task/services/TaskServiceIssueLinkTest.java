package com.nuricanozturk.originhub.task.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.issue.entities.Issue;
import com.nuricanozturk.originhub.issue.repositories.IssueRepository;
import com.nuricanozturk.originhub.pr.repositories.PrRepository;
import com.nuricanozturk.originhub.shared.branch.services.BranchProtocolService;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import com.nuricanozturk.originhub.task.dtos.LinkedIssueInfo;
import com.nuricanozturk.originhub.task.dtos.TaskDetail;
import com.nuricanozturk.originhub.task.dtos.TaskForm;
import com.nuricanozturk.originhub.task.dtos.TaskUpdateForm;
import com.nuricanozturk.originhub.task.entities.BoardColumn;
import com.nuricanozturk.originhub.task.entities.Project;
import com.nuricanozturk.originhub.task.entities.Task;
import com.nuricanozturk.originhub.task.mappers.ProjectMapper;
import com.nuricanozturk.originhub.task.mappers.TaskMapper;
import com.nuricanozturk.originhub.task.repositories.BoardColumnRepository;
import com.nuricanozturk.originhub.task.repositories.ProjectRepository;
import com.nuricanozturk.originhub.task.repositories.SubtaskRepository;
import com.nuricanozturk.originhub.task.repositories.TaskRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaskService — issue linking")
class TaskServiceIssueLinkTest {

  @Mock private TaskRepository taskRepository;
  @Mock private SubtaskRepository subtaskRepository;
  @Mock private BoardColumnRepository boardColumnRepository;
  @Mock private ProjectRepository projectRepository;
  @Mock private RepoRepository repoRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private PrRepository prRepository;
  @Mock private IssueRepository issueRepository;
  @Mock private ProjectService projectService;
  @Mock private TaskMapper taskMapper;
  @Mock private ProjectMapper projectMapper;
  @Mock private BranchProtocolService branchProtocolService;

  @InjectMocks private TaskService taskService;

  // -----------------------------------------------------------------------
  // helpers
  // -----------------------------------------------------------------------

  private static Project project(String codePrefix, long seq) {
    Project p = new Project();
    p.setId(UUID.randomUUID());
    p.setCodePrefix(codePrefix);
    p.setName("Test Project");
    p.setTaskSeq(seq);
    return p;
  }

  private static BoardColumn column() {
    BoardColumn c = new BoardColumn();
    c.setId(UUID.randomUUID());
    c.setName("Backlog");
    return c;
  }

  private static Issue issue(UUID id) {
    Issue i = new Issue();
    i.setId(id);
    i.setNumber(1);
    i.setTitle("Sample issue");
    i.setStatus("OPEN");
    return i;
  }

  private static Task savedTask(Project project, BoardColumn col) {
    Task t = new Task();
    t.setId(UUID.randomUUID());
    t.setCode(project.getCodePrefix() + "-" + project.getTaskSeq());
    t.setTitle("Task");
    t.setStatus("NOT_STARTED");
    t.setType("TASK");
    t.setPosition(0);
    t.setProject(project);
    t.setBoardColumn(col);
    return t;
  }

  private static TaskDetail stubDetail(Task task, LinkedIssueInfo linkedIssueInfo) {
    return TaskDetail.builder()
        .id(task.getId())
        .code(task.getCode())
        .title(task.getTitle())
        .status(task.getStatus())
        .type(task.getType())
        .position(0)
        .boardColumnId(task.getBoardColumn().getId())
        .linkedIssue(linkedIssueInfo)
        .subtasks(List.of())
        .build();
  }

  // -----------------------------------------------------------------------
  // create() with linkedIssueId
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("create — links issue when linkedIssueId is provided")
  void create_linksIssue_whenLinkedIssueIdProvided() {
    UUID issueId = UUID.randomUUID();
    UUID columnId = UUID.randomUUID();
    Project proj = project("OH", 1L);
    BoardColumn col = column();
    col.setId(columnId);
    Issue iss = issue(issueId);
    Task saved = savedTask(proj, col);
    saved.setLinkedIssue(iss);

    when(projectService.findProject("alice", "OH")).thenReturn(proj);
    when(boardColumnRepository.findById(columnId)).thenReturn(Optional.of(col));
    when(projectRepository.findById(proj.getId())).thenReturn(Optional.of(proj));
    when(issueRepository.findById(issueId)).thenReturn(Optional.of(iss));
    when(taskRepository.save(any(Task.class))).thenReturn(saved);
    when(subtaskRepository.findAllByTaskIdOrderByPositionAsc(saved.getId())).thenReturn(List.of());

    LinkedIssueInfo linkedInfo =
        LinkedIssueInfo.builder()
            .id(issueId)
            .number(1)
            .title("Sample issue")
            .status("OPEN")
            .build();
    when(taskMapper.toDetail(any(), any(), any(), any())).thenReturn(stubDetail(saved, linkedInfo));

    TaskForm form = new TaskForm();
    form.setTitle("Task");
    form.setBoardColumnId(columnId);
    form.setType("TASK");
    form.setLinkedIssueId(issueId);

    TaskDetail result = taskService.create("alice", "OH", form);

    assertThat(result.linkedIssue()).isNotNull();
    assertThat(result.linkedIssue().id()).isEqualTo(issueId);

    ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
    verify(taskRepository).save(captor.capture());
    assertThat(captor.getValue().getLinkedIssue()).isSameAs(iss);
  }

  @Test
  @DisplayName("create — does not link issue when linkedIssueId is null")
  void create_doesNotLinkIssue_whenLinkedIssueIdNull() {
    UUID columnId = UUID.randomUUID();
    Project proj = project("OH", 1L);
    BoardColumn col = column();
    col.setId(columnId);
    Task saved = savedTask(proj, col);

    when(projectService.findProject("alice", "OH")).thenReturn(proj);
    when(boardColumnRepository.findById(columnId)).thenReturn(Optional.of(col));
    when(projectRepository.findById(proj.getId())).thenReturn(Optional.of(proj));
    when(taskRepository.save(any(Task.class))).thenReturn(saved);
    when(subtaskRepository.findAllByTaskIdOrderByPositionAsc(saved.getId())).thenReturn(List.of());
    when(taskMapper.toDetail(any(), any(), any(), any())).thenReturn(stubDetail(saved, null));

    TaskForm form = new TaskForm();
    form.setTitle("Task");
    form.setBoardColumnId(columnId);
    form.setType("TASK");
    // linkedIssueId is null

    TaskDetail result = taskService.create("alice", "OH", form);

    assertThat(result.linkedIssue()).isNull();
    verify(issueRepository, never()).findById(any());

    ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
    verify(taskRepository).save(captor.capture());
    assertThat(captor.getValue().getLinkedIssue()).isNull();
  }

  @Test
  @DisplayName("create — throws ItemNotFoundException when linkedIssueId does not exist")
  void create_throws_whenLinkedIssueNotFound() {
    UUID issueId = UUID.randomUUID();
    UUID columnId = UUID.randomUUID();
    Project proj = project("OH", 1L);
    BoardColumn col = column();
    col.setId(columnId);

    when(projectService.findProject("alice", "OH")).thenReturn(proj);
    when(boardColumnRepository.findById(columnId)).thenReturn(Optional.of(col));
    when(projectRepository.findById(proj.getId())).thenReturn(Optional.of(proj));
    when(issueRepository.findById(issueId)).thenReturn(Optional.empty());

    TaskForm form = new TaskForm();
    form.setTitle("Task");
    form.setBoardColumnId(columnId);
    form.setType("TASK");
    form.setLinkedIssueId(issueId);

    assertThatThrownBy(() -> taskService.create("alice", "OH", form))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("Issue not found");
  }

  // -----------------------------------------------------------------------
  // update() — link / unlink issue
  // -----------------------------------------------------------------------

  private Task existingTask(Project proj, BoardColumn col) {
    Task t = new Task();
    t.setId(UUID.randomUUID());
    t.setCode("OH-1");
    t.setTitle("Existing");
    t.setStatus("NOT_STARTED");
    t.setType("TASK");
    t.setPosition(0);
    t.setProject(proj);
    t.setBoardColumn(col);
    return t;
  }

  @Test
  @DisplayName("update — links issue when linkedIssueId provided and unlinkIssue is false")
  void update_linksIssue_whenLinkedIssueIdProvided() {
    UUID issueId = UUID.randomUUID();
    Project proj = project("OH", 1L);
    BoardColumn col = column();
    Task task = existingTask(proj, col);
    Issue iss = issue(issueId);

    when(projectService.findProject("alice", "OH")).thenReturn(proj);
    when(taskRepository.findByProjectIdAndCode(proj.getId(), "OH-1")).thenReturn(Optional.of(task));
    when(issueRepository.findById(issueId)).thenReturn(Optional.of(iss));
    when(taskRepository.save(task)).thenReturn(task);
    when(subtaskRepository.findAllByTaskIdOrderByPositionAsc(task.getId())).thenReturn(List.of());

    LinkedIssueInfo linkedInfo =
        LinkedIssueInfo.builder()
            .id(issueId)
            .number(1)
            .title("Sample issue")
            .status("OPEN")
            .build();
    when(taskMapper.toDetail(any(), any(), any(), any())).thenReturn(stubDetail(task, linkedInfo));

    TaskUpdateForm form = new TaskUpdateForm();
    form.setLinkedIssueId(issueId);

    TaskDetail result = taskService.update("alice", "OH", "OH-1", form);

    assertThat(task.getLinkedIssue()).isSameAs(iss);
    assertThat(result.linkedIssue()).isNotNull();
    assertThat(result.linkedIssue().id()).isEqualTo(issueId);
  }

  @Test
  @DisplayName("update — unlinks issue when unlinkIssue is true")
  void update_unlinksIssue_whenUnlinkIssueTrue() {
    Project proj = project("OH", 1L);
    BoardColumn col = column();
    Task task = existingTask(proj, col);
    task.setLinkedIssue(issue(UUID.randomUUID())); // already linked

    when(projectService.findProject("alice", "OH")).thenReturn(proj);
    when(taskRepository.findByProjectIdAndCode(proj.getId(), "OH-1")).thenReturn(Optional.of(task));
    when(taskRepository.save(task)).thenReturn(task);
    when(subtaskRepository.findAllByTaskIdOrderByPositionAsc(task.getId())).thenReturn(List.of());
    when(taskMapper.toDetail(any(), any(), any(), any())).thenReturn(stubDetail(task, null));

    TaskUpdateForm form = new TaskUpdateForm();
    form.setUnlinkIssue(true);

    taskService.update("alice", "OH", "OH-1", form);

    assertThat(task.getLinkedIssue()).isNull();
    verify(issueRepository, never()).findById(any());
  }

  @Test
  @DisplayName("update — unlinkIssue=true takes precedence over linkedIssueId")
  void update_unlink_takesPrecedenceOverLinkedIssueId() {
    UUID issueId = UUID.randomUUID();
    Project proj = project("OH", 1L);
    BoardColumn col = column();
    Task task = existingTask(proj, col);
    task.setLinkedIssue(issue(UUID.randomUUID())); // already linked to something else

    when(projectService.findProject("alice", "OH")).thenReturn(proj);
    when(taskRepository.findByProjectIdAndCode(proj.getId(), "OH-1")).thenReturn(Optional.of(task));
    when(taskRepository.save(task)).thenReturn(task);
    when(subtaskRepository.findAllByTaskIdOrderByPositionAsc(task.getId())).thenReturn(List.of());
    when(taskMapper.toDetail(any(), any(), any(), any())).thenReturn(stubDetail(task, null));

    TaskUpdateForm form = new TaskUpdateForm();
    form.setUnlinkIssue(true);
    form.setLinkedIssueId(issueId); // both set — unlink wins

    taskService.update("alice", "OH", "OH-1", form);

    assertThat(task.getLinkedIssue()).isNull();
    verify(issueRepository, never()).findById(any());
  }

  @Test
  @DisplayName("update — throws ItemNotFoundException when linked issue not found")
  void update_throws_whenLinkedIssueNotFound() {
    UUID issueId = UUID.randomUUID();
    Project proj = project("OH", 1L);
    BoardColumn col = column();
    Task task = existingTask(proj, col);

    when(projectService.findProject("alice", "OH")).thenReturn(proj);
    when(taskRepository.findByProjectIdAndCode(proj.getId(), "OH-1")).thenReturn(Optional.of(task));
    when(issueRepository.findById(issueId)).thenReturn(Optional.empty());

    TaskUpdateForm form = new TaskUpdateForm();
    form.setLinkedIssueId(issueId);

    assertThatThrownBy(() -> taskService.update("alice", "OH", "OH-1", form))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("Issue not found");
  }

  @Test
  @DisplayName("update — skips issue linking when neither linkedIssueId nor unlinkIssue is set")
  void update_skipsIssueLinking_whenNeitherFieldSet() {
    Project proj = project("OH", 1L);
    BoardColumn col = column();
    Task task = existingTask(proj, col);
    Issue existing = issue(UUID.randomUUID());
    task.setLinkedIssue(existing); // preserve existing link

    when(projectService.findProject("alice", "OH")).thenReturn(proj);
    when(taskRepository.findByProjectIdAndCode(proj.getId(), "OH-1")).thenReturn(Optional.of(task));
    when(taskRepository.save(task)).thenReturn(task);
    when(subtaskRepository.findAllByTaskIdOrderByPositionAsc(task.getId())).thenReturn(List.of());

    LinkedIssueInfo linkedInfo =
        LinkedIssueInfo.builder()
            .id(existing.getId())
            .number(1)
            .title("Sample issue")
            .status("OPEN")
            .build();
    when(taskMapper.toDetail(any(), any(), any(), any())).thenReturn(stubDetail(task, linkedInfo));

    TaskUpdateForm form = new TaskUpdateForm();
    form.setTitle("Updated title");

    taskService.update("alice", "OH", "OH-1", form);

    // linked issue must be untouched
    assertThat(task.getLinkedIssue()).isSameAs(existing);
    verify(issueRepository, never()).findById(any());
  }
}
