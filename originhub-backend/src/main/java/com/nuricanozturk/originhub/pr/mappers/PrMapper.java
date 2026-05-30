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
package com.nuricanozturk.originhub.pr.mappers;

import com.nuricanozturk.originhub.pr.dtos.PrCommentInfo;
import com.nuricanozturk.originhub.pr.dtos.PrDetail;
import com.nuricanozturk.originhub.pr.dtos.PrForm;
import com.nuricanozturk.originhub.pr.dtos.PrInfo;
import com.nuricanozturk.originhub.pr.entities.PrStatus;
import com.nuricanozturk.originhub.pr.entities.PullRequest;
import com.nuricanozturk.originhub.pr.entities.PullRequestComment;
import com.nuricanozturk.originhub.shared.commit.dtos.AuthorInfo;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PrMapper {

  @BeanMapping(builder = @Builder())
  @Mapping(target = "author", source = "authorInfo")
  @Mapping(target = "mergedBy", source = "mergedBy")
  @Mapping(target = "commentCount", source = "commentCount")
  @Mapping(target = "isDraft", source = "pr.draft")
  @NonNull PrDetail toDetail(
      @NonNull PullRequest pr,
      int commentCount,
      @NonNull AuthorInfo authorInfo,
      @Nullable AuthorInfo mergedBy);

  @BeanMapping(builder = @Builder())
  @Mapping(target = "author", source = "authorInfo")
  @Mapping(target = "mergedBy", source = "mergedBy")
  @Mapping(target = "commentCount", source = "commentCount")
  @Mapping(target = "isDraft", source = "pr.draft")
  @NonNull PrInfo toInfo(
      @NonNull PullRequest pr,
      int commentCount,
      @NonNull AuthorInfo authorInfo,
      @Nullable AuthorInfo mergedBy);

  @BeanMapping(builder = @Builder())
  @Mapping(target = "author", source = "author")
  @Mapping(target = "isResolved", source = "comment.resolved")
  @NonNull PrCommentInfo toCommentInfo(
      @NonNull PullRequestComment comment, @NonNull AuthorInfo author);

  default @NonNull PullRequest buildPr(
      final @NonNull PrForm form,
      final @NonNull Repo repo,
      final @NonNull Tenant author,
      final @NonNull String sourceSha,
      final int nextNumber) {

    final var pr = new PullRequest();
    pr.setRepo(repo);
    pr.setCreatedAt(Instant.now());
    pr.setNumber(nextNumber);
    pr.setTitle(form.getTitle());
    pr.setDescription(form.getDescription());
    pr.setStatus(PrStatus.OPEN.name());
    pr.setAuthor(author);
    pr.setSourceBranch(form.getSourceBranch());
    pr.setSourceSha(sourceSha);
    pr.setTargetBranch(form.getTargetBranch());
    pr.setDraft(form.getIsDraft());

    return pr;
  }
}
