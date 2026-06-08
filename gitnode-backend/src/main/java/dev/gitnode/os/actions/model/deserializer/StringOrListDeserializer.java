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
package dev.gitnode.os.actions.model.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Handles YAML runs-on: that may be a single string or a list of strings. */
public class StringOrListDeserializer extends StdDeserializer<List<String>> {

  public StringOrListDeserializer() {
    super(List.class);
  }

  @Override
  public List<String> deserialize(final JsonParser p, final DeserializationContext ctxt)
      throws IOException {

    if (p.currentToken() == JsonToken.START_ARRAY) {
      final var list = new ArrayList<String>();
      while (p.nextToken() != JsonToken.END_ARRAY) {
        list.add(p.getValueAsString());
      }
      return List.copyOf(list);
    }

    return List.of(p.getValueAsString());
  }
}
