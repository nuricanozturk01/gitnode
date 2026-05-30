package com.nuricanozturk.originhub.task.dtos;

import java.util.UUID;
import lombok.Builder;
import org.jspecify.annotations.NullMarked;

@Builder
@NullMarked
public record LinkedIssueInfo(UUID id, int number, String title, String status) {}
