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

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RunnerLabels")
class RunnerLabelsTest {

  @Test
  @DisplayName("a job without runs-on needs a self-hosted runner")
  void forJob_defaultsToSelfHosted() {
    assertThat(RunnerLabels.forJob(null)).containsExactly("self-hosted");
    assertThat(RunnerLabels.forJob(List.of())).containsExactly("self-hosted");
    assertThat(RunnerLabels.forJob(List.of(" "))).containsExactly("self-hosted");
  }

  @Test
  @DisplayName("job labels are lower-cased and de-duplicated, never translated")
  void forJob_lowerCasesAndKeepsNames() {
    assertThat(RunnerLabels.forJob(List.of("Self-Hosted", "Linux", "linux", "ubuntu-latest")))
        .containsExactly("self-hosted", "linux", "ubuntu-latest");
  }

  @Test
  @DisplayName("a runner always offers self-hosted, its OS and its architecture")
  void forRunner_addsDefaults() {
    assertThat(RunnerLabels.forRunner(List.of("gpu"), "linux", "amd64"))
        .containsExactly("self-hosted", "gpu", "linux", "x64");
    assertThat(RunnerLabels.forRunner(List.of("Self-Hosted"), "darwin", "arm64"))
        .containsExactly("self-hosted", "macos", "arm64");
    assertThat(RunnerLabels.forRunner(List.of("custom"), null, null))
        .containsExactly("self-hosted", "custom");
  }

  @Test
  @DisplayName("a default Linux runner satisfies runs-on: [self-hosted, linux]")
  void defaultRunnerMatchesSelfHostedLinux() {
    final var runner = RunnerLabels.forRunner(List.of("self-hosted"), "linux", "amd64");
    assertThat(runner).containsAll(RunnerLabels.forJob(List.of("self-hosted", "linux")));
  }
}
