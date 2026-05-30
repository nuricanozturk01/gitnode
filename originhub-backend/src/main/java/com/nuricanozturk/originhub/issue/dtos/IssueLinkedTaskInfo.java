package com.nuricanozturk.originhub.issue.dtos;

import lombok.Builder;
import org.jspecify.annotations.NullMarked;

@Builder
@NullMarked
public record IssueLinkedTaskInfo(
    String taskCode, String taskTitle, String taskStatus, String projectCode, String projectName) {}
