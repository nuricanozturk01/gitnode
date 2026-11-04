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

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@NullMarked
public class ExpressionEvaluator {

  private static final Pattern EXPR_PATTERN = Pattern.compile("\\$\\{\\{\\s*(.+?)\\s*\\}\\}");

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
   * Evaluates an {@code if:} condition with the full GitHub expression language. A blank condition
   * is false here; callers that treat "no condition" as success() must check for it themselves.
   * Conditions that cannot be parsed evaluate to false (and are logged) so a typo skips the job
   * instead of running it unintentionally.
   */
  public boolean evaluateCondition(
      final String condition, final Map<String, String> ctx, final String jobStatus) {

    final String trimmed = condition.trim();
    if (trimmed.isEmpty()) {
      return false;
    }

    try {
      final Matcher whole = EXPR_PATTERN.matcher(trimmed);
      if (whole.matches()) {
        return ExpressionEngine.truthy(
            ExpressionEngine.evaluate(whole.group(1).trim(), ctx, jobStatus));
      }
      if (trimmed.contains("${{")) {
        return this.evaluateMixed(trimmed, ctx, jobStatus);
      }
      return ExpressionEngine.truthy(ExpressionEngine.evaluate(trimmed, ctx, jobStatus));
    } catch (final ExpressionEngine.ExpressionException ex) {
      log.warn("Invalid condition '{}': {}", condition, ex.getMessage());
      return false;
    }
  }

  /**
   * Text mixed with {@code ${{ }}}, e.g. {@code ${{ github.ref }} == 'refs/heads/main'}. GitHub
   * would make that a plain (truthy) string; GitNode substitutes each block with a quoted literal
   * and evaluates the result, which is what the author meant. Text that is not an expression after
   * substitution is truthy when non-empty, like on GitHub.
   */
  private boolean evaluateMixed(
      final String condition, final Map<String, String> ctx, final String jobStatus) {

    final Matcher m = EXPR_PATTERN.matcher(condition);
    final var sb = new StringBuilder();
    while (m.find()) {
      final var value = ExpressionEngine.evaluate(m.group(1).trim(), ctx, jobStatus);
      final String quoted = "'" + ExpressionEngine.asString(value).replace("'", "''") + "'";
      m.appendReplacement(sb, Matcher.quoteReplacement(quoted));
    }
    m.appendTail(sb);
    final String substituted = sb.toString();
    if (substituted.isBlank()) {
      return false;
    }
    try {
      return ExpressionEngine.truthy(ExpressionEngine.evaluate(substituted, ctx, jobStatus));
    } catch (final ExpressionEngine.ExpressionException ex) {
      return true;
    }
  }

  @Nullable
  private String resolveExpr(final String expr, final Map<String, String> ctx) {
    if (ctx.containsKey(expr)) {
      return ctx.get(expr);
    }
    if (isSimpleContextRef(expr)) {
      return ctx.getOrDefault(expr, "");
    }
    if (expr.startsWith("steps.")) {
      // Step outputs only exist while the job runs; the runner resolves them. Leave the
      // expression untouched unless the caller already knows the value.
      return ctx.get(expr);
    }
    return null;
  }

  private static boolean isSimpleContextRef(final String expr) {
    return (expr.startsWith("inputs.") || expr.startsWith("matrix.") || expr.startsWith("secrets."))
        && !expr.contains("==")
        && !expr.contains("!=");
  }
}
