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
package com.nuricanozturk.originhub.profile.repositories;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@NullMarked
public class UserContributionRepository {

  private static final String AGGREGATE_SQL =
      """
      SELECT activity_day, activity_type, COUNT(*) AS activity_count
      FROM (
        SELECT (i.created_at AT TIME ZONE 'UTC')::date AS activity_day, 'issue' AS activity_type
        FROM issues i
        JOIN repositories r ON r.id = i.repo_id
        WHERE i.author_id = :tenantId
          AND i.created_at >= :fromInstant
          AND i.created_at < :toExclusive
          AND (:includePrivate OR r.is_private = FALSE)

        UNION ALL

        SELECT (ic.created_at AT TIME ZONE 'UTC')::date, 'issue_comment'
        FROM issue_comments ic
        JOIN issues i ON i.id = ic.issue_id
        JOIN repositories r ON r.id = i.repo_id
        WHERE ic.author_id = :tenantId
          AND ic.created_at >= :fromInstant
          AND ic.created_at < :toExclusive
          AND (:includePrivate OR r.is_private = FALSE)

        UNION ALL

        SELECT (pr.created_at AT TIME ZONE 'UTC')::date, 'pull_request'
        FROM pull_requests pr
        JOIN repositories r ON r.id = pr.repo_id
        WHERE pr.author_id = :tenantId
          AND pr.created_at >= :fromInstant
          AND pr.created_at < :toExclusive
          AND (:includePrivate OR r.is_private = FALSE)

        UNION ALL

        SELECT (prc.created_at AT TIME ZONE 'UTC')::date, 'pull_request_comment'
        FROM pull_request_comments prc
        JOIN pull_requests pr ON pr.id = prc.pr_id
        JOIN repositories r ON r.id = pr.repo_id
        WHERE prc.author_id = :tenantId
          AND prc.created_at >= :fromInstant
          AND prc.created_at < :toExclusive
          AND (:includePrivate OR r.is_private = FALSE)

        UNION ALL

        SELECT (pr.merged_at AT TIME ZONE 'UTC')::date, 'pull_request_merge'
        FROM pull_requests pr
        JOIN repositories r ON r.id = pr.repo_id
        WHERE pr.merged_by_id = :tenantId
          AND pr.merged_at IS NOT NULL
          AND pr.merged_at >= :fromInstant
          AND pr.merged_at < :toExclusive
          AND (:includePrivate OR r.is_private = FALSE)

        UNION ALL

        SELECT (COALESCE(rls.published_at, rls.created_at) AT TIME ZONE 'UTC')::date, 'release'
        FROM releases rls
        JOIN repositories r ON r.id = rls.repo_id
        WHERE rls.author_id = :tenantId
          AND rls.is_draft = FALSE
          AND COALESCE(rls.published_at, rls.created_at) >= :fromInstant
          AND COALESCE(rls.published_at, rls.created_at) < :toExclusive
          AND (:includePrivate OR r.is_private = FALSE)

        UNION ALL

        SELECT (s.created_at AT TIME ZONE 'UTC')::date, 'snippet'
        FROM snippets s
        WHERE s.owner_id = :tenantId
          AND s.created_at >= :fromInstant
          AND s.created_at < :toExclusive
          AND (:includePrivate OR s.visibility = 'PUBLIC')

        UNION ALL

        SELECT (sr.created_at AT TIME ZONE 'UTC')::date, 'snippet_revision'
        FROM snippet_revisions sr
        JOIN snippets s ON s.id = sr.snippet_id
        WHERE sr.author_id = :tenantId
          AND sr.created_at >= :fromInstant
          AND sr.created_at < :toExclusive
          AND (:includePrivate OR s.visibility = 'PUBLIC')

        UNION ALL

        SELECT (sc.created_at AT TIME ZONE 'UTC')::date, 'snippet_comment'
        FROM snippet_comments sc
        JOIN snippets s ON s.id = sc.snippet_id
        WHERE sc.author_id = :tenantId
          AND sc.created_at >= :fromInstant
          AND sc.created_at < :toExclusive
          AND (:includePrivate OR s.visibility = 'PUBLIC')
      ) contributions
      GROUP BY activity_day, activity_type
      ORDER BY activity_day
      """;

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public List<ContributionAggregateRow> findDailyAggregates(
      final UUID tenantId,
      final Instant fromInstant,
      final Instant toExclusive,
      final boolean includePrivate) {

    final var params =
        new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("fromInstant", Timestamp.from(fromInstant))
            .addValue("toExclusive", Timestamp.from(toExclusive))
            .addValue("includePrivate", includePrivate);

    return this.jdbcTemplate.query(AGGREGATE_SQL, params, UserContributionRepository::mapRow);
  }

  private static ContributionAggregateRow mapRow(final ResultSet rs, final int rowNum)
      throws SQLException {

    final java.sql.Date activityDay = rs.getDate("activity_day");
    return new ContributionAggregateRow(
        activityDay != null ? activityDay.toLocalDate() : null,
        rs.getString("activity_type"),
        rs.getLong("activity_count"));
  }

  public record ContributionAggregateRow(LocalDate day, String type, long count) {}
}
