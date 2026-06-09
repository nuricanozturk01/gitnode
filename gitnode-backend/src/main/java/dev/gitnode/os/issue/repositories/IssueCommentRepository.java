package dev.gitnode.os.issue.repositories;

import dev.gitnode.os.issue.entities.IssueComment;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
@NullMarked
public interface IssueCommentRepository extends JpaRepository<IssueComment, UUID> {

  @Query(
      """
      SELECT c FROM IssueComment c
      JOIN FETCH c.author
      WHERE c.issue.id = :issueId
      ORDER BY c.createdAt ASC
      """)
  Page<IssueComment> findAllByIssueIdOrderByCreatedAtAsc(UUID issueId, Pageable pageable);

  Optional<IssueComment> findByIdAndIssueId(UUID id, UUID issueId);

  int countByIssueId(UUID issueId);
}
