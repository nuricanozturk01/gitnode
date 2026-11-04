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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@code on:} block. Accepts every form GitHub does: a single event ({@code on: push}), a list
 * ({@code on: [push, pull_request]}) and the mapping form, where an event may be written without a
 * value ({@code workflow_dispatch:}).
 */
@NullMarked
public record OnTriggerModel(
    @Nullable PushTriggerModel push,
    @JsonProperty("pull_request") @Nullable PullRequestTriggerModel pullRequest,
    @JsonProperty("workflow_dispatch") @Nullable WorkflowDispatchModel workflowDispatch,
    @Nullable List<ScheduleModel> schedule) {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  public OnTriggerModel(
      final @Nullable PushTriggerModel push,
      final @Nullable PullRequestTriggerModel pullRequest,
      final @Nullable WorkflowDispatchModel workflowDispatch) {
    this(push, pullRequest, workflowDispatch, null);
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  static OnTriggerModel fromYaml(final @Nullable Object raw) {
    if (raw instanceof final String name) {
      return fromMap(Map.of(name, Map.of()));
    }
    if (raw instanceof final List<?> names) {
      final var map = new java.util.LinkedHashMap<String, Object>();
      for (final Object name : names) {
        if (name != null) {
          map.put(name.toString(), Map.of());
        }
      }
      return fromMap(map);
    }
    if (raw instanceof final Map<?, ?> map) {
      return fromMap(map);
    }
    return new OnTriggerModel(null, null, null, null);
  }

  private static OnTriggerModel fromMap(final Map<?, ?> map) {
    final var schedule =
        map.get("schedule") == null
            ? null
            : MAPPER.convertValue(map.get("schedule"), new TypeReference<List<ScheduleModel>>() {});
    return new OnTriggerModel(
        read(map, "push", PushTriggerModel.class),
        read(map, "pull_request", PullRequestTriggerModel.class),
        read(map, "workflow_dispatch", WorkflowDispatchModel.class),
        schedule);
  }

  /** Absent key → null (trigger not used); key without value → an empty trigger. */
  private static <T> @Nullable T read(final Map<?, ?> map, final String key, final Class<T> type) {
    if (!map.containsKey(key)) {
      return null;
    }
    final Object value = map.get(key);
    return MAPPER.convertValue(value == null ? Map.of() : value, type);
  }
}
