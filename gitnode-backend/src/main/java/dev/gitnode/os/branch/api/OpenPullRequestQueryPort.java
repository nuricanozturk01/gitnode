package dev.gitnode.os.branch.api;

import java.util.List;
import java.util.UUID;

public interface OpenPullRequestQueryPort {
  List<String> findTargetBranches(UUID repoId, String sourceBranch);
}
