package com.nuricanozturk.originhub.task.listeners;

import static org.mockito.Mockito.verify;

import com.nuricanozturk.originhub.shared.issue.events.IssueDeletedEvent;
import com.nuricanozturk.originhub.task.repositories.TaskRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("IssueDeletedEventListener (RAPOR2 bug #7)")
class IssueDeletedEventListenerTest {

  @Mock private TaskRepository taskRepository;

  @InjectMocks private IssueDeletedEventListener listener;

  @Test
  @DisplayName("onIssueDeleted — calls clearLinkedIssueId with the deleted issue id")
  void onIssueDeleted_callsClearLinkedIssueId() {
    UUID issueId = UUID.randomUUID();
    IssueDeletedEvent event = new IssueDeletedEvent(issueId);

    listener.onIssueDeleted(event);

    verify(taskRepository).clearLinkedIssueId(issueId);
  }

  @Test
  @DisplayName("onIssueDeleted — does not clear other issue ids")
  void onIssueDeleted_doesNotClearOtherIds() {
    UUID issueId = UUID.randomUUID();
    UUID otherId = UUID.randomUUID();
    IssueDeletedEvent event = new IssueDeletedEvent(issueId);

    listener.onIssueDeleted(event);

    verify(taskRepository).clearLinkedIssueId(issueId);
    org.mockito.Mockito.verify(taskRepository, org.mockito.Mockito.never())
        .clearLinkedIssueId(otherId);
  }
}
