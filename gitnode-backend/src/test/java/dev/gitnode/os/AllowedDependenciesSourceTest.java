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
package dev.gitnode.os;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Spring Modulith verifies bytecode, where compile-time constants (for example {@code CacheNames})
 * are inlined and leave no dependency. IDEs analyse the source and flag them. This test reads the
 * imports so that {@code allowedDependencies} always matches what the source actually uses.
 */
class AllowedDependenciesSourceTest {

  private static final Path MAIN = Path.of("src/main/java/dev/gitnode/os");
  private static final Pattern NAMED = Pattern.compile("@NamedInterface\\(\"([^\"]+)\"\\)");
  private static final Pattern ALLOWED =
      Pattern.compile("allowedDependencies\\s*=\\s*\\{([^}]*)\\}", Pattern.DOTALL);
  private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");
  private static final Pattern REF =
      Pattern.compile("dev\\.gitnode\\.os\\.([a-z0-9_.]+)\\.[A-Z]\\w*");

  @Test
  void sourceImportsAreCoveredByAllowedDependencies() throws IOException {
    final Map<String, String> named = new HashMap<>();
    final Map<String, Set<String>> allowed = new HashMap<>();
    try (Stream<Path> files = Files.walk(MAIN)) {
      for (final Path file : files.filter(f -> f.endsWith("package-info.java")).toList()) {
        final String pkg = MAIN.relativize(file.getParent()).toString().replace('/', '.');
        final String text = Files.readString(file);
        final Matcher n = NAMED.matcher(text);
        if (n.find()) {
          named.put(pkg, n.group(1));
        }
        final Matcher a = ALLOWED.matcher(text);
        if (a.find() && !pkg.contains(".")) {
          final Set<String> declared = new TreeSet<>();
          final Matcher q = QUOTED.matcher(a.group(1));
          while (q.find()) {
            declared.add(q.group(1));
          }
          allowed.put(pkg, declared);
        }
      }
    }

    final Set<String> problems = new TreeSet<>();
    try (Stream<Path> files = Files.walk(MAIN)) {
      for (final Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
        final String module = MAIN.relativize(file).getName(0).toString();
        if (!allowed.containsKey(module)) {
          continue;
        }
        final Matcher m = REF.matcher(Files.readString(file));
        while (m.find()) {
          final String pkg = m.group(1);
          final String target = pkg.contains(".") ? pkg.substring(0, pkg.indexOf('.')) : pkg;
          if (target.equals(module) || !allowed.containsKey(target) && !"shared".equals(target)) {
            continue;
          }
          final String iface = namedInterfaceOf(pkg, named);
          if (iface != null && !allowed.get(module).contains(target + " :: " + iface)) {
            problems.add(
                module + " uses '" + target + " :: " + iface + "' (" + file.getFileName() + ")");
          }
        }
      }
    }
    assertThat(problems)
        .as("modules use named interfaces missing from allowedDependencies")
        .isEmpty();
  }

  private static String namedInterfaceOf(final String pkg, final Map<String, String> named) {
    String candidate = pkg;
    while (!candidate.isEmpty()) {
      if (named.containsKey(candidate)) {
        return named.get(candidate);
      }
      final int dot = candidate.lastIndexOf('.');
      candidate = dot < 0 ? "" : candidate.substring(0, dot);
    }
    return null;
  }
}
