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
package com.nuricanozturk.originhub.actions.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record MatrixStrategy(
    Map<String, List<String>> dimensions,
    @JsonProperty("include") @Nullable List<Map<String, String>> include,
    @JsonProperty("exclude") @Nullable List<Map<String, String>> exclude) {

  public MatrixStrategy {
    dimensions = dimensions == null ? new HashMap<>() : new HashMap<>(dimensions);
  }

  public MatrixStrategy() {
    this(new HashMap<>(), null, null);
  }

  @JsonAnySetter
  void addDimension(final String key, final List<String> values) {
    this.dimensions.put(key, values);
  }
}
