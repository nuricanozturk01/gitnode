package dev.gitnode.os.issue.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record IssueLabelForm(
    @NotBlank @Size(max = 50) String name,
    @NotBlank @Pattern(regexp = "[0-9a-fA-F]{6}") String color,
    @Size(max = 255) String description) {}
