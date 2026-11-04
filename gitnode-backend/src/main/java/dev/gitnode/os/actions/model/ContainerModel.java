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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A job's {@code container:}. GitHub accepts a bare image name or the full mapping; both parse to
 * this record.
 */
@NullMarked
public record ContainerModel(
    String image,
    @Nullable Map<String, String> env,
    @Nullable List<String> ports,
    @Nullable List<String> volumes,
    @Nullable String options) {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  static ContainerModel fromYaml(final Object raw) {
    if (raw instanceof final String image) {
      return new ContainerModel(image.strip(), null, null, null, null);
    }
    return MAPPER.convertValue(raw, Raw.class).toModel();
  }

  private record Raw(
      @JsonProperty("image") String image,
      @JsonProperty("env") @Nullable Map<String, String> env,
      @JsonProperty("ports") @Nullable List<String> ports,
      @JsonProperty("volumes") @Nullable List<String> volumes,
      @JsonProperty("options") @Nullable String options) {

    ContainerModel toModel() {
      return new ContainerModel(
          this.image == null ? "" : this.image.strip(),
          this.env,
          this.ports,
          this.volumes,
          this.options);
    }
  }
}
