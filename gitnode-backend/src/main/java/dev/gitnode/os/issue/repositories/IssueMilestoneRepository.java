package dev.gitnode.os.issue.repositories;

import dev.gitnode.os.issue.entities.IssueMilestone;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueMilestoneRepository extends JpaRepository<IssueMilestone, UUID> {
  List<IssueMilestone> findAllByRepoIdOrderByTitleAsc(UUID repoId);

  Optional<IssueMilestone> findByIdAndRepoId(UUID id, UUID repoId);

  Optional<IssueMilestone> findByRepoIdAndTitleIgnoreCase(UUID repoId, String title);
}
