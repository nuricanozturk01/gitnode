package dev.gitnode.os.issue.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "issue_milestones")
public class IssueMilestone {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "repo_id", nullable = false)
  private UUID repoId;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(length = 2000)
  private String description;

  @Column(name = "due_date")
  private LocalDate dueDate;

  @Column(nullable = false, length = 10)
  private String status;
}
