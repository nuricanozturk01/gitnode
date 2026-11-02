package dev.gitnode.os.issue.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "issue_labels")
public class IssueLabel {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "repo_id", nullable = false)
  private UUID repoId;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(nullable = false, length = 6)
  private String color;

  @Column(length = 255)
  private String description;
}
