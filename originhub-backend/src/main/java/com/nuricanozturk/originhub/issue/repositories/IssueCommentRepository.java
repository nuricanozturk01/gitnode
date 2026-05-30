package com.nuricanozturk.originhub.issue.repositories;

import com.nuricanozturk.originhub.issue.entities.IssueComment;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface IssueCommentRepository extends JpaRepository<IssueComment, UUID> {

  @Query(
      """
      SELECT c FROM IssueComment c
      JOIN FETCH c.author
      WHERE c.issue.id = :issueId
      ORDER BY c.createdAt ASC
      """)
  @NonNull Page<IssueComment> findAllByIssueIdOrderByCreatedAtAsc(
      @NonNull UUID issueId, @NonNull Pageable pageable);

  Optional<IssueComment> findByIdAndIssueId(@NonNull UUID id, @NonNull UUID issueId);

  int countByIssueId(@NonNull UUID issueId);
}
