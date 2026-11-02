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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.gitnode.os.shared.git.provider.GitProvider;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@DisplayName("CommitContributionIndexer with a real Git repository")
class CommitContributionIndexerTest {

  @TempDir Path dir;

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final TenantRepository tenants = mock(TenantRepository.class);
  private CommitContributionIndexer indexer;
  private Git git;
  private final UUID repoId = UUID.randomUUID();
  private final Tenant alice = new Tenant();
  private String indexedSha;

  @BeforeEach
  void setUp() throws Exception {
    git = Git.init().setInitialBranch("main").setDirectory(dir.toFile()).call();
    alice.setId(UUID.randomUUID());
    alice.setEmail("Alice@Example.org");
    when(tenants.findAllByEmailIn(any())).thenReturn(List.of(alice));
    when(jdbc.queryForList(
            anyString(),
            any(SqlParameterSource.class),
            org.mockito.ArgumentMatchers.eq(String.class)))
        .thenAnswer(i -> indexedSha == null ? List.of() : List.of(indexedSha));
    when(jdbc.update(anyString(), any(SqlParameterSource.class)))
        .thenAnswer(
            i -> {
              indexedSha = (String) ((MapSqlParameterSource) i.getArgument(1)).getValue("sha");
              return 1;
            });
    indexer =
        new CommitContributionIndexer(
            mock(RepoRepository.class), tenants, mock(GitProvider.class), jdbc);
  }

  private String commit(final String email, final String message) throws Exception {
    return this.commit(email, message, "f.txt");
  }

  private String commit(final String email, final String message, final String file)
      throws Exception {
    Files.writeString(dir.resolve(file), message);
    git.add().addFilepattern(".").call();
    return git.commit()
        .setMessage(message)
        .setAuthor(new PersonIdent("n", email))
        .setCommitter(new PersonIdent("n", email))
        .call()
        .getName();
  }

  @SuppressWarnings("unchecked")
  private List<String> storedShas() {
    final var captor = ArgumentCaptor.forClass(MapSqlParameterSource[].class);
    org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.atLeastOnce())
        .batchUpdate(anyString(), captor.capture());
    final var all = new ArrayList<String>();
    captor
        .getAllValues()
        .forEach(
            batch -> {
              for (final var row : batch) {
                all.add((String) row.getValue("sha"));
              }
            });
    return all;
  }

  @Test
  @DisplayName("only commits by known accounts are stored, matched on lower-cased e-mail")
  void storesKnownAuthorsOnly() throws Exception {
    final var mine = commit("alice@example.org", "one");
    commit("stranger@elsewhere.org", "two");
    final var head = git.getRepository().resolve("HEAD");

    final var saved = indexer.index(git.getRepository(), repoId, head);

    assertThat(saved).isEqualTo(1);
    assertThat(storedShas()).containsExactly(mine);
    assertThat(indexedSha).isEqualTo(head.name());
  }

  @Test
  @DisplayName("a later push indexes only the commits added since the previous head")
  void incrementalPush() throws Exception {
    commit("alice@example.org", "one");
    indexer.index(git.getRepository(), repoId, git.getRepository().resolve("HEAD"));
    final var second = commit("alice@example.org", "two");
    final var third = commit("alice@example.org", "three");

    final var saved =
        indexer.index(git.getRepository(), repoId, git.getRepository().resolve("HEAD"));

    assertThat(saved).isEqualTo(2);
    assertThat(storedShas()).contains(second, third);
  }

  @Test
  @DisplayName(
      "a merge commit is not counted and an unreachable previous head falls back to a full walk")
  void mergesAndRewrittenHistory() throws Exception {
    commit("alice@example.org", "one");
    git.checkout().setCreateBranch(true).setName("side").call();
    commit("alice@example.org", "side change", "side.txt");
    git.checkout().setName("main").call();
    commit("alice@example.org", "main change");
    git.merge().include(git.getRepository().resolve("side")).setMessage("merge").call();
    indexedSha = "0".repeat(40);

    final var saved =
        indexer.index(git.getRepository(), repoId, git.getRepository().resolve("HEAD"));

    assertThat(saved).isEqualTo(3);
  }

  @Test
  @DisplayName("users.noreply addresses resolve to the account with that username")
  void noreplyAddresses() throws Exception {
    alice.setUsername("alice");
    when(tenants.findByUsername("alice")).thenReturn(java.util.Optional.of(alice));
    when(tenants.findByUsername("nobody")).thenReturn(java.util.Optional.empty());
    final var plain = commit("alice@users.noreply.gitnode", "one");
    final var numbered = commit("12345+Alice@users.noreply.example.com", "two");
    commit("nobody@users.noreply.gitnode", "three");
    commit("alice@elsewhere.org", "four");

    final var saved =
        indexer.index(git.getRepository(), repoId, git.getRepository().resolve("HEAD"));

    assertThat(saved).isEqualTo(2);
    assertThat(storedShas()).containsExactlyInAnyOrder(plain, numbered);
  }
}
