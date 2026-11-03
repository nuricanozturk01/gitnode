package dev.gitnode.os.task.adapters;

import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.issue.api.LinkedTaskData;
import dev.gitnode.os.issue.api.TaskQueryPort;
import dev.gitnode.os.task.repositories.TaskRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
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
  private final TenantQueryPort tenants;

  @Override
  public List<LinkedTaskData> findByLinkedIssueId(
      final UUID issueId, final @Nullable UUID viewerId) {
    final var visible =
        this.taskRepository.findByLinkedIssueId(issueId).stream()
            .filter(t -> t.getProject().isPublic() || t.getProject().getOwnerId().equals(viewerId))
            .toList();
    final var owners =
        this.tenants.findAllByIds(
            visible.stream().map(t -> t.getProject().getOwnerId()).collect(Collectors.toSet()));
    return visible.stream()
        .filter(t -> owners.containsKey(t.getProject().getOwnerId()))
        .map(
            t ->
                new LinkedTaskData(
                    t.getCode(),
                    t.getTitle(),
                    t.getStatus(),
                    t.getProject().getCodePrefix(),
                    t.getProject().getName(),
                    owners.get(t.getProject().getOwnerId()).username()))
        .toList();
  }
}
