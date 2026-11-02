package dev.gitnode.os.issue.dtos;

import dev.gitnode.os.shared.commit.dtos.AuthorInfo;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

@Builder
public record IssueDetail(
    UUID id,
    int number,
    String title,
    @Nullable String description,
    String status,
    AuthorInfo author,
    @Nullable AuthorInfo assignee,
    List<AuthorInfo> assignees,
    List<IssueLabelInfo> labels,
    @Nullable IssueMilestoneInfo milestone,
    int commentCount,
    @Nullable Instant createdAt,
    @Nullable Instant updatedAt,
    @Nullable Instant closedAt) {}
