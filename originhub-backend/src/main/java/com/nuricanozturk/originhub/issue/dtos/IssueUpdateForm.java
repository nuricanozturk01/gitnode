package com.nuricanozturk.originhub.issue.dtos;

import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NullMarked;

@Data
@NoArgsConstructor
@AllArgsConstructor
@NullMarked
public class IssueUpdateForm {

  @Size(min = 1, max = 255)
  private String title;

  private String description;

  private String status;

  private UUID assigneeId;
}
