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
package dev.gitnode.os.repo.mappers;

import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.repo.dtos.RepoForkedFromInfo;
import dev.gitnode.os.repo.dtos.RepoInfo;
import dev.gitnode.os.repo.dtos.TenantRepoInfo;
import dev.gitnode.os.repo.entities.Repo;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
@NullMarked
public interface RepoMapper {

  @Mapping(
      target = "topics",
      expression =
          "java(repo.getTopics() != null ? repo.getTopics() : java.util.Collections.emptySet())")
  @Mapping(target = "isPrivate", source = "private")
  @Mapping(target = "isArchived", source = "archived")
  @Mapping(target = "owner", source = "ownerId")
  @Mapping(target = "forkedFrom", source = "forkedFrom")
  RepoInfo toDto(Repo repo, @Context Map<UUID, TenantRef> owners);

  default TenantRepoInfo mapOwner(final UUID ownerId, @Context final Map<UUID, TenantRef> owners) {
    final var owner = owners.get(ownerId);
    return owner == null
        ? null
        : new TenantRepoInfo(owner.id(), owner.username(), owner.avatarUrl());
  }

  default @Nullable RepoForkedFromInfo mapForkedFrom(
      final @Nullable Repo forkedFrom, @Context final Map<UUID, TenantRef> owners) {
    if (forkedFrom == null) {
      return null;
    }
    final var owner = owners.get(forkedFrom.getOwnerId());
    return new RepoForkedFromInfo(
        forkedFrom.getId(), owner == null ? "" : owner.username(), forkedFrom.getName());
  }
}
