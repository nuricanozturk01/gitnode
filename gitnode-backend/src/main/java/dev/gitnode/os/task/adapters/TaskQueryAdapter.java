package dev.gitnode.os.task.adapters;

import dev.gitnode.os.issue.api.LinkedTaskData;
import dev.gitnode.os.issue.api.TaskQueryPort;
import dev.gitnode.os.task.repositories.TaskRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
@NullMarked
public class TaskQueryAdapter implements TaskQueryPort {

  private final TaskRepository taskRepository;

  @Override
  public List<LinkedTaskData> findByLinkedIssueId(
      final UUID issueId, final @Nullable UUID viewerId) {
    return this.taskRepository.findByLinkedIssueId(issueId).stream()
        .filter(
            t -> t.getProject().isPublic() || t.getProject().getOwner().getId().equals(viewerId))
        .map(
            t ->
                new LinkedTaskData(
                    t.getCode(),
                    t.getTitle(),
                    t.getStatus(),
                    t.getProject().getCodePrefix(),
                    t.getProject().getName(),
                    t.getProject().getOwner().getUsername()))
        .toList();
  }
}
