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

import dev.gitnode.os.shared.git.provider.GitProvider;
import dev.gitnode.os.shared.lock.SchedulerLock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Constants;
import org.jspecify.annotations.NullMarked;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Indexes repositories that existed before commit contributions were tracked. A few repositories
 * are processed per tick so a large installation catches up without a startup stall.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class CommitContributionBackfill {

  private static final int REPOS_PER_TICK = 10;

  private final NamedParameterJdbcTemplate jdbc;
  private final GitProvider gitProvider;
  private final CommitContributionIndexer indexer;

  private record Pending(UUID id, String owner, String name, String branch) {}

  @SchedulerLock(name = "commit-backfill", ttlSeconds = 900)
  @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
  public void backfill() {
    final var pending =
        this.jdbc.query(
            "SELECT r.id, o.username AS owner, r.name, COALESCE(r.default_branch, 'main') AS branch"
                + " FROM repositories r JOIN tenant o ON o.id = r.owner_id"
                + " WHERE NOT EXISTS (SELECT 1 FROM commit_index_state s WHERE s.repo_id = r.id)"
                + " ORDER BY r.id LIMIT :limit",
            new MapSqlParameterSource("limit", REPOS_PER_TICK),
            (rs, row) ->
                new Pending(
                    rs.getObject("id", UUID.class),
                    rs.getString("owner"),
                    rs.getString("name"),
                    rs.getString("branch")));
    for (final var repo : pending) {
      this.backfill(repo);
    }
  }

  @SuppressWarnings("checkstyle:IllegalCatch") // backfill loop: failure marks the repo unindexable
  private void backfill(final Pending repo) {
    try (final var git = this.gitProvider.open(repo.owner(), repo.name())) {
      final var head = git.resolve(Constants.R_HEADS + repo.branch() + "^{commit}");
      if (head == null) {
        this.indexer.markUnindexable(repo.id());
        return;
      }
      this.indexer.index(git, repo.id(), head);
    } catch (final Exception e) {
      log.warn("Commit backfill failed for {}/{}: {}", repo.owner(), repo.name(), e.getMessage());
      this.indexer.markUnindexable(repo.id());
    }
  }
}
