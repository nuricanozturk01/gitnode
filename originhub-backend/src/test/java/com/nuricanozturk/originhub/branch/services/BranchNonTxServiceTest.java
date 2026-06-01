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
package com.nuricanozturk.originhub.branch.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.shared.branch.dtos.BranchForm;
import com.nuricanozturk.originhub.shared.branch.events.BranchCreatedEvent;
import com.nuricanozturk.originhub.shared.branch.events.BranchDeletedEvent;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemAlreadyExistsException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.git.provider.GitProvider;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BranchNonTxService unit tests")
class BranchNonTxServiceTest {

  @TempDir Path workDir;

  @Mock private BranchTxService branchTxService;
  @Mock private GitProvider gitProvider;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private BranchNonTxService branchNonTxService;

  private Git git;
  private Path gitDir;
  private String defaultBranch;
  private Repo repo;

  @BeforeEach
  void setUp() throws Exception {
    git = Git.init().setDirectory(workDir.toFile()).call();
    gitDir = workDir.resolve(".git");
    defaultBranch = git.getRepository().getBranch();
    Files.writeString(workDir.resolve("README.md"), "# hi");
    git.add().addFilepattern("README.md").call();
    git.commit().setMessage("init").call();
    when(gitProvider.open(any(), any()))
        .thenAnswer(
            inv ->
                new FileRepositoryBuilder().setGitDir(gitDir.toFile()).readEnvironment().build());
    repo = repo(UUID.randomUUID(), "owner", "demo", defaultBranch);
    when(branchTxService.findRepoByOwnerAndRepoName("owner", "demo")).thenReturn(repo);
  }

  @AfterEach
  void tearDown() {
    git.close();
  }

  @Test
  @DisplayName("create throws ItemAlreadyExistsException when branch already exists")
  void create_throws_whenBranchExists() {
    BranchForm form = new BranchForm(defaultBranch, defaultBranch);

    assertThatThrownBy(() -> branchNonTxService.create("owner", "demo", form))
        .isInstanceOf(ItemAlreadyExistsException.class)
        .hasMessageContaining("branchAlreadyExists");
  }

  @Test
  @DisplayName("create creates branch from source and publishes event")
  void create_createsBranchAndPublishesEvent_whenValid() throws Exception {
    BranchForm form = new BranchForm("feature", defaultBranch);

    var info = branchNonTxService.create("owner", "demo", form);

    assertThat(info.name()).isEqualTo("feature");
    assertThat(info.isDefault()).isFalse();
    verify(eventPublisher).publishEvent(any(BranchCreatedEvent.class));
  }

  @Test
  @DisplayName("create throws ItemNotFoundException when source branch is missing")
  void create_throws_whenSourceMissing() {
    BranchForm form = new BranchForm("feature", "ghost");

    assertThatThrownBy(() -> branchNonTxService.create("owner", "demo", form))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("sourceNotFound");
  }

  @Test
  @DisplayName("delete throws ErrorOccurredException when deleting default branch")
  void delete_throws_whenDefaultBranch() {
    assertThatThrownBy(() -> branchNonTxService.delete("owner", "demo", defaultBranch))
        .isInstanceOf(ErrorOccurredException.class)
        .hasMessageContaining("defaultBranchCannotDelete");
  }

  @Test
  @DisplayName("delete removes non-default branch and publishes event")
  void delete_removesBranch_whenNotDefault() throws Exception {
    git.branchCreate().setName("feature").call();
    repo.setDefaultBranch(defaultBranch);

    branchNonTxService.delete("owner", "demo", "feature");

    verify(eventPublisher).publishEvent(any(BranchDeletedEvent.class));
    assertThat(git.getRepository().findRef("refs/heads/feature")).isNull();
  }

  @Test
  @DisplayName("get throws ItemNotFoundException when branch does not exist")
  void get_throws_whenBranchMissing() {
    assertThatThrownBy(() -> branchNonTxService.get("owner", "demo", "missing"))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("branchNotFound");
  }

  @Test
  @DisplayName("setDefaultBranch updates repo metadata via tx service")
  void setDefaultBranch_updatesDefault_whenBranchExists() throws Exception {
    git.branchCreate().setName("develop").call();

    branchNonTxService.setDefaultBranch("owner", "demo", "develop");

    verify(branchTxService).updateDefaultBranch(eq(repo.getId()), eq("develop"));
  }

  @Test
  @DisplayName("getAll lists branches with default first")
  void getAll_listsBranches_sortedWithDefaultFirst() throws Exception {
    git.branchCreate().setName("feature").call();

    var branches = branchNonTxService.getAll("owner", "demo");

    assertThat(branches).hasSizeGreaterThanOrEqualTo(2);
    assertThat(branches.getFirst().isDefault()).isTrue();
  }

  private static Repo repo(UUID id, String owner, String name, String defaultBranch) {
    Tenant ownerTenant = new Tenant();
    ownerTenant.setUsername(owner);
    Repo r = new Repo();
    r.setId(id);
    r.setName(name);
    r.setOwner(ownerTenant);
    r.setDefaultBranch(defaultBranch);
    return r;
  }
}
