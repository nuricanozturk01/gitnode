package dev.gitnode.os.issue.api;

import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface IssueQueryService {

  Optional<IssueData> findById(UUID id);
}
