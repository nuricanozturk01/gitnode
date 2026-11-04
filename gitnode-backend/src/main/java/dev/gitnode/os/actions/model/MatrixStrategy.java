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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A job's {@code strategy.matrix}. Either the usual mapping (dimensions plus {@code include} and
 * {@code exclude}) or, as on GitHub, an expression such as {@code ${{
 * fromJSON(needs.plan.outputs.m) }}} that only yields the mapping once the jobs it needs have
 * finished.
 *
 * <p>Every value is kept as a string. Scalars are stringified, nested objects and lists become
 * their JSON text.
 */
@NullMarked
public record MatrixStrategy(
    Map<String, List<String>> dimensions,
    @Nullable List<Map<String, String>> include,
    @Nullable List<Map<String, String>> exclude,
    @Nullable String expression) {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public MatrixStrategy {
    dimensions = dimensions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(dimensions);
  }

  public MatrixStrategy(
      final Map<String, List<String>> dimensions,
      final @Nullable List<Map<String, String>> include,
      final @Nullable List<Map<String, String>> exclude) {
    this(dimensions, include, exclude, null);
  }

  public MatrixStrategy() {
    this(new LinkedHashMap<>(), null, null, null);
  }

  /** True when the matrix is an expression that is evaluated when the job's needs are done. */
  public boolean isDynamic() {
    return this.expression != null && !this.expression.isBlank();
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  static MatrixStrategy fromYaml(final Object raw) {
    if (raw instanceof final String text) {
      return new MatrixStrategy(new LinkedHashMap<>(), null, null, text);
    }
    if (raw instanceof final Map<?, ?> map) {
      return fromMap(map);
    }
    throw new IllegalArgumentException("strategy.matrix must be a mapping or an expression");
  }

  /** The matrix described by an expression's JSON result, e.g. {"include":[{"os":"a"}]}. */
  public static MatrixStrategy fromJson(final String json) {
    try {
      final var parsed = MAPPER.readValue(json, Object.class);
      if (parsed instanceof final Map<?, ?> map) {
        return fromMap(map);
      }
    } catch (final JsonProcessingException e) {
      throw new IllegalArgumentException(
          "matrix expression is not valid JSON: " + e.getOriginalMessage());
    }
    throw new IllegalArgumentException("matrix expression must evaluate to an object");
  }

  private static MatrixStrategy fromMap(final Map<?, ?> map) {
    final var dimensions = new LinkedHashMap<String, List<String>>();
    List<Map<String, String>> include = null;
    List<Map<String, String>> exclude = null;
    for (final var entry : map.entrySet()) {
      final var key = String.valueOf(entry.getKey());
      switch (key) {
        case "include" -> include = entries(entry.getValue());
        case "exclude" -> exclude = entries(entry.getValue());
        default -> dimensions.put(key, values(entry.getValue()));
      }
    }
    return new MatrixStrategy(dimensions, include, exclude, null);
  }

  private static List<Map<String, String>> entries(final @Nullable Object raw) {
    final var out = new ArrayList<Map<String, String>>();
    if (raw instanceof final List<?> list) {
      for (final var item : list) {
        if (item instanceof final Map<?, ?> map) {
          final var entry = new LinkedHashMap<String, String>();
          map.forEach((k, v) -> entry.put(String.valueOf(k), text(v)));
          out.add(entry);
        }
      }
    }
    return out;
  }

  private static List<String> values(final @Nullable Object raw) {
    final var out = new ArrayList<String>();
    if (raw instanceof final List<?> list) {
      list.forEach(item -> out.add(text(item)));
    } else if (raw != null) {
      out.add(text(raw));
    }
    return out;
  }

  private static String text(final @Nullable Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof Map<?, ?> || value instanceof List<?>) {
      try {
        return MAPPER.writeValueAsString(value);
      } catch (final JsonProcessingException e) {
        return String.valueOf(value);
      }
    }
    return String.valueOf(value);
  }
}
