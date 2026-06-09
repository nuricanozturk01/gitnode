package dev.gitnode.os.issue.api;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface TaskQueryPort {
  List<LinkedTaskData> findByLinkedIssueId(UUID issueId);
}
