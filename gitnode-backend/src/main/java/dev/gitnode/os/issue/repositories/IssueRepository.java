package dev.gitnode.os.issue.repositories;

import dev.gitnode.os.issue.entities.Issue;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
@NullMarked
public interface IssueRepository extends JpaRepository<Issue, UUID> {

  @Query(
      """
      SELECT i FROM Issue i
      JOIN FETCH i.author
      LEFT JOIN FETCH i.assignee
      WHERE i.repo.id = :repoId AND i.status = :status
      ORDER BY i.createdAt DESC
      """)
  Page<Issue> findAllByRepoIdAndStatusOrderByCreatedAtDesc(
      UUID repoId, String status, Pageable pageable);

  @Query(
      """
      SELECT i FROM Issue i JOIN FETCH i.author LEFT JOIN FETCH i.assignee JOIN i.labels l
      WHERE i.repo.id = :repoId AND i.status = :status AND l.id = :labelId
      ORDER BY i.createdAt DESC
      """)
  Page<Issue> findAllByLabel(UUID repoId, String status, UUID labelId, Pageable pageable);

  @Query(
      """
      SELECT i FROM Issue i JOIN FETCH i.author LEFT JOIN FETCH i.assignee
      WHERE i.repo.id = :repoId AND i.status = :status AND i.milestone.id = :milestoneId
      AND (:labelId IS NULL OR EXISTS (SELECT l.id FROM i.labels l WHERE l.id = :labelId))
      ORDER BY i.createdAt DESC
      """)
  Page<Issue> findAllByMilestone(
      UUID repoId, String status, UUID milestoneId, UUID labelId, Pageable pageable);

  @Query(
      """
      SELECT i FROM Issue i
      JOIN FETCH i.author
      LEFT JOIN FETCH i.assignee
      WHERE i.repo.id = :repoId AND i.number = :number
      """)
  Optional<Issue> findByRepoIdAndNumber(UUID repoId, int number);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM Issue i WHERE i.repo.id = :repoId AND i.number = :number")
  Optional<Issue> findByRepoIdAndNumberForUpdate(UUID repoId, int number);

  @Query("SELECT COALESCE(MAX(i.number), 0) FROM Issue i WHERE i.repo.id = :repoId")
  int findMaxNumberByRepoId(UUID repoId);

  int countByRepoIdAndStatus(UUID repoId, String status);
}
