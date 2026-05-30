package com.nuricanozturk.originhub.issue.dtos;

import lombok.Builder;
import org.jspecify.annotations.NonNull;

@Builder
public record IssueLinkedTaskInfo(
    @NonNull String taskCode,
    @NonNull String taskTitle,
    @NonNull String taskStatus,
    @NonNull String projectCode,
    @NonNull String projectName) {}
