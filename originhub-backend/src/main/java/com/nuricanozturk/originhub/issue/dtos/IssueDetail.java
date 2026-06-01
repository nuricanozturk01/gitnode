package com.nuricanozturk.originhub.issue.dtos;

import com.nuricanozturk.originhub.shared.commit.dtos.AuthorInfo;
import java.time.Instant;
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
    int commentCount,
    @Nullable Instant createdAt,
    @Nullable Instant updatedAt,
    @Nullable Instant closedAt) {}
