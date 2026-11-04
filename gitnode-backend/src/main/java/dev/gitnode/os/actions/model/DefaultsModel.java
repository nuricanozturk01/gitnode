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
package dev.gitnode.os.actions.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** The {@code defaults:} block of a workflow or job. */
@NullMarked
public record DefaultsModel(@Nullable RunDefaults run) {

  /** Defaults applied to every {@code run:} step. */
  public record RunDefaults(
      @Nullable String shell,
      @JsonProperty("working-directory") @Nullable String workingDirectory) {}
}
