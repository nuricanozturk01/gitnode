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
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
@NullMarked
public interface IssueMapper {

  @BeanMapping(builder = @Builder())
  @Mapping(target = "commentCount", source = "commentCount")
  IssueInfo toInfo(Issue issue, int commentCount);

  @BeanMapping(builder = @Builder())
  @Mapping(target = "commentCount", source = "commentCount")
  IssueDetail toDetail(Issue issue, int commentCount);

  @BeanMapping(builder = @Builder())
  IssueCommentInfo toCommentInfo(IssueComment comment);

  @Mapping(target = "name", source = "displayName")
  @Nullable AuthorInfo toAuthorInfo(@Nullable Tenant tenant);
}
