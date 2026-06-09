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
package dev.gitnode.os.collaborator.mappers;

import dev.gitnode.os.collaborator.dtos.CollaboratorInfo;
import dev.gitnode.os.collaborator.entities.RepoCollaborator;
import org.apache.commons.codec.digest.DigestUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
@NullMarked
public interface CollaboratorMapper {

  @BeanMapping(builder = @Builder())
  @Mapping(target = "id", source = "id")
  @Mapping(target = "username", source = "tenant.username")
  @Mapping(target = "displayName", source = "tenant.displayName")
  @Mapping(target = "avatarUrl", source = "collaborator", qualifiedByName = "resolveAvatarUrl")
  @Mapping(target = "permissions", source = "permissions")
  @Mapping(target = "status", source = "status")
  @Mapping(target = "invitedBy", source = "invitedBy.username")
  @Mapping(target = "createdAt", source = "createdAt")
  @Mapping(target = "updatedAt", source = "updatedAt")
  CollaboratorInfo toInfo(RepoCollaborator collaborator);

  @Named("resolveAvatarUrl")
  default String resolveAvatarUrl(final RepoCollaborator collaborator) {
    final @Nullable String stored = collaborator.getTenant().getAvatarUrl();
    if (stored != null && !stored.isBlank()) {
      return stored;
    }
    final var hash =
        DigestUtils.md5Hex(
            collaborator.getTenant().getEmail().trim().toLowerCase(java.util.Locale.getDefault()));
    return "https://www.gravatar.com/avatar/" + hash + "?d=identicon";
  }
}
