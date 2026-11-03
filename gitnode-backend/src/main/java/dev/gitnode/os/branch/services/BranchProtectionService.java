package dev.gitnode.os.branch.services;

import dev.gitnode.os.branch.api.BranchProtectionPort;
import dev.gitnode.os.branch.entities.BranchProtectionRule;
import dev.gitnode.os.branch.repositories.BranchProtectionRepository;
import dev.gitnode.os.repo.api.RepoCommandPort;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchProtectionService implements BranchProtectionPort {
  private final BranchProtectionRepository rules;
  private final RepoLookupPort repos;
  private final RepoCommandPort repoCommands;

  @Override
  public Optional<BranchProtectionInfo> findProtection(final UUID repoId, final String branch) {
    return this.rules
        .findByRepoIdAndBranchName(repoId, branch)
        .map(
            rule ->
                new BranchProtectionInfo(
                    rule.getRequiredApprovals(),
                    rule.isRequireConversationResolution(),
                    rule.isRequireCodeOwnerReview(),
                    rule.requiredCheckNames()));
  }

  public Optional<BranchProtectionRule> find(final UUID repoId, final String branch) {
    return this.rules.findByRepoIdAndBranchName(repoId, branch);
  }

  public List<BranchProtectionRule> list(final UUID repoId) {
    return this.rules.findAllByRepoIdOrderByBranchName(repoId);
  }

  public java.util.Set<String> protectedBranchNames(final String owner, final String repoName) {
    return this.repos
        .find(owner, repoName)
        .map(repo -> this.rules.findAllByRepoIdOrderByBranchName(repo.id()))
        .map(
            list ->
                list.stream()
                    .map(BranchProtectionRule::getBranchName)
                    .collect(java.util.stream.Collectors.toSet()))
        .orElse(java.util.Set.of());
  }

  @Transactional
  public BranchProtectionRule save(
      final UUID repoId,
      final String branch,
      final int approvals,
      final List<String> checks,
      final boolean requireConversationResolution,
      final boolean requireCodeOwnerReview) {
    if (!this.repoCommands.lockForUpdate(repoId)) {
      throw new ItemNotFoundException("Repository not found");
    }
    final var rule =
        this.rules.findByRepoIdAndBranchName(repoId, branch).orElseGet(BranchProtectionRule::new);
    rule.setRepoId(repoId);
    rule.setBranchName(branch);
    rule.setRequiredApprovals(approvals);
    rule.setRequireConversationResolution(requireConversationResolution);
    rule.setRequireCodeOwnerReview(requireCodeOwnerReview);
    rule.setRequiredChecks(String.join(",", checks));
    return this.rules.save(rule);
  }

  @Transactional
  public void delete(final UUID repoId, final UUID ruleId) {
    if (!this.repoCommands.lockForUpdate(repoId)) {
      throw new ItemNotFoundException("Repository not found");
    }
    final var rule =
        this.rules
            .findById(ruleId)
            .filter(found -> found.getRepoId().equals(repoId))
            .orElseThrow(() -> new ItemNotFoundException("Branch protection rule not found"));
    this.rules.delete(rule);
  }

  @Override
  public void assertDirectWriteAllowed(
      final String owner, final String repoName, final String branch) {
    final var repo =
        this.repos
            .find(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException("Repository not found"));
    if (this.rules.findByRepoIdAndBranchName(repo.id(), branch).isPresent()) {
      throw new BadRequestException(
          "Protected branch can only be updated by merging a pull request");
    }
  }
}
