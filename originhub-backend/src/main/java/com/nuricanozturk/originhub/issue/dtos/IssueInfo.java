package com.nuricanozturk.originhub.issue.dtos;

import com.nuricanozturk.originhub.shared.commit.dtos.AuthorInfo;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Builder
public record IssueInfo(
    @NonNull UUID id,
    int number,
    @NonNull String title,
    @NonNull String status,
    @NonNull AuthorInfo author,
    @Nullable AuthorInfo assignee,
    int commentCount,
    @Nullable Instant createdAt,
    @Nullable Instant updatedAt,
    @Nullable Instant closedAt) {}
