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
package dev.gitnode.os.branch.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.repo.api.RepoCommandPort;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BranchTxService unit tests")
class BranchTxServiceTest {

  @Mock private RepoLookupPort repoRepository;
  @Mock private RepoCommandPort repoCommands;

  @InjectMocks private BranchTxService branchTxService;

  @Test
  @DisplayName("findRepoByOwnerAndRepoName returns repo when found")
  void findRepoByOwnerAndRepoName_returnsRepo_whenFound() {
    RepoRef repo = createRepo(UUID.randomUUID(), "owner", "my-repo", "main");
    when(repoRepository.find("owner", "my-repo")).thenReturn(Optional.of(repo));

    RepoRef result = branchTxService.findRepoByOwnerAndRepoName("owner", "my-repo");

    assertThat(result).isSameAs(repo);
    assertThat(result.name()).isEqualTo("my-repo");
    assertThat(result.defaultBranch()).isEqualTo("main");
  }

  @Test
  @DisplayName("findRepoByOwnerAndRepoName throws ItemNotFoundException when repo not found")
  void findRepoByOwnerAndRepoName_throws_whenNotFound() {
    when(repoRepository.find("owner", "missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> branchTxService.findRepoByOwnerAndRepoName("owner", "missing"))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("repoNotFound");
  }

  @Test
  @DisplayName("updateDefaultBranch calls repository with repoId and branchName")
  void updateDefaultBranch_delegatesToRepository() {
    UUID repoId = UUID.randomUUID();

    branchTxService.updateDefaultBranch(repoId, "develop");

    verify(repoCommands).updateDefaultBranch(eq(repoId), eq("develop"));
  }

  private static RepoRef createRepo(
      UUID id, String ownerUsername, String name, String defaultBranch) {
    return new RepoRef(id, UUID.randomUUID(), ownerUsername, name, false, defaultBranch);
  }
}
