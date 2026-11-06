package dev.gitnode.os.issue.repositories;

import dev.gitnode.os.issue.entities.IssueComment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@NullMarked
public interface IssueCommentRepository extends JpaRepository<IssueComment, UUID> {

  @Query(
      """
      SELECT c FROM IssueComment c
      WHERE c.issue.id = :issueId
      ORDER BY c.createdAt ASC
      """)
  Page<IssueComment> findAllByIssueIdOrderByCreatedAtAsc(UUID issueId, Pageable pageable);

  Optional<IssueComment> findByIdAndIssueId(UUID id, UUID issueId);

  int countByIssueId(UUID issueId);

  /** Comment counts of many issues in one query (rows of {issueId, count}). */
  @Query(
      "SELECT c.issue.id, count(c) FROM IssueComment c WHERE c.issue.id IN :issueIds"
          + " GROUP BY c.issue.id")
  List<Object[]> countByIssueIds(@Param("issueIds") java.util.Collection<UUID> issueIds);

  @Query("SELECT DISTINCT c.authorId FROM IssueComment c WHERE c.issue.id = :issueId")
  List<UUID> findDistinctCommenterIdsByIssueId(UUID issueId);
}
