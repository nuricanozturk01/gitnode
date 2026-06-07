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
package com.nuricanozturk.originhub.actions.services;

import com.nuricanozturk.originhub.actions.model.JobModel;
import com.nuricanozturk.originhub.actions.model.MatrixStrategy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@NullMarked
public class MatrixExpander {

  private static final Map<String, String> EMPTY_COMBINATION = Map.of();

  public List<Map<String, String>> expand(final JobModel job) {

    final var strategy = job.strategy();
    final var matrix = strategy == null ? null : strategy.matrix();

    if (matrix == null || matrix.dimensions().isEmpty()) {
      return this.handleNullOrEmptyDimensions(matrix);
    }

    var combinations = this.cartesianProduct(matrix.dimensions());
    combinations = this.applyExclude(combinations, matrix.exclude());
    combinations = this.applyInclude(combinations, matrix.include());

    return combinations.isEmpty() ? List.of(EMPTY_COMBINATION) : combinations;
  }

  private List<Map<String, String>> handleNullOrEmptyDimensions(
      final @Nullable MatrixStrategy matrix) {
    if (matrix != null && matrix.include() != null && !matrix.include().isEmpty()) {
      return new ArrayList<>(matrix.include());
    }
    return List.of(EMPTY_COMBINATION);
  }

  private List<Map<String, String>> cartesianProduct(final Map<String, List<String>> dimensions) {

    var result = new ArrayList<Map<String, String>>();
    result.add(new LinkedHashMap<>());

    for (final var entry : dimensions.entrySet()) {
      final var key = entry.getKey();
      final var values = entry.getValue();
      final var expanded = new ArrayList<Map<String, String>>();

      for (final var base : result) {
        for (final var value : values) {
          final var next = new LinkedHashMap<>(base);
          next.put(key, value);
          expanded.add(next);
        }
      }
      result = expanded;
    }

    return new ArrayList<>(result);
  }

  private List<Map<String, String>> applyExclude(
      final List<Map<String, String>> combinations,
      final @Nullable List<Map<String, String>> exclude) {

    if (exclude == null || exclude.isEmpty()) {
      return combinations;
    }

    final var result = new ArrayList<Map<String, String>>();
    for (final var combination : combinations) {
      if (exclude.stream().noneMatch(ex -> this.isSubset(ex, combination))) {
        result.add(combination);
      }
    }
    return result;
  }

  private List<Map<String, String>> applyInclude(
      final List<Map<String, String>> combinations,
      final @Nullable List<Map<String, String>> include) {

    if (include == null || include.isEmpty()) {
      return combinations;
    }

    final var result = new ArrayList<>(combinations);
    for (final var extra : include) {
      result.add(new LinkedHashMap<>(extra));
    }
    return result;
  }

  private boolean isSubset(final Map<String, String> subset, final Map<String, String> full) {
    return subset.entrySet().stream().allMatch(e -> e.getValue().equals(full.get(e.getKey())));
  }
}
