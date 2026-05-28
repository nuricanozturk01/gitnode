package com.nuricanozturk.originhub.issue.repositories;

import com.nuricanozturk.originhub.issue.entities.Issue;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface IssueRepository extends JpaRepository<Issue, UUID> {

  @Query(
      """
      SELECT i FROM Issue i
      JOIN FETCH i.author
      LEFT JOIN FETCH i.assignee
      WHERE i.repo.id = :repoId AND i.status = :status
      ORDER BY i.createdAt DESC
      """)
  @NonNull Page<Issue> findAllByRepoIdAndStatusOrderByCreatedAtDesc(
      @NonNull UUID repoId, @NonNull String status, @NonNull Pageable pageable);

  @Query(
      """
      SELECT i FROM Issue i
      JOIN FETCH i.author
      LEFT JOIN FETCH i.assignee
      WHERE i.repo.id = :repoId AND i.number = :number
      """)
  Optional<Issue> findByRepoIdAndNumber(@NonNull UUID repoId, int number);

  @Query("SELECT COALESCE(MAX(i.number), 0) FROM Issue i WHERE i.repo.id = :repoId")
  int findMaxNumberByRepoId(@NonNull UUID repoId);
}
