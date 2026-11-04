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
package dev.gitnode.os.actions.services;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Runner labels the way GitHub treats self-hosted runners: lower-case (labels are compared
 * case-insensitively) and every runner always offers {@code self-hosted}, its OS and its
 * architecture, so {@code runs-on: [self-hosted, linux]} finds a default Linux runner. GitNode has
 * no GitHub-hosted images, so names like {@code ubuntu-latest} are not translated; a job asking for
 * them waits for a runner that carries that label.
 */
@NullMarked
final class RunnerLabels {

  static final String SELF_HOSTED = "self-hosted";

  private RunnerLabels() {}

  /** The labels a job needs from a runner: runs-on, lower-case; self-hosted when there is none. */
  static List<String> forJob(final @Nullable List<String> runsOn) {

    final var out = new LinkedHashSet<String>();
    if (runsOn != null) {
      for (final var raw : runsOn) {
        if (raw != null && !raw.isBlank()) {
          out.add(raw.strip().toLowerCase(Locale.ROOT));
        }
      }
    }
    if (out.isEmpty()) {
      out.add(SELF_HOSTED);
    }
    return List.copyOf(out);
  }

  /** The labels a runner offers: the ones it was given plus self-hosted, OS and architecture. */
  static List<String> forRunner(
      final List<String> given, final @Nullable String os, final @Nullable String arch) {

    final var out = new LinkedHashSet<String>();
    out.add(SELF_HOSTED);
    for (final var raw : given) {
      if (raw != null && !raw.isBlank()) {
        out.add(raw.strip().toLowerCase(Locale.ROOT));
      }
    }
    osLabel(os).ifPresent(out::add);
    archLabel(arch).ifPresent(out::add);
    return new ArrayList<>(out);
  }

  private static java.util.Optional<String> osLabel(final @Nullable String os) {
    if (os == null) {
      return java.util.Optional.empty();
    }
    return switch (os.strip().toLowerCase(Locale.ROOT)) {
      case "linux" -> java.util.Optional.of("linux");
      case "darwin", "macos", "osx" -> java.util.Optional.of("macos");
      case "windows" -> java.util.Optional.of("windows");
      default -> java.util.Optional.empty();
    };
  }

  private static java.util.Optional<String> archLabel(final @Nullable String arch) {
    if (arch == null) {
      return java.util.Optional.empty();
    }
    return switch (arch.strip().toLowerCase(Locale.ROOT)) {
      case "amd64", "x86_64", "x64" -> java.util.Optional.of("x64");
      case "arm64", "aarch64" -> java.util.Optional.of("arm64");
      case "arm" -> java.util.Optional.of("arm");
      case "386", "x86" -> java.util.Optional.of("x86");
      default -> java.util.Optional.empty();
    };
  }
}
