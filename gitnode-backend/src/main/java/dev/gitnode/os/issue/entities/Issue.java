package dev.gitnode.os.issue.entities;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@Entity
@Table(name = "issues")
public class Issue {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "repo_id", nullable = false)
  private UUID repoId;

  @Column(name = "number", nullable = false)
  private int number;

  @Column(name = "title", nullable = false, length = 255)
  private String title;

  @Column(name = "description", length = Integer.MAX_VALUE)
  private String description;

  @ColumnDefault("'OPEN'")
  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  /** Primary assignee; the database sets it to NULL when the account is deleted. */
  @Column(name = "assignee_id")
  private UUID assigneeId;

  @ManyToOne(fetch = FetchType.LAZY)
  @OnDelete(action = OnDeleteAction.SET_NULL)
  @JoinColumn(name = "milestone_id")
  private IssueMilestone milestone;

  @BatchSize(size = 50)
  @ElementCollection
  @CollectionTable(name = "issue_assignees", joinColumns = @JoinColumn(name = "issue_id"))
  @Column(name = "tenant_id", nullable = false)
  private Set<UUID> assigneeIds = new HashSet<>();

  @BatchSize(size = 50)
  @ManyToMany
  @JoinTable(
      name = "issue_label_assignments",
      joinColumns = @JoinColumn(name = "issue_id"),
      inverseJoinColumns = @JoinColumn(name = "label_id"))
  private Set<IssueLabel> labels = new HashSet<>();

  @CreationTimestamp
  @Column(name = "created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "closed_at")
  private Instant closedAt;
}
