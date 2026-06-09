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
package dev.gitnode.os.profile.services;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gitnode.os.profile.dtos.ContributionBreakdown;
import dev.gitnode.os.profile.dtos.ContributionDay;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserContributionService unit tests")
class UserContributionServiceTest {

  @Test
  @DisplayName("computes relative contribution levels")
  void computeLevel_usesRelativeBuckets() {
    assertThat(UserContributionService.computeLevel(0, 20)).isZero();
    assertThat(UserContributionService.computeLevel(5, 20)).isEqualTo(1);
    assertThat(UserContributionService.computeLevel(10, 20)).isEqualTo(2);
    assertThat(UserContributionService.computeLevel(15, 20)).isEqualTo(3);
    assertThat(UserContributionService.computeLevel(20, 20)).isEqualTo(4);
    assertThat(UserContributionService.computeLevel(1, 1)).isEqualTo(1);
  }

  @Test
  @DisplayName("computes longest and current streaks")
  void streaks_countContiguousActiveDays() {
    final var days =
        List.of(
            day("2026-01-01", 1),
            day("2026-01-02", 2),
            day("2026-01-03", 0),
            day("2026-01-04", 1),
            day("2026-01-05", 1),
            day("2026-01-06", 1));

    assertThat(UserContributionService.computeLongestStreak(days)).isEqualTo(3);
    assertThat(UserContributionService.computeCurrentStreak(days)).isEqualTo(3);
  }

  @Test
  @DisplayName("applies levels to each day")
  void applyLevels_updatesEachDay() {
    final var days = new ArrayList<>(List.of(day("2026-01-01", 1), day("2026-01-02", 4)));

    UserContributionService.applyLevels(days, 4);

    assertThat(days.get(0).level()).isEqualTo(1);
    assertThat(days.get(1).level()).isEqualTo(4);
  }

  private static ContributionDay day(final String isoDate, final int count) {

    return new ContributionDay(
        LocalDate.parse(isoDate), count, 0, new ContributionBreakdown(0, 0, 0, 0, 0, 0, 0, 0, 0));
  }
}
