package dev.gitnode.os.issue.api;

import java.util.UUID;

public record IssueData(UUID id, int number, String title, String status) {}
