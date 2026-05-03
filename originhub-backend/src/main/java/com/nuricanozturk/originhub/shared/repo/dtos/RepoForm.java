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
package com.nuricanozturk.originhub.shared.repo.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepoForm {

  @NotNull
  @Size(min = 1, max = 100)
  @Pattern(
      regexp = "^[a-zA-Z0-9_\\-\\.]+$",
      message = "Repository name can only contain letters, numbers, hyphens, underscores and dots")
  private String name;

  @Size(max = 500)
  private String description;

  @Size(max = 6, message = "Maximum 6 topics allowed")
  private Set<@NotNull @Size(min = 1, max = 50) @Pattern(regexp = "^[a-zA-Z0-9\\-]+$") String>
      topics;
}
