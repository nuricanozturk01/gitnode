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
package dev.gitnode.os.snippet.mappers;

import dev.gitnode.os.shared.repo.entities.Repo;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.snippet.dtos.SnippetCommentInfo;
import dev.gitnode.os.snippet.dtos.SnippetDetail;
import dev.gitnode.os.snippet.dtos.SnippetFileInfo;
import dev.gitnode.os.snippet.dtos.SnippetForkedFromInfo;
import dev.gitnode.os.snippet.dtos.SnippetInfo;
import dev.gitnode.os.snippet.dtos.SnippetLinkedRepoInfo;
import dev.gitnode.os.snippet.dtos.SnippetOwnerInfo;
import dev.gitnode.os.snippet.dtos.SnippetRevisionDetail;
import dev.gitnode.os.snippet.dtos.SnippetRevisionInfo;
import dev.gitnode.os.snippet.entities.Snippet;
import dev.gitnode.os.snippet.entities.SnippetComment;
import dev.gitnode.os.snippet.entities.SnippetFile;
import dev.gitnode.os.snippet.entities.SnippetRevision;
import dev.gitnode.os.snippet.entities.SnippetRevisionFile;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
@NullMarked
public interface SnippetMapper {

  @BeanMapping(builder = @Builder())
  @Mapping(target = "avatarUrl", ignore = true)
  SnippetOwnerInfo toOwnerInfo(Tenant tenant);

  @BeanMapping(builder = @Builder())
  @Mapping(target = "content", source = "content")
  SnippetFileInfo toFileInfo(SnippetFile file, String content);

  @BeanMapping(builder = @Builder())
  @Mapping(target = "content", source = "content")
  SnippetFileInfo toRevisionFileInfo(SnippetRevisionFile file, String content);

  @BeanMapping(builder = @Builder())
  @Nullable SnippetForkedFromInfo toForkedFromInfo(@Nullable Snippet forkedFrom);

  @BeanMapping(builder = @Builder())
  SnippetLinkedRepoInfo toLinkedRepoInfo(Repo repo);

  @BeanMapping(builder = @Builder())
  SnippetInfo toInfo(Snippet snippet);

  @BeanMapping(builder = @Builder())
  SnippetCommentInfo toCommentInfo(SnippetComment comment);

  @BeanMapping(builder = @Builder())
  SnippetRevisionInfo toRevisionInfo(SnippetRevision revision);

  default SnippetDetail toDetail(final Snippet snippet, final Map<UUID, String> contentByFileId) {
    final var files =
        snippet.getFiles().stream()
            .map(f -> this.toFileInfo(f, contentByFileId.getOrDefault(f.getId(), "")))
            .toList();

    return SnippetDetail.builder()
        .id(snippet.getId())
        .title(snippet.getTitle())
        .description(snippet.getDescription())
        .visibility(snippet.getVisibility())
        .owner(this.toOwnerInfo(snippet.getOwner()))
        .fileCount(snippet.getFileCount())
        .commentCount(snippet.getCommentCount())
        .forkCount(snippet.getForkCount())
        .forkedFrom(this.toForkedFromInfo(snippet.getForkedFrom()))
        .repos(snippet.getRepos().stream().map(this::toLinkedRepoInfo).toList())
        .files(files)
        .createdAt(snippet.getCreatedAt())
        .updatedAt(snippet.getUpdatedAt())
        .build();
  }

  default SnippetRevisionDetail toRevisionDetail(
      final SnippetRevision revision,
      final List<SnippetRevisionFile> files,
      final Map<UUID, String> contentByFileId) {
    return SnippetRevisionDetail.builder()
        .id(revision.getId())
        .title(revision.getTitle())
        .description(revision.getDescription())
        .summary(revision.getSummary())
        .author(this.toOwnerInfo(revision.getAuthor()))
        .files(
            files.stream()
                .map(f -> this.toRevisionFileInfo(f, contentByFileId.getOrDefault(f.getId(), "")))
                .toList())
        .createdAt(revision.getCreatedAt())
        .build();
  }
}
