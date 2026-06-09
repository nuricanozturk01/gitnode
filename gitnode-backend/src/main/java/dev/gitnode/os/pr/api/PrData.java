package dev.gitnode.os.pr.api;

import java.util.UUID;

public record PrData(
    UUID id, int number, String title, String sourceBranch, String targetBranch, String status) {}
