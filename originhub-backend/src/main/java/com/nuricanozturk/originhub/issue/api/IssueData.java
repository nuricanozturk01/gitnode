package com.nuricanozturk.originhub.issue.api;

import java.util.UUID;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record IssueData(UUID id, int number, String title, String status) {}
