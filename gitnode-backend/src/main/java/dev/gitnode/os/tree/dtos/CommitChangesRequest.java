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

package dev.gitnode.os.tree.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Several file changes committed to one branch as a single commit. {@code expectedHeadSha} is the
 * branch head the editor was based on; the commit is refused if the branch moved since.
 */
@NullMarked
public record CommitChangesRequest(
    @NotBlank @Size(max = 255) String branch,
    @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{40}$") String expectedHeadSha,
    @NotBlank @Size(max = 500) String message,
    @Nullable @Size(max = 5000) String description,
    @NotEmpty @Size(max = 100) List<@Valid Change> changes) {

  /** A created/updated file when {@code delete} is false (content required), else a deletion. */
  public record Change(
      @NotBlank @Size(max = 4096) String path,
      @Nullable @Size(max = 2_000_000) String content,
      boolean delete) {}
}
