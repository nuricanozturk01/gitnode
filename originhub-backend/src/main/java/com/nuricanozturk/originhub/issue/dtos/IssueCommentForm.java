package com.nuricanozturk.originhub.issue.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NullMarked;

@Data
@NoArgsConstructor
@AllArgsConstructor
@NullMarked
public class IssueCommentForm {

  @NotBlank private String body;
}
