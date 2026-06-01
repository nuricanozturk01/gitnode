package com.nuricanozturk.originhub.task.listeners;

import com.nuricanozturk.originhub.shared.issue.events.IssueDeletedEvent;
import com.nuricanozturk.originhub.task.repositories.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@NullMarked
public class IssueDeletedEventListener {

  private final TaskRepository taskRepository;

  @ApplicationModuleListener
  public void onIssueDeleted(final IssueDeletedEvent event) {
    this.taskRepository.clearLinkedIssueId(event.issueId());
  }
}
