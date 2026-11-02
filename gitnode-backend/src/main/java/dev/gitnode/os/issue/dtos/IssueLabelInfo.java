package dev.gitnode.os.issue.dtos;

import dev.gitnode.os.issue.entities.IssueLabel;
import java.util.UUID;

public record IssueLabelInfo(UUID id, String name, String color, String description) {
  public static IssueLabelInfo from(final IssueLabel label) {
    return new IssueLabelInfo(
        label.getId(), label.getName(), label.getColor(), label.getDescription());
  }
}
