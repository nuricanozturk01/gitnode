package com.nuricanozturk.originhub.issue.api;

import java.util.List;
import java.util.UUID;

public interface TaskQueryPort {
  List<LinkedTaskData> findByLinkedIssueId(UUID issueId);
}
