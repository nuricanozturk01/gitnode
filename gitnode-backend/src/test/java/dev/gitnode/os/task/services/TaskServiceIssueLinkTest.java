package dev.gitnode.os.task.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.issue.api.IssueData;
import dev.gitnode.os.issue.api.IssueQueryService;
import dev.gitnode.os.pr.repositories.PrRepository;
import dev.gitnode.os.shared.branch.services.BranchProtocolService;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import dev.gitnode.os.task.dtos.LinkedIssueInfo;
import dev.gitnode.os.task.dtos.TaskDetail;
import dev.gitnode.os.task.dtos.TaskForm;
import dev.gitnode.os.task.dtos.TaskUpdateForm;
import dev.gitnode.os.task.entities.BoardColumn;
import dev.gitnode.os.task.entities.Project;
import dev.gitnode.os.task.entities.Task;
import dev.gitnode.os.task.mappers.ProjectMapper;
import dev.gitnode.os.task.mappers.TaskMapper;
import dev.gitnode.os.task.repositories.BoardColumnRepository;
import dev.gitnode.os.task.repositories.ProjectRepository;
import dev.gitnode.os.task.repositories.SubtaskRepository;
import dev.gitnode.os.task.repositories.TaskRepository;
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
import org.springframework.context.ApplicationEventPublisher;

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
  @Mock private IssueQueryService issueQueryService;
  @Mock private ProjectService projectService;
  @Mock private TaskMapper taskMapper;
  @Mock private ProjectMapper projectMapper;
  @Mock private BranchProtocolService branchProtocolService;
  @Mock private ApplicationEventPublisher eventPublisher;

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

  private static IssueData issueData(UUID id) {
    return new IssueData(id, 1, "Sample issue", "OPEN");
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
    IssueData iss = issueData(issueId);
    Task saved = savedTask(proj, col);
    saved.setLinkedIssueId(issueId);

    when(projectService.findProject("alice", "OH")).thenReturn(proj);
    when(boardColumnRepository.findById(columnId)).thenReturn(Optional.of(col));
    when(projectRepository.findById(proj.getId())).thenReturn(Optional.of(proj));
    when(issueQueryService.findById(issueId)).thenReturn(Optional.of(iss));
    when(taskRepository.save(any(Task.class))).thenReturn(saved);
    when(subtaskRepository.findAllByTaskIdOrderByPositionAsc(saved.getId())).thenReturn(List.of());

    LinkedIssueInfo linkedInfo =
        LinkedIssueInfo.builder()
            .id(issueId)
            .number(1)
            .title("Sample issue")
            .status("OPEN")
            .build();
    when(issueQueryService.findById(issueId)).thenReturn(Optional.of(iss));
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
    assertThat(captor.getValue().getLinkedIssueId()).isEqualTo(issueId);
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
    verify(issueQueryService, never()).findById(any());

    ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
    verify(taskRepository).save(captor.capture());
    assertThat(captor.getValue().getLinkedIssueId()).isNull();
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
    when(issueQueryService.findById(issueId)).thenReturn(Optional.empty());

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
    IssueData iss = issueData(issueId);

    when(projectService.findProject("alice", "OH")).thenReturn(proj);
    when(taskRepository.findByProjectIdAndCode(proj.getId(), "OH-1")).thenReturn(Optional.of(task));
    when(issueQueryService.findById(issueId)).thenReturn(Optional.of(iss));
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

    assertThat(task.getLinkedIssueId()).isEqualTo(issueId);
    assertThat(result.linkedIssue()).isNotNull();
    assertThat(result.linkedIssue().id()).isEqualTo(issueId);
  }

  @Test
  @DisplayName("update — unlinks issue when unlinkIssue is true")
  void update_unlinksIssue_whenUnlinkIssueTrue() {
    Project proj = project("OH", 1L);
    BoardColumn col = column();
    Task task = existingTask(proj, col);
    task.setLinkedIssueId(UUID.randomUUID()); // already linked

    when(projectService.findProject("alice", "OH")).thenReturn(proj);
    when(taskRepository.findByProjectIdAndCode(proj.getId(), "OH-1")).thenReturn(Optional.of(task));
    when(taskRepository.save(task)).thenReturn(task);
    when(subtaskRepository.findAllByTaskIdOrderByPositionAsc(task.getId())).thenReturn(List.of());
    when(taskMapper.toDetail(any(), any(), any(), any())).thenReturn(stubDetail(task, null));

    TaskUpdateForm form = new TaskUpdateForm();
    form.setUnlinkIssue(true);

    taskService.update("alice", "OH", "OH-1", form);

    assertThat(task.getLinkedIssueId()).isNull();
    verify(issueQueryService, never()).findById(any());
  }

  @Test
  @DisplayName("update — unlinkIssue=true takes precedence over linkedIssueId")
  void update_unlink_takesPrecedenceOverLinkedIssueId() {
    UUID issueId = UUID.randomUUID();
    Project proj = project("OH", 1L);
    BoardColumn col = column();
    Task task = existingTask(proj, col);
    task.setLinkedIssueId(UUID.randomUUID()); // already linked to something else

    when(projectService.findProject("alice", "OH")).thenReturn(proj);
    when(taskRepository.findByProjectIdAndCode(proj.getId(), "OH-1")).thenReturn(Optional.of(task));
    when(taskRepository.save(task)).thenReturn(task);
    when(subtaskRepository.findAllByTaskIdOrderByPositionAsc(task.getId())).thenReturn(List.of());
    when(taskMapper.toDetail(any(), any(), any(), any())).thenReturn(stubDetail(task, null));

    TaskUpdateForm form = new TaskUpdateForm();
    form.setUnlinkIssue(true);
    form.setLinkedIssueId(issueId); // both set — unlink wins

    taskService.update("alice", "OH", "OH-1", form);

    assertThat(task.getLinkedIssueId()).isNull();
    verify(issueQueryService, never()).findById(any());
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
    when(issueQueryService.findById(issueId)).thenReturn(Optional.empty());

    TaskUpdateForm form = new TaskUpdateForm();
    form.setLinkedIssueId(issueId);

    assertThatThrownBy(() -> taskService.update("alice", "OH", "OH-1", form))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("Issue not found");
  }

  @Test
  @DisplayName("update — skips issue linking when neither linkedIssueId nor unlinkIssue is set")
  void update_skipsIssueLinking_whenNeitherFieldSet() {
    UUID existingIssueId = UUID.randomUUID();
    Project proj = project("OH", 1L);
    BoardColumn col = column();
    Task task = existingTask(proj, col);
    task.setLinkedIssueId(existingIssueId); // preserve existing link

    when(projectService.findProject("alice", "OH")).thenReturn(proj);
    when(taskRepository.findByProjectIdAndCode(proj.getId(), "OH-1")).thenReturn(Optional.of(task));
    when(taskRepository.save(task)).thenReturn(task);
    when(subtaskRepository.findAllByTaskIdOrderByPositionAsc(task.getId())).thenReturn(List.of());

    LinkedIssueInfo linkedInfo =
        LinkedIssueInfo.builder()
            .id(existingIssueId)
            .number(1)
            .title("Sample issue")
            .status("OPEN")
            .build();
    when(issueQueryService.findById(existingIssueId))
        .thenReturn(Optional.of(new IssueData(existingIssueId, 1, "Sample issue", "OPEN")));
    when(taskMapper.toDetail(any(), any(), any(), any())).thenReturn(stubDetail(task, linkedInfo));

    TaskUpdateForm form = new TaskUpdateForm();
    form.setTitle("Updated title");

    taskService.update("alice", "OH", "OH-1", form);

    // linked issue id must be untouched
    assertThat(task.getLinkedIssueId()).isEqualTo(existingIssueId);
  }
}
