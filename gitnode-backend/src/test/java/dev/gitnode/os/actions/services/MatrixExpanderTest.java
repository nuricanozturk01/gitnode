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

import dev.gitnode.os.actions.model.JobModel;
import dev.gitnode.os.actions.model.MatrixStrategy;
import dev.gitnode.os.actions.model.StrategyModel;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MatrixExpander unit tests")
class MatrixExpanderTest {

  private final MatrixExpander expander = new MatrixExpander();

  @Test
  @DisplayName("no strategy → single empty combination")
  void noStrategy_singleEmptyCombination() {
    final var result = this.expander.expand(job(null));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst()).isEmpty();
  }

  @Test
  @DisplayName("single dimension → one combination per value")
  void singleDimension_oneCombinationPerValue() {
    final var matrix = new MatrixStrategy(Map.of("os", List.of("ubuntu", "windows")), null, null);

    final var result = this.expander.expand(job(matrix));

    assertThat(result).hasSize(2);
    assertThat(result).extracting(m -> m.get("os")).containsExactlyInAnyOrder("ubuntu", "windows");
  }

  @Test
  @DisplayName("two dimensions → cartesian product")
  void twoDimensions_cartesianProduct() {
    final var matrix =
        new MatrixStrategy(
            Map.of("os", List.of("ubuntu", "windows"), "node", List.of("18", "20")), null, null);

    final var result = this.expander.expand(job(matrix));

    assertThat(result).hasSize(4);
    assertThat(result)
        .contains(
            Map.of("os", "ubuntu", "node", "18"),
            Map.of("os", "ubuntu", "node", "20"),
            Map.of("os", "windows", "node", "18"),
            Map.of("os", "windows", "node", "20"));
  }

  @Test
  @DisplayName("include adds extra combinations")
  void include_addsExtraCombination() {
    final var matrix =
        new MatrixStrategy(
            Map.of("os", List.of("ubuntu")), List.of(Map.of("os", "macos", "node", "22")), null);

    final var result = this.expander.expand(job(matrix));

    assertThat(result).hasSize(2);
    assertThat(result).contains(Map.of("os", "macos", "node", "22"));
  }

  @Test
  @DisplayName("exclude removes matching combinations")
  void exclude_removesMatchingCombination() {
    final var matrix =
        new MatrixStrategy(
            Map.of("os", List.of("ubuntu", "windows"), "node", List.of("18", "20")),
            null,
            List.of(Map.of("os", "windows", "node", "18")));

    final var result = this.expander.expand(job(matrix));

    assertThat(result).hasSize(3);
    assertThat(result).doesNotContain(Map.of("os", "windows", "node", "18"));
  }

  @Test
  @DisplayName("mixed include and exclude applied together")
  void mixedIncludeAndExclude() {
    final var matrix =
        new MatrixStrategy(
            Map.of("os", List.of("ubuntu", "windows")),
            List.of(Map.of("os", "macos")),
            List.of(Map.of("os", "windows")));

    final var result = this.expander.expand(job(matrix));

    assertThat(result).hasSize(2);
    assertThat(result).extracting(m -> m.get("os")).containsExactlyInAnyOrder("ubuntu", "macos");
  }

  private static JobModel job(final @Nullable MatrixStrategy matrix) {
    final var strategy = matrix == null ? null : new StrategyModel(matrix, null, null);
    return new JobModel(
        "build", List.of("self-hosted"), null, null, null, null, strategy, null, List.of());
  }
}
