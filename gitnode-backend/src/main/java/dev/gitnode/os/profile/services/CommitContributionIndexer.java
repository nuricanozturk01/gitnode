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

import dev.gitnode.os.events.repo.RepoPushedEvent;
import dev.gitnode.os.identity.entities.Tenant;
import dev.gitnode.os.identity.repositories.TenantRepository;
import dev.gitnode.os.repo.repositories.RepoRepository;
import dev.gitnode.os.shared.git.provider.GitProvider;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

/**
 * Records which accounts authored commits on a repository's default branch, so the contribution
 * heatmap can count code and not only issues and pull requests. Each push reads only the commits
 * added since the previously indexed head.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class CommitContributionIndexer {

  static final int MAX_COMMITS_PER_RUN = 20_000;
  private static final java.util.regex.Pattern NOREPLY =
      java.util.regex.Pattern.compile("^(?:\\d+\\+)?([a-z0-9._-]+)@users\\.noreply\\.[a-z0-9.-]+$");

  private final RepoRepository repos;
  private final TenantRepository tenants;
  private final GitProvider gitProvider;
  private final NamedParameterJdbcTemplate jdbc;

  @ApplicationModuleListener
  public void onPush(final RepoPushedEvent event) throws IOException {
    final var sha = event.commitSha();
    if (sha == null) {
      return;
    }
    final var repo = this.repos.findByIdWithOwner(event.repoId()).orElse(null);
    if (repo == null || !repo.getDefaultBranch().equals(event.branchName())) {
      return;
    }
    try (final var git = this.gitProvider.open(repo.getOwner().getUsername(), repo.getName())) {
      this.index(git, repo.getId(), ObjectId.fromString(sha));
    }
  }

  /** Indexes the commits reachable from {@code head} that were not indexed before. */
  int index(final Repository git, final UUID repoId, final ObjectId head) throws IOException {
    final var previous = this.indexedSha(repoId);
    final var commits = new java.util.ArrayList<RevCommit>();
    try (final var walk = new RevWalk(git)) {
      walk.markStart(walk.parseCommit(head));
      this.markPreviousUninteresting(git, walk, previous);
      for (final var commit : walk) {
        if (commits.size() >= MAX_COMMITS_PER_RUN) {
          log.warn("Commit index for repo {} capped at {} commits", repoId, MAX_COMMITS_PER_RUN);
          break;
        }
        if (commit.getParentCount() <= 1) {
          commits.add(commit);
        }
      }
    }
    final var saved = this.store(repoId, commits);
    this.saveState(repoId, head.name());
    return saved;
  }

  private void markPreviousUninteresting(
      final Repository git, final RevWalk walk, final @Nullable String previous) {
    if (previous == null) {
      return;
    }
    try {
      final var id = ObjectId.fromString(previous);
      if (git.getObjectDatabase().has(id)) {
        walk.markUninteresting(walk.parseCommit(id));
      }
    } catch (final IOException | IllegalArgumentException ignored) {
      // history was rewritten or pruned: index everything, duplicates are ignored
    }
  }

  /** Maps lower-cased commit e-mail addresses to the account they count for. */
  private Map<String, UUID> ownersOf(final Set<String> emails) {
    final Map<String, UUID> owners = new HashMap<>();
    for (final Tenant tenant : this.tenants.findAllByEmailIn(emails)) {
      owners.put(tenant.getEmail().toLowerCase(Locale.ROOT), tenant.getId());
    }
    this.jdbc.query(
        "SELECT lower(email) AS email, tenant_id FROM tenant_emails WHERE lower(email) IN (:emails)",
        new MapSqlParameterSource("emails", emails),
        rs -> {
          owners.putIfAbsent(rs.getString("email"), rs.getObject("tenant_id", UUID.class));
        });
    for (final var email : emails) {
      if (!owners.containsKey(email)) {
        this.byNoreplyAddress(email).ifPresent(id -> owners.put(email, id));
      }
    }
    return owners;
  }

  private int store(final UUID repoId, final List<RevCommit> commits) {
    if (commits.isEmpty()) {
      return 0;
    }
    final var emails = new HashSet<String>();
    commits.forEach(c -> emails.add(this.email(c)));
    final var owners = this.ownersOf(emails);
    final var rows = new java.util.ArrayList<MapSqlParameterSource>();
    for (final var commit : commits) {
      final var author = owners.get(this.email(commit));
      if (author == null) {
        continue;
      }
      rows.add(
          new MapSqlParameterSource()
              .addValue("repoId", repoId)
              .addValue("sha", commit.getName())
              .addValue("authorId", author)
              .addValue(
                  "committedAt",
                  Timestamp.from(
                      Instant.ofEpochSecond(
                          commit.getAuthorIdent().getWhenAsInstant().getEpochSecond()))));
    }
    if (rows.isEmpty()) {
      return 0;
    }
    this.jdbc.batchUpdate(
        "INSERT INTO commit_contributions (repo_id, sha, author_id, committed_at)"
            + " VALUES (:repoId, :sha, :authorId, :committedAt) ON CONFLICT DO NOTHING",
        rows.toArray(MapSqlParameterSource[]::new));
    return rows.size();
  }

  /**
   * Matches {@code username@users.noreply.<host>} and {@code 123+username@users.noreply.<host>}.
   */
  private java.util.Optional<UUID> byNoreplyAddress(final String email) {
    final var matcher = NOREPLY.matcher(email);
    if (!matcher.matches()) {
      return java.util.Optional.empty();
    }
    return this.tenants.findByUsername(matcher.group(1)).map(Tenant::getId);
  }

  private String email(final RevCommit commit) {
    return commit.getAuthorIdent().getEmailAddress().toLowerCase(Locale.ROOT);
  }

  private @Nullable String indexedSha(final UUID repoId) {
    final var found =
        this.jdbc.queryForList(
            "SELECT indexed_sha FROM commit_index_state WHERE repo_id = :repoId",
            new MapSqlParameterSource("repoId", repoId),
            String.class);
    return found.isEmpty() ? null : found.get(0);
  }

  /**
   * Records that nothing could be indexed, so a backfill does not retry this repository forever.
   */
  void markUnindexable(final UUID repoId) {
    this.saveState(repoId, ObjectId.zeroId().name());
  }

  private void saveState(final UUID repoId, final String sha) {
    this.jdbc.update(
        "INSERT INTO commit_index_state (repo_id, indexed_sha) VALUES (:repoId, :sha)"
            + " ON CONFLICT (repo_id) DO UPDATE SET indexed_sha = EXCLUDED.indexed_sha",
        new MapSqlParameterSource().addValue("repoId", repoId).addValue("sha", sha));
  }
}
