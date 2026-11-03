package dev.gitnode.os.branch.repositories;

import dev.gitnode.os.branch.entities.BranchProtectionRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchProtectionRepository extends JpaRepository<BranchProtectionRule, UUID> {
  Optional<BranchProtectionRule> findByRepoIdAndBranchName(UUID repoId, String branchName);

  List<BranchProtectionRule> findAllByRepoIdOrderByBranchName(UUID repoId);
}
