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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExpressionEvaluator unit tests")
class ExpressionEvaluatorTest {

  private final ExpressionEvaluator evaluator = new ExpressionEvaluator();

  private static final Map<String, String> CTX =
      Map.of(
          "github.sha", "abc123",
          "github.ref", "refs/heads/main",
          "github.actor", "octocat",
          "github.event_name", "push",
          "env.NODE_VERSION", "22",
          "secrets.API_URL", "https://api.example.com",
          "steps.build.outputs.version", "1.0.0",
          "inputs.environment", "production");

  @Test
  @DisplayName("interpolates single expression")
  void interpolatesSingle() {
    assertThat(evaluator.evaluate("commit: ${{ github.sha }}", CTX)).isEqualTo("commit: abc123");
  }

  @Test
  @DisplayName("interpolates multiple expressions in one string")
  void interpolatesMultiple() {
    final String result = evaluator.evaluate("${{ github.actor }} pushed ${{ github.sha }}", CTX);
    assertThat(result).isEqualTo("octocat pushed abc123");
  }

  @Test
  @DisplayName("interpolates secret reference")
  void interpolatesSecret() {
    assertThat(evaluator.evaluate("url=${{ secrets.API_URL }}", CTX))
        .isEqualTo("url=https://api.example.com");
  }

  @Test
  @DisplayName("interpolates step output")
  void interpolatesStepOutput() {
    assertThat(evaluator.evaluate("v${{ steps.build.outputs.version }}", CTX)).isEqualTo("v1.0.0");
  }

  @Test
  @DisplayName("keeps unknown expression as-is")
  void keepsUnknownAsIs() {
    final String expr = "${{ unknown.thing }}";
    assertThat(evaluator.evaluate(expr, CTX)).isEqualTo(expr);
  }

  @Test
  @DisplayName("evaluates always() condition")
  void conditionAlways() {
    assertThat(evaluator.evaluateCondition("always()", CTX, "success")).isTrue();
    assertThat(evaluator.evaluateCondition("always()", CTX, "failure")).isTrue();
  }

  @Test
  @DisplayName("evaluates failure() condition")
  void conditionFailure() {
    assertThat(evaluator.evaluateCondition("failure()", CTX, "failure")).isTrue();
    assertThat(evaluator.evaluateCondition("failure()", CTX, "success")).isFalse();
  }

  @Test
  @DisplayName("evaluates == equality condition")
  void conditionEquality() {
    final Map<String, String> ctx = Map.of("github.ref", "refs/heads/main");
    assertThat(
            evaluator.evaluateCondition("${{ github.ref }} == 'refs/heads/main'", ctx, "success"))
        .isTrue();
    assertThat(evaluator.evaluateCondition("${{ github.ref }} == 'refs/heads/dev'", ctx, "success"))
        .isFalse();
  }

  @Test
  @DisplayName("evaluates != inequality condition")
  void conditionInequality() {
    final Map<String, String> ctx = Map.of("github.ref", "refs/heads/main");
    assertThat(evaluator.evaluateCondition("${{ github.ref }} != 'refs/heads/dev'", ctx, "success"))
        .isTrue();
  }

  @Test
  @DisplayName("empty string is falsy")
  void emptyStringFalsy() {
    assertThat(evaluator.evaluateCondition("", CTX, "success")).isFalse();
  }

  @Test
  @DisplayName("false string is falsy")
  void falseStringFalsy() {
    assertThat(evaluator.evaluateCondition("false", CTX, "success")).isFalse();
  }
}
