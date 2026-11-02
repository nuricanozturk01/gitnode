package dev.gitnode.os.pr.repositories;

import dev.gitnode.os.pr.entities.PrReview;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PrReviewRepository extends JpaRepository<PrReview, UUID> {
  @Query(
      "SELECT r FROM PrReview r JOIN FETCH r.author WHERE r.pullRequest.id = :prId ORDER BY r.createdAt ASC")
  List<PrReview> findAllByPrId(UUID prId);
}
