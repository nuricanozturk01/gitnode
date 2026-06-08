package dev.gitnode.os.task.adapters;

import dev.gitnode.os.issue.api.LinkedTaskData;
import dev.gitnode.os.issue.api.TaskQueryPort;
import dev.gitnode.os.task.repositories.TaskRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
@NullMarked
public class TaskQueryAdapter implements TaskQueryPort {

  private final TaskRepository taskRepository;

  @Override
  public List<LinkedTaskData> findByLinkedIssueId(final UUID issueId) {
    return this.taskRepository.findByLinkedIssueId(issueId).stream()
        .map(
            t ->
                new LinkedTaskData(
                    t.getCode(),
                    t.getTitle(),
                    t.getStatus(),
                    t.getProject().getCodePrefix(),
                    t.getProject().getName()))
        .toList();
  }
}
