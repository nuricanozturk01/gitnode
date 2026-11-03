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
package dev.gitnode.os.pr.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;

/**
 * A parsed CODEOWNERS file. Patterns follow gitignore rules and, as on GitHub, the last matching
 * line decides who owns a path. Only {@code @username} owners are understood; teams and e-mail
 * addresses are skipped because GitNode has no organizations.
 */
@NullMarked
final class CodeOwnersFile {

  /** The locations searched on the target branch, in order of precedence. */
  static final List<String> LOCATIONS =
      List.of(".gitnode/CODEOWNERS", ".github/CODEOWNERS", "CODEOWNERS", "docs/CODEOWNERS");

  private static final String ANY_DIRECTORIES = "**/";

  /** One CODEOWNERS line; {@code owners} is empty when the line un-owns the matched paths. */
  record Rule(String pattern, Pattern regex, List<String> owners) {}

  private final List<Rule> rules;

  private CodeOwnersFile(final List<Rule> rules) {
    this.rules = rules;
  }

  static CodeOwnersFile empty() {
    return new CodeOwnersFile(List.of());
  }

  static CodeOwnersFile parse(final String content) {
    final var rules = new ArrayList<Rule>();
    for (final var raw : content.split("\\R")) {
      final var tokens = tokens(raw);
      if (tokens.isEmpty()) {
        continue;
      }
      final var pattern = tokens.get(0);
      final var owners =
          tokens.stream()
              .skip(1)
              .filter(token -> token.startsWith("@") && token.length() > 1)
              .map(token -> token.substring(1).toLowerCase(Locale.ROOT))
              .filter(name -> name.indexOf('/') < 0)
              .distinct()
              .toList();
      rules.add(new Rule(pattern, toRegex(pattern), owners));
    }
    return new CodeOwnersFile(List.copyOf(rules));
  }

  boolean isEmpty() {
    return this.rules.isEmpty();
  }

  /** The rule owning {@code path}: the last one whose pattern matches. */
  Optional<Rule> ruleFor(final String path) {
    for (int i = this.rules.size() - 1; i >= 0; i--) {
      if (this.rules.get(i).regex().matcher(path).matches()) {
        return Optional.of(this.rules.get(i));
      }
    }
    return Optional.empty();
  }

  private static List<String> tokens(final String line) {
    final var tokens = new ArrayList<String>();
    for (final var token : line.strip().split("\\s+")) {
      if (token.isEmpty()) {
        continue;
      }
      if (token.startsWith("#")) {
        break;
      }
      tokens.add(token);
    }
    return tokens;
  }

  private static Pattern toRegex(final String raw) {
    var pattern = raw;
    final var directoryOnly = pattern.endsWith("/");
    if (directoryOnly) {
      pattern = pattern.substring(0, pattern.length() - 1);
    }
    final var anchored = pattern.startsWith("/") || pattern.indexOf('/') >= 0;
    if (pattern.startsWith("/")) {
      pattern = pattern.substring(1);
    }
    final var regex = new StringBuilder(anchored ? "^" : "^(?:.*/)?");
    appendGlob(regex, pattern);
    regex.append(directoryOnly ? "/.*$" : "(?:/.*)?$");
    return Pattern.compile(regex.toString());
  }

  private static void appendGlob(final StringBuilder regex, final String glob) {
    int i = 0;
    while (i < glob.length()) {
      final var c = glob.charAt(i);
      if (glob.startsWith(ANY_DIRECTORIES, i)) {
        regex.append("(?:.*/)?");
        i += ANY_DIRECTORIES.length();
      } else if (glob.startsWith("**", i)) {
        regex.append(".*");
        i += 2;
      } else if (c == '*') {
        regex.append("[^/]*");
        i++;
      } else if (c == '?') {
        regex.append("[^/]");
        i++;
      } else {
        regex.append(Pattern.quote(String.valueOf(c)));
        i++;
      }
    }
  }
}
