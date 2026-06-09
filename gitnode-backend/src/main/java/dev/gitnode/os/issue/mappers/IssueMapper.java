package dev.gitnode.os.issue.mappers;

import dev.gitnode.os.issue.dtos.IssueCommentInfo;
import dev.gitnode.os.issue.dtos.IssueDetail;
import dev.gitnode.os.issue.dtos.IssueInfo;
import dev.gitnode.os.issue.entities.Issue;
import dev.gitnode.os.issue.entities.IssueComment;
import dev.gitnode.os.shared.commit.dtos.AuthorInfo;
import dev.gitnode.os.shared.tenant.entities.Tenant;
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
