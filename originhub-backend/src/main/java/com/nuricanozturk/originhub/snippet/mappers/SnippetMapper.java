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
package com.nuricanozturk.originhub.snippet.mappers;

import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.snippet.dtos.SnippetCommentInfo;
import com.nuricanozturk.originhub.snippet.dtos.SnippetDetail;
import com.nuricanozturk.originhub.snippet.dtos.SnippetFileInfo;
import com.nuricanozturk.originhub.snippet.dtos.SnippetForkedFromInfo;
import com.nuricanozturk.originhub.snippet.dtos.SnippetInfo;
import com.nuricanozturk.originhub.snippet.dtos.SnippetOwnerInfo;
import com.nuricanozturk.originhub.snippet.dtos.SnippetRevisionDetail;
import com.nuricanozturk.originhub.snippet.dtos.SnippetRevisionInfo;
import com.nuricanozturk.originhub.snippet.entities.Snippet;
import com.nuricanozturk.originhub.snippet.entities.SnippetComment;
import com.nuricanozturk.originhub.snippet.entities.SnippetFile;
import com.nuricanozturk.originhub.snippet.entities.SnippetRevision;
import com.nuricanozturk.originhub.snippet.entities.SnippetRevisionFile;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SnippetMapper {

  default @NonNull SnippetOwnerInfo toOwnerInfo(final @NonNull Tenant tenant) {
    return SnippetOwnerInfo.builder()
        .id(tenant.getId())
        .username(tenant.getUsername())
        .avatarUrl(null)
        .build();
  }

  default @NonNull SnippetFileInfo toFileInfo(
      final @NonNull SnippetFile file, final @NonNull String content) {
    return SnippetFileInfo.builder()
        .id(file.getId())
        .filename(file.getFilename())
        .content(content)
        .position(file.getPosition())
        .build();
  }

  default @NonNull SnippetFileInfo toRevisionFileInfo(
      final @NonNull SnippetRevisionFile file, final @NonNull String content) {
    return SnippetFileInfo.builder()
        .id(file.getId())
        .filename(file.getFilename())
        .content(content)
        .position(file.getPosition())
        .build();
  }

  default @Nullable SnippetForkedFromInfo toForkedFromInfo(final @Nullable Snippet forkedFrom) {
    if (forkedFrom == null) {
      return null;
    }
    return SnippetForkedFromInfo.builder()
        .id(forkedFrom.getId())
        .title(forkedFrom.getTitle())
        .owner(this.toOwnerInfo(forkedFrom.getOwner()))
        .build();
  }

  default @NonNull SnippetInfo toInfo(final @NonNull Snippet snippet) {
    return SnippetInfo.builder()
        .id(snippet.getId())
        .title(snippet.getTitle())
        .description(snippet.getDescription())
        .visibility(snippet.getVisibility())
        .owner(this.toOwnerInfo(snippet.getOwner()))
        .fileCount(snippet.getFileCount())
        .commentCount(snippet.getCommentCount())
        .forkCount(snippet.getForkCount())
        .forkedFrom(this.toForkedFromInfo(snippet.getForkedFrom()))
        .createdAt(snippet.getCreatedAt())
        .updatedAt(snippet.getUpdatedAt())
        .build();
  }

  default @NonNull SnippetDetail toDetail(
      final @NonNull Snippet snippet, final @NonNull Map<UUID, String> contentByFileId) {
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
        .files(files)
        .createdAt(snippet.getCreatedAt())
        .updatedAt(snippet.getUpdatedAt())
        .build();
  }

  default @NonNull SnippetCommentInfo toCommentInfo(final @NonNull SnippetComment comment) {
    return SnippetCommentInfo.builder()
        .id(comment.getId())
        .body(comment.getBody())
        .author(this.toOwnerInfo(comment.getAuthor()))
        .createdAt(comment.getCreatedAt())
        .updatedAt(comment.getUpdatedAt())
        .build();
  }

  default @NonNull SnippetRevisionInfo toRevisionInfo(final @NonNull SnippetRevision revision) {
    return SnippetRevisionInfo.builder()
        .id(revision.getId())
        .summary(revision.getSummary())
        .author(this.toOwnerInfo(revision.getAuthor()))
        .createdAt(revision.getCreatedAt())
        .build();
  }

  default @NonNull SnippetRevisionDetail toRevisionDetail(
      final @NonNull SnippetRevision revision,
      final @NonNull List<SnippetRevisionFile> files,
      final @NonNull Map<UUID, String> contentByFileId) {
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
