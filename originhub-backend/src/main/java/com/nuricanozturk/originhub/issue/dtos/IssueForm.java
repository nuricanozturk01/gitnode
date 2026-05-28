package com.nuricanozturk.originhub.issue.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IssueForm {

  @NotBlank
  @Size(min = 1, max = 255)
  private String title;

  private String description;

  private UUID assigneeId;
}
