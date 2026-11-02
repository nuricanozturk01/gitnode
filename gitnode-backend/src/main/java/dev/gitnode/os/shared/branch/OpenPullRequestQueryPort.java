package dev.gitnode.os.shared.branch;

import java.util.List;
import java.util.UUID;

public interface OpenPullRequestQueryPort {
  List<String> findTargetBranches(UUID repoId, String sourceBranch);
}
