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
package dev.gitnode.os.shared.repo.mappers;

import dev.gitnode.os.shared.repo.dtos.RepoForkedFromInfo;
import dev.gitnode.os.shared.repo.dtos.RepoInfo;
import dev.gitnode.os.shared.repo.dtos.TenantRepoInfo;
import dev.gitnode.os.shared.repo.entities.Repo;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
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
  @Mapping(target = "forkedFrom", source = "forkedFrom")
  RepoInfo toDto(Repo repo);

  TenantRepoInfo mapOwner(Tenant owner);

  @Mapping(target = "ownerUsername", source = "owner.username")
  @Nullable RepoForkedFromInfo mapForkedFrom(@Nullable Repo forkedFrom);
}
