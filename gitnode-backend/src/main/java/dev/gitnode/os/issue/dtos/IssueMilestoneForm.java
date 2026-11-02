package dev.gitnode.os.issue.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record IssueMilestoneForm(
    @NotBlank @Size(max = 255) String title,
    @Size(max = 2000) String description,
    LocalDate dueDate,
    @NotBlank @Pattern(regexp = "OPEN|CLOSED") String status) {}
