package dev.gitnode.os.issue.dtos;

import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class IssueUpdateForm {

  @Size(min = 1, max = 255)
  private String title;

  private String description;

  private String status;

  private UUID assigneeId;

  @Size(max = 255)
  private String assigneeUsername;

  @Size(max = 255)
  private String addAssigneeUsername;

  @Size(max = 255)
  private String removeAssigneeUsername;

  private boolean clearAssignee;

  private UUID addLabelId;

  private UUID removeLabelId;

  private UUID milestoneId;

  private boolean clearMilestone;

  public IssueUpdateForm(
      final String title, final String description, final String status, final UUID assigneeId) {
    this.title = title;
    this.description = description;
    this.status = status;
    this.assigneeId = assigneeId;
  }
}
