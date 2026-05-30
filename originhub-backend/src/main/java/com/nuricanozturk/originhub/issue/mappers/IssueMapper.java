package com.nuricanozturk.originhub.issue.mappers;

import com.nuricanozturk.originhub.issue.dtos.IssueCommentInfo;
import com.nuricanozturk.originhub.issue.dtos.IssueDetail;
import com.nuricanozturk.originhub.issue.dtos.IssueInfo;
import com.nuricanozturk.originhub.issue.entities.Issue;
import com.nuricanozturk.originhub.issue.entities.IssueComment;
import com.nuricanozturk.originhub.shared.commit.dtos.AuthorInfo;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
@NullMarked
public interface IssueMapper {

  default IssueInfo toInfo(final Issue issue, final int commentCount) {
    return IssueInfo.builder()
        .id(issue.getId())
        .number(issue.getNumber())
        .title(issue.getTitle())
        .status(issue.getStatus())
        .author(this.toAuthorInfo(issue.getAuthor()))
        .assignee(this.toAuthorInfo(issue.getAssignee()))
        .commentCount(commentCount)
        .createdAt(issue.getCreatedAt())
        .updatedAt(issue.getUpdatedAt())
        .closedAt(issue.getClosedAt())
        .build();
  }

  default IssueDetail toDetail(final Issue issue, final int commentCount) {
    return IssueDetail.builder()
        .id(issue.getId())
        .number(issue.getNumber())
        .title(issue.getTitle())
        .description(issue.getDescription())
        .status(issue.getStatus())
        .author(this.toAuthorInfo(issue.getAuthor()))
        .assignee(this.toAuthorInfo(issue.getAssignee()))
        .commentCount(commentCount)
        .createdAt(issue.getCreatedAt())
        .updatedAt(issue.getUpdatedAt())
        .closedAt(issue.getClosedAt())
        .build();
  }

  default IssueCommentInfo toCommentInfo(final IssueComment comment) {
    return IssueCommentInfo.builder()
        .id(comment.getId())
        .author(this.toAuthorInfo(comment.getAuthor()))
        .body(comment.getBody())
        .createdAt(comment.getCreatedAt())
        .updatedAt(comment.getUpdatedAt())
        .build();
  }

  default @Nullable AuthorInfo toAuthorInfo(final @Nullable Tenant tenant) {
    if (tenant == null) {
      return null;
    }
    return new AuthorInfo(
        tenant.getDisplayName(), tenant.getEmail(), tenant.getUsername(), tenant.getAvatarUrl());
  }
}
