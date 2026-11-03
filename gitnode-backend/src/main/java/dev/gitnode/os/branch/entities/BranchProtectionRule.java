package dev.gitnode.os.branch.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "branch_protection_rules")
@Getter
@Setter
public class BranchProtectionRule {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "repo_id", nullable = false)
  private UUID repoId;

  @Column(name = "branch_name", nullable = false, length = 255)
  private String branchName;

  @Column(name = "required_approvals", nullable = false)
  private int requiredApprovals;

  @Column(name = "require_conversation_resolution", nullable = false)
  private boolean requireConversationResolution;

  @Column(name = "required_checks", nullable = false, columnDefinition = "text")
  private String requiredChecks = "";

  public java.util.List<String> requiredCheckNames() {
    return java.util.Arrays.stream(this.requiredChecks.split(","))
        .map(String::strip)
        .filter(name -> !name.isEmpty())
        .toList();
  }
}
