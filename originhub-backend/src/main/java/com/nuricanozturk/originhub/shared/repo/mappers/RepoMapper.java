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
package com.nuricanozturk.originhub.shared.repo.mappers;

import com.nuricanozturk.originhub.shared.repo.dtos.RepoInfo;
import com.nuricanozturk.originhub.shared.repo.dtos.TenantRepoInfo;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import org.jspecify.annotations.NullMarked;
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
  /**
   * Lombok exposes {@code isPrivate()} as bean property {@code private}; MapStruct would otherwise
   * default to false.
   */
  @Mapping(target = "isPrivate", source = "private")
  @Mapping(target = "isArchived", source = "archived")
  RepoInfo toDto(Repo repo);

  default TenantRepoInfo mapOwner(final Tenant owner) {

    if (owner == null) {
      return null;
    }

    return new TenantRepoInfo(owner.getId(), owner.getUsername(), owner.getAvatarUrl());
  }
}
