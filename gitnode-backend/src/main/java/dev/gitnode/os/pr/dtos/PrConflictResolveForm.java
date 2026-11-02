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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Resolution of every conflicting file, applied against the exact heads the user saw. */
@NullMarked
public record PrConflictResolveForm(
    @NotBlank String sourceSha,
    @NotBlank String targetSha,
    @NotEmpty @Size(max = 200) List<@Valid Resolution> files,
    @Nullable @Size(max = 500) String message) {

  public record Resolution(@NotBlank String path, @NotNull @Size(max = 1_048_576) String content) {}
}
