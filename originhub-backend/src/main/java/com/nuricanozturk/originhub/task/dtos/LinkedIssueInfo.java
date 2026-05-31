package com.nuricanozturk.originhub.task.dtos;

import java.util.UUID;
import lombok.Builder;

@Builder
public record LinkedIssueInfo(UUID id, int number, String title, String status) {}
