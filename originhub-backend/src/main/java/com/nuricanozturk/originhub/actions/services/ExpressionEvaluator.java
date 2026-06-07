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
      final String resolved = this.resolveExpr(expr, ctx);
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
   * <p>Handles both raw expressions and {@code ${{ expr }}} wrapped forms. Operands on either side
   * of a comparison are resolved individually so that {@code ${{ inputs.X != 'true' }}} correctly
   * resolves {@code inputs.X} from context rather than treating the whole expression as a key.
   *
   * @param condition the condition expression (already interpolated or raw)
   * @param ctx flat context map
   * @param jobStatus current job status ("success", "failure", "cancelled")
   */
  public boolean evaluateCondition(
      final String condition, final Map<String, String> ctx, final String jobStatus) {

    final String inner = this.unwrapExpression(condition.trim(), ctx);

    if ("always()".equalsIgnoreCase(inner)) {
      return true;
    }
    if ("failure()".equalsIgnoreCase(inner)) {
      return "failure".equalsIgnoreCase(jobStatus);
    }
    if ("success()".equalsIgnoreCase(inner)) {
      return "success".equalsIgnoreCase(jobStatus);
    }
    if ("cancelled()".equalsIgnoreCase(inner)) {
      return "cancelled".equalsIgnoreCase(jobStatus);
    }

    return this.evaluateComparison(inner, ctx);
  }

  /**
   * If the condition is a single {@code ${{ expr }}} block, extracts the inner expression so that
   * comparison operands can be resolved individually. Otherwise returns the fully interpolated
   * string.
   */
  private String unwrapExpression(final String condition, final Map<String, String> ctx) {
    final Matcher m = EXPR_PATTERN.matcher(condition);
    if (m.matches()) {
      return m.group(1).trim();
    }
    return this.evaluate(condition, ctx);
  }

  private boolean evaluateComparison(final String expr, final Map<String, String> ctx) {
    if (expr.contains("!=")) {
      final String[] parts = expr.split("!=", 2);
      final String left = this.resolveOperand(parts[0].trim(), ctx);
      final String right = this.normalize(parts[1].trim());
      return !left.equals(right);
    }
    if (expr.contains("==")) {
      final String[] parts = expr.split("==", 2);
      final String left = this.resolveOperand(parts[0].trim(), ctx);
      final String right = this.normalize(parts[1].trim());
      return left.equals(right);
    }

    // Simple value — resolve and treat non-empty/non-false as truthy
    final String resolved = this.resolveOperand(expr, ctx);
    return !resolved.isEmpty() && !"false".equalsIgnoreCase(resolved);
  }

  /**
   * Resolves a single operand: tries context lookup first, falls back to treating it as a string
   * literal (stripping surrounding quotes).
   */
  private String resolveOperand(final String operand, final Map<String, String> ctx) {
    final String resolved = this.resolveExpr(operand, ctx);
    return resolved != null ? resolved : this.normalize(operand);
  }

  // ── private ──────────────────────────────────────────────────────────────

  @Nullable
  private String resolveExpr(final String expr, final Map<String, String> ctx) {
    if (ctx.containsKey(expr)) {
      return ctx.get(expr);
    }
    if (isSimpleContextRef(expr)) {
      return ctx.getOrDefault(expr, "");
    }
    if (expr.startsWith("steps.") && expr.contains(".outputs.")) {
      return ctx.getOrDefault(expr, "");
    }
    return null;
  }

  /**
   * Returns true for inputs.* and matrix.* refs that are plain lookups (no comparison operators).
   */
  private static boolean isSimpleContextRef(final String expr) {
    return (expr.startsWith("inputs.") || expr.startsWith("matrix."))
        && !expr.contains("==")
        && !expr.contains("!=");
  }

  private String normalize(final String s) {
    return s.trim().replaceAll("^['\"]|['\"]$", "");
  }
}
