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

package dev.gitnode.os.pr.dtos;

import dev.gitnode.os.shared.commit.dtos.AuthorInfo;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Labels, assignees and milestone of a pull request. */
@NullMarked
public record PrMetadata(
    List<Label> labels,
    List<AuthorInfo> assignees,
    @Nullable Milestone milestone,
    boolean canEdit) {

  public record Label(UUID id, String name, String color, @Nullable String description) {}

  public record Milestone(UUID id, String title, String status) {}

  /** The full new state; omitted lists clear the corresponding field. */
  public record Form(
      @NotNull @Size(max = 20) List<UUID> labelIds,
      @NotNull @Size(max = 10) List<String> assignees,
      @Nullable UUID milestoneId) {}
}
