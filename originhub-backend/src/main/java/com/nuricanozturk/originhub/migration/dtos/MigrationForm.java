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
package com.nuricanozturk.originhub.migration.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.Data;
import org.jspecify.annotations.NullMarked;

@Data
@NullMarked
public class MigrationForm {

  @NotNull private MigrationService service;

  @NotNull
  @NotBlank
  @Pattern(
      regexp = "^(https?://)?(www\\.)?github\\.com/[\\w.-]+/[\\w.-]+$",
      message = "Must be a valid GitHub repository URL (e.g. github.com/user/repo)")
  private String url;

  @NotNull @NotBlank private String accessToken;

  @NotNull @NotEmpty private List<MigrationItem> migrationItems;

  @NotNull @NotBlank private String owner;

  @NotNull @NotBlank private String repoName;
}
