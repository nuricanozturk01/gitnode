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

import dev.gitnode.os.profile.dtos.ContributionBreakdown;
import dev.gitnode.os.profile.dtos.ContributionDay;
import dev.gitnode.os.profile.dtos.UserContributionsResponse;
import dev.gitnode.os.profile.repositories.UserContributionRepository;
import dev.gitnode.os.profile.repositories.UserContributionRepository.ContributionAggregateRow;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NullMarked
@Transactional(readOnly = true)
public class UserContributionService {

  private static final int ROLLING_DAYS = 371;
  private static final String USER_NOT_FOUND = "userNotFound";
  private static final double LEVEL_ONE_RATIO = 0.25;
  private static final double LEVEL_TWO_RATIO = 0.5;
  private static final double LEVEL_THREE_RATIO = 0.75;
  private static final int CONTRIBUTION_LEVEL_THREE = 3;
  private static final int CONTRIBUTION_LEVEL_FOUR = 4;

  private final TenantRepository tenantRepository;
  private final UserContributionRepository userContributionRepository;

  public UserContributionsResponse getContributions(
      final String username, final @Nullable UUID viewerTenantId) {

    final var tenant =
        this.tenantRepository
            .findByUsername(username)
            .orElseThrow(() -> new ItemNotFoundException(USER_NOT_FOUND));

    final boolean includePrivate = viewerTenantId != null && viewerTenantId.equals(tenant.getId());

    final LocalDate rangeEnd = LocalDate.now(ZoneOffset.UTC);
    final LocalDate rangeStart = rangeEnd.minusDays(ROLLING_DAYS - 1L);
    final Instant fromInstant = rangeStart.atStartOfDay(ZoneOffset.UTC).toInstant();
    final Instant toExclusive = rangeEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

    final List<ContributionAggregateRow> aggregates =
        this.userContributionRepository.findDailyAggregates(
            tenant.getId(), fromInstant, toExclusive, includePrivate);

    final Map<LocalDate, MutableBreakdown> breakdownByDay = new HashMap<>();

    for (final ContributionAggregateRow row : aggregates) {
      final MutableBreakdown breakdown =
          breakdownByDay.computeIfAbsent(row.day(), ignored -> new MutableBreakdown());
      breakdown.add(row.type(), (int) row.count());
    }

    final List<ContributionDay> days = new ArrayList<>(ROLLING_DAYS);
    var totalContributions = 0;
    var maxDailyCount = 0;

    for (var day = rangeStart; !day.isAfter(rangeEnd); day = day.plusDays(1)) {
      final MutableBreakdown breakdown = breakdownByDay.getOrDefault(day, MutableBreakdown.EMPTY);
      final int count = breakdown.total();
      totalContributions += count;
      maxDailyCount = Math.max(maxDailyCount, count);
      days.add(new ContributionDay(day, count, 0, breakdown.toDto()));
    }

    applyLevels(days, maxDailyCount);

    final int longestStreak = computeLongestStreak(days);
    final int currentStreak = computeCurrentStreak(days);

    return new UserContributionsResponse(
        tenant.getUsername(),
        rangeStart,
        rangeEnd,
        totalContributions,
        longestStreak,
        currentStreak,
        includePrivate,
        List.copyOf(days));
  }

  static void applyLevels(final List<ContributionDay> days, final int maxDailyCount) {

    for (int index = 0; index < days.size(); index++) {
      final ContributionDay day = days.get(index);
      final int level = computeLevel(day.count(), maxDailyCount);
      days.set(index, new ContributionDay(day.date(), day.count(), level, day.breakdown()));
    }
  }

  static int computeLevel(final int count, final int maxDailyCount) {

    if (count <= 0) {
      return 0;
    }

    if (maxDailyCount <= 1) {
      return 1;
    }

    final double ratio = (double) count / maxDailyCount;
    if (ratio <= LEVEL_ONE_RATIO) {
      return 1;
    }
    if (ratio <= LEVEL_TWO_RATIO) {
      return 2;
    }
    if (ratio <= LEVEL_THREE_RATIO) {
      return CONTRIBUTION_LEVEL_THREE;
    }
    return CONTRIBUTION_LEVEL_FOUR;
  }

  static int computeLongestStreak(final List<ContributionDay> days) {

    var longest = 0;
    var current = 0;

    for (final ContributionDay day : days) {
      if (day.count() > 0) {
        current++;
        longest = Math.max(longest, current);
      } else {
        current = 0;
      }
    }

    return longest;
  }

  static int computeCurrentStreak(final List<ContributionDay> days) {

    var streak = 0;

    for (int index = days.size() - 1; index >= 0; index--) {
      if (days.get(index).count() <= 0) {
        break;
      }
      streak++;
    }

    return streak;
  }

  private static final class MutableBreakdown {

    private static final MutableBreakdown EMPTY = new MutableBreakdown();

    private int issues;
    private int issueComments;
    private int pullRequests;
    private int pullRequestComments;
    private int pullRequestMerges;
    private int releases;
    private int snippets;
    private int snippetRevisions;
    private int snippetComments;

    private final Map<String, Consumer<Integer>> adders =
        Map.ofEntries(
            Map.entry("issue", c -> this.issues += c),
            Map.entry("issue_comment", c -> this.issueComments += c),
            Map.entry("pull_request", c -> this.pullRequests += c),
            Map.entry("pull_request_comment", c -> this.pullRequestComments += c),
            Map.entry("pull_request_merge", c -> this.pullRequestMerges += c),
            Map.entry("release", c -> this.releases += c),
            Map.entry("snippet", c -> this.snippets += c),
            Map.entry("snippet_revision", c -> this.snippetRevisions += c),
            Map.entry("snippet_comment", c -> this.snippetComments += c));

    void add(final String type, final int count) {

      final var adder = this.adders.get(type);
      if (adder != null) {
        adder.accept(count);
      }
    }

    int total() {

      return this.issues
          + this.issueComments
          + this.pullRequests
          + this.pullRequestComments
          + this.pullRequestMerges
          + this.releases
          + this.snippets
          + this.snippetRevisions
          + this.snippetComments;
    }

    ContributionBreakdown toDto() {

      return new ContributionBreakdown(
          this.issues,
          this.issueComments,
          this.pullRequests,
          this.pullRequestComments,
          this.pullRequestMerges,
          this.releases,
          this.snippets,
          this.snippetRevisions,
          this.snippetComments);
    }
  }
}
