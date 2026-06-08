package dev.gitnode.os.issue.dtos;

import dev.gitnode.os.shared.commit.dtos.AuthorInfo;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
public record IssueCommentInfo(
    UUID id,
    AuthorInfo author,
    String body,
    @Nullable Instant createdAt,
    @Nullable Instant updatedAt) {}
