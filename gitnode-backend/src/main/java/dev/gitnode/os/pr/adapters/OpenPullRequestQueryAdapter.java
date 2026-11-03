package dev.gitnode.os.pr.adapters;

import dev.gitnode.os.branch.api.OpenPullRequestQueryPort;
import dev.gitnode.os.pr.repositories.PrRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OpenPullRequestQueryAdapter implements OpenPullRequestQueryPort {
  private final PrRepository pullRequests;

  @Override
  public List<String> findTargetBranches(final UUID repoId, final String sourceBranch) {
    return this.pullRequests.findOpenTargetBranches(repoId, sourceBranch);
  }
}
