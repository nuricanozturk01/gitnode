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
package com.nuricanozturk.originhub.tag.mappers;

import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.tag.dtos.ReleaseInfo;
import com.nuricanozturk.originhub.tag.dtos.TenantReleaseInfo;
import com.nuricanozturk.originhub.tag.entities.Release;
import org.jspecify.annotations.NullMarked;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
@NullMarked
public interface ReleaseMapper {

  default ReleaseInfo toInfo(final Release release) {
    return ReleaseInfo.builder()
        .id(release.getId())
        .tagName(release.getTagName())
        .name(release.getName())
        .body(release.getBody())
        .isDraft(release.isDraft())
        .isPrerelease(release.isPrerelease())
        .author(this.toTenantInfo(release.getAuthor()))
        .publishedAt(release.getPublishedAt())
        .createdAt(release.getCreatedAt())
        .updatedAt(release.getUpdatedAt())
        .build();
  }

  default TenantReleaseInfo toTenantInfo(final Tenant tenant) {
    return new TenantReleaseInfo(
        tenant.getId(), tenant.getUsername(), tenant.getDisplayName(), tenant.getAvatarUrl());
  }
}
