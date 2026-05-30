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
package com.nuricanozturk.originhub.tree.utils;

import java.util.Locale;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@UtilityClass
public class LanguageExtensionUtils {

  private static final @NonNull Map<@NonNull String, @NonNull String> EXTENSION_LANGUAGE_MAP =
      Map.ofEntries(
          Map.entry("java", "java"),
          Map.entry("ts", "typescript"),
          Map.entry("tsx", "typescript"),
          Map.entry("js", "javascript"),
          Map.entry("jsx", "javascript"),
          Map.entry("py", "python"),
          Map.entry("go", "go"),
          Map.entry("rs", "rust"),
          Map.entry("kt", "kotlin"),
          Map.entry("md", "markdown"),
          Map.entry("yml", "yaml"),
          Map.entry("yaml", "yaml"),
          Map.entry("json", "json"),
          Map.entry("xml", "xml"),
          Map.entry("html", "html"),
          Map.entry("css", "css"),
          Map.entry("scss", "css"),
          Map.entry("sass", "css"),
          Map.entry("sh", "bash"),
          Map.entry("bash", "bash"),
          Map.entry("sql", "sql"),
          Map.entry("dockerfile", "dockerfile"),
          Map.entry("toml", "toml"),
          Map.entry("properties", "properties"));

  private static final @NonNull String DEFAULT_LANGUAGE = "plaintext";

  public static @NonNull String detectLanguage(final @Nullable String fileName) {

    if (fileName == null) {
      return DEFAULT_LANGUAGE;
    }

    final var lastDot = fileName.lastIndexOf('.');

    if (lastDot < 0 || lastDot == fileName.length() - 1) {
      return DEFAULT_LANGUAGE;
    }

    final var extension = fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT);

    return EXTENSION_LANGUAGE_MAP.getOrDefault(extension, DEFAULT_LANGUAGE);
  }
}
