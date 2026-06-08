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
package dev.gitnode.os.commit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.git.provider.GitProvider;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommitNonTxService unit tests")
class CommitNonTxServiceTest {

  @TempDir Path workDir;

  @Mock private GitProvider gitProvider;
  @Mock private TenantRepository tenantRepository;

  @InjectMocks private CommitNonTxService commitNonTxService;

  private Git git;
  private Path gitDir;
  private String branch;

  @BeforeEach
  void setUp() throws Exception {
    git = Git.init().setDirectory(workDir.toFile()).call();
    gitDir = workDir.resolve(".git");
    branch = git.getRepository().getBranch();
    when(gitProvider.open(any(), any()))
        .thenAnswer(
            inv ->
                new FileRepositoryBuilder().setGitDir(gitDir.toFile()).readEnvironment().build());
  }

  @AfterEach
  void tearDown() {
    git.close();
  }

  @Test
  @DisplayName("getCommits returns empty page when branch does not exist")
  void getCommits_returnsEmptyPage_whenBranchMissing() throws Exception {
    var result = commitNonTxService.getCommits("owner", "repo", "no-such-branch", 0, 10);

    assertThat(result.items()).isEmpty();
    assertThat(result.totalItems()).isZero();
    assertThat(result.hasNext()).isFalse();
  }

  @Test
  @DisplayName("getCommits returns paginated commits for existing branch")
  void getCommits_returnsCommits_whenBranchExists() throws Exception {
    Files.writeString(workDir.resolve("README.md"), "# hello");
    git.add().addFilepattern("README.md").call();
    git.commit()
        .setMessage("Initial commit")
        .setAuthor("Author", "author@test.com")
        .setCommitter("Author", "author@test.com")
        .call();
    when(tenantRepository.findAllByEmailIn(any())).thenReturn(List.of());

    var result = commitNonTxService.getCommits("owner", "repo", branch, 0, 10);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().message()).isEqualTo("Initial commit");
  }

  @Test
  @DisplayName("getCommit throws ItemNotFoundException for unknown SHA")
  void getCommit_throws_whenShaUnknown() {
    assertThatThrownBy(() -> commitNonTxService.getCommit("owner", "repo", "deadbeef"))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("commitNotFound");
  }

  @Test
  @DisplayName("getCommit returns detail for valid commit")
  void getCommit_returnsDetail_whenShaValid() throws Exception {
    Files.writeString(workDir.resolve("file.txt"), "content");
    git.add().addFilepattern("file.txt").call();
    var commit = git.commit().setMessage("Add file").call();
    when(tenantRepository.findByUsernameOrEmail(any())).thenReturn(Optional.empty());

    var detail = commitNonTxService.getCommit("owner", "repo", commit.getName());

    assertThat(detail.sha()).isEqualTo(commit.getName());
    assertThat(detail.message()).isEqualTo("Add file");
  }

  @Test
  @DisplayName("getCommitDiff throws ItemNotFoundException for unknown SHA")
  void getCommitDiff_throws_whenShaUnknown() {
    assertThatThrownBy(() -> commitNonTxService.getCommitDiff("owner", "repo", "badsha1"))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("commitNotFound");
  }

  @Test
  @DisplayName("getCommits resolves author from tenant email when known")
  void getCommits_resolvesAuthorFromTenant_whenEmailMatches() throws Exception {
    Files.writeString(workDir.resolve("a.txt"), "a");
    git.add().addFilepattern("a.txt").call();
    git.commit()
        .setMessage("Commit")
        .setAuthor("Author", "alice@example.com")
        .setCommitter("Author", "alice@example.com")
        .call();
    Tenant tenant = new Tenant();
    tenant.setUsername("alice");
    tenant.setEmail("alice@example.com");
    tenant.setAvatarUrl("https://avatar");
    when(tenantRepository.findAllByEmailIn(any())).thenReturn(List.of(tenant));

    var result = commitNonTxService.getCommits("owner", "repo", branch, 0, 10);

    assertThat(result.items().getFirst().author().username()).isEqualTo("alice");
    assertThat(result.items().getFirst().author().avatarUrl()).isEqualTo("https://avatar");
  }
}
