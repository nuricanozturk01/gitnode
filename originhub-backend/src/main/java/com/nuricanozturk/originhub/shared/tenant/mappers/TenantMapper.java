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
package com.nuricanozturk.originhub.shared.tenant.mappers;

import com.nuricanozturk.originhub.shared.tenant.dtos.TenantInfo;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import org.jspecify.annotations.NonNull;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TenantMapper {

  @Mapping(
      target = "displayName",
      expression =
          "java(tenant.getDisplayName() != null ? tenant.getDisplayName() : tenant.getUsername())")
  @NonNull TenantInfo toTenantInfo(@NonNull Tenant tenant);
}
