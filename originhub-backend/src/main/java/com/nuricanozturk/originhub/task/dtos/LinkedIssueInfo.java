package com.nuricanozturk.originhub.task.dtos;

import java.util.UUID;
import lombok.Builder;
import org.jspecify.annotations.NonNull;

@Builder
public record LinkedIssueInfo(
    @NonNull UUID id, int number, @NonNull String title, @NonNull String status) {}
