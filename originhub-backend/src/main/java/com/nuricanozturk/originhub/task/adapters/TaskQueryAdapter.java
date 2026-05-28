package com.nuricanozturk.originhub.task.adapters;

import com.nuricanozturk.originhub.issue.api.LinkedTaskData;
import com.nuricanozturk.originhub.issue.api.TaskQueryPort;
import com.nuricanozturk.originhub.task.repositories.TaskRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class TaskQueryAdapter implements TaskQueryPort {

  private final @NonNull TaskRepository taskRepository;

  @Override
  public List<LinkedTaskData> findByLinkedIssueId(final @NonNull UUID issueId) {
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
