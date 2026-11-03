/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.gitnode.os.issue.mappers;

import dev.gitnode.os.commit.api.AuthorInfo;
import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.issue.dtos.IssueCommentInfo;
import dev.gitnode.os.issue.dtos.IssueDetail;
import dev.gitnode.os.issue.dtos.IssueInfo;
import dev.gitnode.os.issue.dtos.IssueLabelInfo;
import dev.gitnode.os.issue.dtos.IssueMilestoneInfo;
import dev.gitnode.os.issue.entities.Issue;
import dev.gitnode.os.issue.entities.IssueComment;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Builds the API views of issues. Issues keep author, assignees and comment authors by account id;
 * their names are resolved here, in one batch per page, through the identity port.
 */
@Component
@RequiredArgsConstructor
@NullMarked
public class IssueMapper {

  private final TenantQueryPort tenants;

  private record Lookup(Map<UUID, TenantRef> accounts) {

    AuthorInfo author(final UUID id) {
      final var tenant = this.accounts.get(id);
      return tenant == null
          ? new AuthorInfo("ghost", "", "ghost", "")
          : new AuthorInfo(
              tenant.displayName(), tenant.email(), tenant.username(), tenant.avatarUrl());
    }

    @Nullable AuthorInfo authorOrNull(final @Nullable UUID id) {
      return id == null || !this.accounts.containsKey(id) ? null : this.author(id);
    }

    List<AuthorInfo> authors(final Collection<UUID> ids) {
      return ids.stream()
          .map(this.accounts::get)
          .filter(java.util.Objects::nonNull)
          .sorted(Comparator.comparing(TenantRef::username))
          .map(t -> new AuthorInfo(t.displayName(), t.email(), t.username(), t.avatarUrl()))
          .toList();
    }
  }

  private Lookup lookup(final Collection<Issue> issues, final Collection<IssueComment> comments) {
    final Set<UUID> ids = new HashSet<>();
    for (final var issue : issues) {
      ids.add(issue.getAuthorId());
      if (issue.getAssigneeId() != null) {
        ids.add(issue.getAssigneeId());
      }
      ids.addAll(issue.getAssigneeIds());
    }
    comments.forEach(comment -> ids.add(comment.getAuthorId()));
    return new Lookup(this.tenants.findAllByIds(ids));
  }

  public IssueInfo toInfo(final Issue issue, final int commentCount) {
    return this.toInfo(issue, commentCount, this.lookup(List.of(issue), List.of()));
  }

  public Page<IssueInfo> toInfoPage(
      final Page<Issue> page, final ToIntFunction<Issue> commentCount) {
    final var lookup = this.lookup(page.getContent(), List.of());
    return page.map(issue -> this.toInfo(issue, commentCount.applyAsInt(issue), lookup));
  }

  private IssueInfo toInfo(final Issue issue, final int commentCount, final Lookup lookup) {
    return IssueInfo.builder()
        .id(issue.getId())
        .number(issue.getNumber())
        .title(issue.getTitle())
        .status(issue.getStatus())
        .author(lookup.author(issue.getAuthorId()))
        .assignee(lookup.authorOrNull(issue.getAssigneeId()))
        .assignees(lookup.authors(issue.getAssigneeIds()))
        .labels(issue.getLabels().stream().map(IssueLabelInfo::from).toList())
        .milestone(
            issue.getMilestone() == null ? null : IssueMilestoneInfo.from(issue.getMilestone()))
        .commentCount(commentCount)
        .createdAt(issue.getCreatedAt())
        .updatedAt(issue.getUpdatedAt())
        .closedAt(issue.getClosedAt())
        .build();
  }

  public IssueDetail toDetail(final Issue issue, final int commentCount) {
    final var lookup = this.lookup(List.of(issue), List.of());
    return IssueDetail.builder()
        .id(issue.getId())
        .number(issue.getNumber())
        .title(issue.getTitle())
        .description(issue.getDescription())
        .status(issue.getStatus())
        .author(lookup.author(issue.getAuthorId()))
        .assignee(lookup.authorOrNull(issue.getAssigneeId()))
        .assignees(lookup.authors(issue.getAssigneeIds()))
        .labels(issue.getLabels().stream().map(IssueLabelInfo::from).toList())
        .milestone(
            issue.getMilestone() == null ? null : IssueMilestoneInfo.from(issue.getMilestone()))
        .commentCount(commentCount)
        .createdAt(issue.getCreatedAt())
        .updatedAt(issue.getUpdatedAt())
        .closedAt(issue.getClosedAt())
        .build();
  }

  public IssueCommentInfo toCommentInfo(final IssueComment comment) {
    return this.toCommentInfo(comment, this.lookup(List.of(), List.of(comment)));
  }

  public Page<IssueCommentInfo> toCommentInfoPage(final Page<IssueComment> page) {
    final var lookup = this.lookup(List.of(), page.getContent());
    return page.map(comment -> this.toCommentInfo(comment, lookup));
  }

  private IssueCommentInfo toCommentInfo(final IssueComment comment, final Lookup lookup) {
    return IssueCommentInfo.builder()
        .id(comment.getId())
        .author(lookup.author(comment.getAuthorId()))
        .body(comment.getBody())
        .createdAt(comment.getCreatedAt())
        .updatedAt(comment.getUpdatedAt())
        .build();
  }
}
