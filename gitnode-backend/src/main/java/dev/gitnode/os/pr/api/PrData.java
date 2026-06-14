package dev.gitnode.os.pr.api;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record PrData(
    UUID id,
    int number,
    String title,
    String sourceBranch,
    String targetBranch,
    String status,
    @Nullable String sourceSha,
    UUID authorId) {}
