package dev.gitnode.os.issue.repositories;

import dev.gitnode.os.issue.entities.IssueLabel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueLabelRepository extends JpaRepository<IssueLabel, UUID> {
  List<IssueLabel> findAllByRepoIdOrderByNameAsc(UUID repoId);

  Optional<IssueLabel> findByIdAndRepoId(UUID id, UUID repoId);

  Optional<IssueLabel> findByRepoIdAndNameIgnoreCase(UUID repoId, String name);
}
