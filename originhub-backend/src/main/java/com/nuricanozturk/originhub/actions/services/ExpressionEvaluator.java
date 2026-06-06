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

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Evaluates {@code ${{ expression }}} syntax used in workflow YAML.
 *
 * <p>Supported contexts: {@code github.*}, {@code env.*}, {@code secrets.*}, {@code
 * steps.<id>.outputs.<name>}, {@code inputs.*}, {@code matrix.*}, {@code job.status}.
 *
 * <p>Also evaluates simple boolean {@code if:} conditions ({@code ==} and {@code !=}).
 */
@Service
@NullMarked
public class ExpressionEvaluator {

  private static final Pattern EXPR_PATTERN = Pattern.compile("\\$\\{\\{\\s*(.+?)\\s*\\}\\}");

  /**
   * Interpolates all {@code ${{ }}} expressions in the template string.
   *
   * @param template raw workflow string (may contain multiple expressions)
   * @param ctx flat context map; keys use dot notation (e.g. {@code "github.sha"})
   * @return fully interpolated string; unresolved expressions kept as-is
   */
  public String evaluate(final String template, final Map<String, String> ctx) {
    final Matcher m = EXPR_PATTERN.matcher(template);
    final StringBuilder sb = new StringBuilder();
    while (m.find()) {
      final String expr = m.group(1).trim();
      final String resolved = resolveExpr(expr, ctx);
      m.appendReplacement(sb, Matcher.quoteReplacement(resolved != null ? resolved : m.group(0)));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /**
   * Evaluates an {@code if:} condition string to a boolean.
   *
   * <p>Supports: {@code a == b}, {@code a != b}, {@code always()}, {@code failure()}, {@code
   * success()}, simple non-empty truthy values.
   *
   * @param condition the condition expression (already interpolated or raw)
   * @param ctx flat context map
   * @param jobStatus current job status ("success", "failure", "cancelled")
   */
  public boolean evaluateCondition(
      final String condition, final Map<String, String> ctx, final String jobStatus) {

    final String interpolated = evaluate(condition.trim(), ctx);

    if ("always()".equalsIgnoreCase(interpolated)) {
      return true;
    }
    if ("failure()".equalsIgnoreCase(interpolated)) {
      return "failure".equalsIgnoreCase(jobStatus);
    }
    if ("success()".equalsIgnoreCase(interpolated)) {
      return "success".equalsIgnoreCase(jobStatus);
    }
    if ("cancelled()".equalsIgnoreCase(interpolated)) {
      return "cancelled".equalsIgnoreCase(jobStatus);
    }

    if (interpolated.contains("==")) {
      final String[] parts = interpolated.split("==", 2);
      return normalize(parts[0]).equals(normalize(parts[1]));
    }
    if (interpolated.contains("!=")) {
      final String[] parts = interpolated.split("!=", 2);
      return !normalize(parts[0]).equals(normalize(parts[1]));
    }

    // Treat non-empty, non-"false" string as truthy
    return !interpolated.isEmpty() && !"false".equalsIgnoreCase(interpolated);
  }

  // ── private ──────────────────────────────────────────────────────────────

  @Nullable
  private String resolveExpr(final String expr, final Map<String, String> ctx) {
    // Direct context lookup (e.g. "github.sha", "env.NODE_VERSION")
    if (ctx.containsKey(expr)) {
      return ctx.get(expr);
    }

    // steps.<id>.outputs.<name>
    if (expr.startsWith("steps.") && expr.contains(".outputs.")) {
      return ctx.get(expr);
    }

    // Fallback: unresolved → keep original placeholder
    return null;
  }

  private String normalize(final String s) {
    return s.trim().replaceAll("^['\"]|['\"]$", "");
  }
}
