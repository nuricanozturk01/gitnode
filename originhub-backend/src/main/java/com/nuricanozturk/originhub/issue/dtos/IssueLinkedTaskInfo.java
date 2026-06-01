package com.nuricanozturk.originhub.issue.dtos;

import lombok.Builder;

@Builder
public record IssueLinkedTaskInfo(
    String taskCode, String taskTitle, String taskStatus, String projectCode, String projectName) {}
