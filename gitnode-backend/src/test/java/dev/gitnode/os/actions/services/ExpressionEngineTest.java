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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gitnode.os.actions.services.ExpressionEngine.ExpressionException;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("ExpressionEngine — GitHub expression language")
class ExpressionEngineTest {

  private static final Map<String, String> VARS =
      Map.ofEntries(
          Map.entry("github.ref", "refs/heads/main"),
          Map.entry("github.event_name", "push"),
          Map.entry("inputs.skip", "false"),
          Map.entry("inputs.flag", "true"),
          Map.entry("inputs.target", "vps"),
          Map.entry("matrix.os", "Linux"),
          Map.entry("needs.build.result", "success"));

  private static Object eval(final String src) {
    return ExpressionEngine.evaluate(src, VARS, "success");
  }

  static Stream<Arguments> cases() {
    return Stream.of(
        Arguments.of("true", true),
        Arguments.of("false", false),
        Arguments.of("null", null),
        Arguments.of("1", 1.0),
        Arguments.of("-2.5", -2.5),
        Arguments.of("0x10", 16.0),
        Arguments.of("1e2", 100.0),
        Arguments.of("'it''s'", "it's"),
        Arguments.of("github.ref == 'refs/heads/main'", true),
        Arguments.of("github.ref == 'REFS/HEADS/MAIN'", true),
        Arguments.of("github.ref != 'refs/heads/dev'", true),
        Arguments.of("github.event_name == 'push' && inputs.target == 'vps'", true),
        Arguments.of("github.event_name == 'pull_request' || inputs.target == 'vps'", true),
        Arguments.of("github.event_name == 'pull_request' || inputs.target == 'default'", false),
        Arguments.of("!(inputs.target == 'vps')", false),
        Arguments.of("!false", true),
        Arguments.of("inputs.skip != 'true'", true),
        Arguments.of("inputs.skip == false", true),
        Arguments.of("inputs.skip == 'false'", true),
        Arguments.of("inputs.skip != true", true),
        Arguments.of("!inputs.skip", true),
        Arguments.of("inputs.skip || 'x'", "x"),
        Arguments.of("inputs.flag == true", true),
        Arguments.of("inputs.flag != 'true'", false),
        Arguments.of("inputs.flag && 'on'", "on"),
        Arguments.of("inputs.target == true", false),
        Arguments.of("inputs.missing == ''", true),
        Arguments.of("inputs.missing", null),
        Arguments.of("inputs.missing || 'fallback'", "fallback"),
        Arguments.of("matrix.os == 'linux'", true),
        Arguments.of("needs.build.result == 'success'", true),
        Arguments.of("1 < 2", true),
        Arguments.of("2 <= 2", true),
        Arguments.of("3 > 2", true),
        Arguments.of("3 >= 4", false),
        Arguments.of("'a' < 'b'", true),
        Arguments.of("contains('Hello World', 'world')", true),
        Arguments.of("contains(github.ref, 'feature')", false),
        Arguments.of("startsWith(github.ref, 'refs/heads/')", true),
        Arguments.of("endsWith(github.ref, '/main')", true),
        Arguments.of("format('{0}-{1}-{0}', 'a', 'b')", "a-b-a"),
        Arguments.of("format('{{literal}} {0}', 'x')", "{literal} x"),
        Arguments.of("join('a, b ,c', '/')", "a/b/c"),
        Arguments.of("toJSON('x')", "\"x\""),
        Arguments.of("fromJSON('true')", true),
        Arguments.of("fromJSON('42')", 42.0),
        Arguments.of("success()", true),
        Arguments.of("failure()", false),
        Arguments.of("always()", true),
        Arguments.of("cancelled()", false),
        Arguments.of("success() && github.ref == 'refs/heads/main'", true),
        Arguments.of("(1 == 1) && (2 == 2)", true),
        Arguments.of("true || false && false", true));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  @DisplayName("evaluates")
  void evaluates(final String source, final Object expected) {
    assertThat(eval(source)).isEqualTo(expected);
  }

  @Test
  @DisplayName("status functions follow the job status")
  void statusFunctions() {
    assertThat(ExpressionEngine.evaluate("success()", Map.of(), "failure")).isEqualTo(false);
    assertThat(ExpressionEngine.evaluate("failure()", Map.of(), "failure")).isEqualTo(true);
    assertThat(ExpressionEngine.evaluate("cancelled()", Map.of(), "cancelled")).isEqualTo(true);
    assertThat(ExpressionEngine.evaluate("always()", Map.of(), "failure")).isEqualTo(true);
    assertThat(ExpressionEngine.evaluate("success()", Map.of(), "skipped")).isEqualTo(false);
    assertThat(ExpressionEngine.evaluate("failure()", Map.of(), "skipped")).isEqualTo(false);
    assertThat(ExpressionEngine.evaluate("success()", Map.of(), "")).isEqualTo(true);
  }

  @ParameterizedTest
  @MethodSource("badExpressions")
  @DisplayName("rejects malformed expressions")
  void rejectsMalformed(final String source) {
    assertThatThrownBy(() -> eval(source)).isInstanceOf(ExpressionException.class);
  }

  static Stream<String> badExpressions() {
    return Stream.of(
        "'unterminated",
        "(1 == 1",
        "1 ==",
        "unknownFn(1)",
        "contains('a')",
        "github..ref",
        "1 2",
        "@",
        "inputs.x[");
  }

  @Test
  @DisplayName("truthiness matches GitHub")
  void truthiness() {
    assertThat(ExpressionEngine.truthy(null)).isFalse();
    assertThat(ExpressionEngine.truthy(false)).isFalse();
    assertThat(ExpressionEngine.truthy(0.0)).isFalse();
    assertThat(ExpressionEngine.truthy(Double.NaN)).isFalse();
    assertThat(ExpressionEngine.truthy("")).isFalse();
    assertThat(ExpressionEngine.truthy("0")).isTrue();
    assertThat(ExpressionEngine.truthy("x")).isTrue();
    assertThat(ExpressionEngine.truthy(-1.0)).isTrue();
    assertThat(ExpressionEngine.truthy(true)).isTrue();
  }

  @Test
  @DisplayName("renders values the way GitHub interpolates them")
  void asString() {
    assertThat(ExpressionEngine.asString(null)).isEmpty();
    assertThat(ExpressionEngine.asString(true)).isEqualTo("true");
    assertThat(ExpressionEngine.asString(3.0)).isEqualTo("3");
    assertThat(ExpressionEngine.asString(3.5)).isEqualTo("3.5");
    assertThat(ExpressionEngine.asString("x")).isEqualTo("x");
  }
}
