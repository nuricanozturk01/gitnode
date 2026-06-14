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
package dev.gitnode.os.shared.repo.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

@AllArgsConstructor
@Getter
public class RepoInfo implements Serializable {
  private final UUID id;
  private final TenantRepoInfo owner;
  private final String name;
  private final String description;

  @JsonProperty("isPrivate")
  private final boolean isPrivate;

  @JsonProperty("isArchived")
  private final boolean isArchived;

  private final String defaultBranch;
  private final Set<String> topics;
  private final boolean deleteHeadBranchOnPrMerge;
  private final boolean deleteHeadBranchOnPrClose;
  private final boolean aiPrReviewEnabled;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final @Nullable RepoForkedFromInfo forkedFrom;
  private final int forkCount;
}
