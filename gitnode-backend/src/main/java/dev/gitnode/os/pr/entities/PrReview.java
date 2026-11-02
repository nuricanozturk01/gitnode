package dev.gitnode.os.pr.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@Entity
@Table(name = "pr_reviews")
public class PrReview {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "pr_id")
  private PullRequest pullRequest;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "author_id")
  private dev.gitnode.os.shared.tenant.entities.Tenant author;

  @Column(nullable = false, length = 20)
  private String state;

  @Column(columnDefinition = "text")
  private String body;

  @Column(name = "commit_sha", nullable = false, length = 40)
  private String commitSha;

  @Column(name = "target_sha", length = 40)
  private String targetSha;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
