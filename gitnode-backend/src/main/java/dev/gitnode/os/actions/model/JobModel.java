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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import dev.gitnode.os.actions.model.deserializer.StringOrListDeserializer;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record JobModel(
    @Nullable String name,
    @JsonProperty("runs-on") @JsonDeserialize(using = StringOrListDeserializer.class)
        @Nullable List<String> runsOn,
    @JsonProperty("timeout-minutes") @Nullable Integer timeoutMinutes,
    @JsonDeserialize(using = StringOrListDeserializer.class) @Nullable List<String> needs,
    @JsonProperty("if") @Nullable String condition,
    @Nullable Map<String, String> env,
    @Nullable StrategyModel strategy,
    @Nullable Map<String, ServiceModel> services,
    @Nullable List<StepModel> steps,
    @Nullable DefaultsModel defaults) {

  public JobModel(
      final @Nullable String name,
      final @Nullable List<String> runsOn,
      final @Nullable Integer timeoutMinutes,
      final @Nullable List<String> needs,
      final @Nullable String condition,
      final @Nullable Map<String, String> env,
      final @Nullable StrategyModel strategy,
      final @Nullable Map<String, ServiceModel> services,
      final @Nullable List<StepModel> steps) {
    this(name, runsOn, timeoutMinutes, needs, condition, env, strategy, services, steps, null);
  }
}
