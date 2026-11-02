package dev.gitnode.os.issue.dtos;

import dev.gitnode.os.issue.entities.IssueMilestone;
import java.time.LocalDate;
import java.util.UUID;

public record IssueMilestoneInfo(
    UUID id, String title, String description, LocalDate dueDate, String status) {
  public static IssueMilestoneInfo from(final IssueMilestone milestone) {
    return new IssueMilestoneInfo(
        milestone.getId(),
        milestone.getTitle(),
        milestone.getDescription(),
        milestone.getDueDate(),
        milestone.getStatus());
  }
}
