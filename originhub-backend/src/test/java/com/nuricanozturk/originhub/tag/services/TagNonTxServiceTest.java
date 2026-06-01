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
package com.nuricanozturk.originhub.tag.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemAlreadyExistsException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.git.provider.GitProvider;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.tag.dtos.CreateTagForm;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TagNonTxService unit tests")
class TagNonTxServiceTest {

  @TempDir Path workDir;

  @Mock private GitProvider gitProvider;
  @Mock private ReleaseTxService releaseTxService;

  @InjectMocks private TagNonTxService tagNonTxService;

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
    repo = repo(UUID.randomUUID(), defaultBranch);
    when(releaseTxService.findRepo("owner", "demo")).thenReturn(repo);
    when(releaseTxService.findByRepoIdAndTagName(eq(repo.getId()), any()))
        .thenReturn(Optional.empty());
  }

  @AfterEach
  void tearDown() {
    git.close();
  }

  @Test
  @DisplayName("getAll returns empty list when repository has no tags")
  void getAll_returnsEmpty_whenNoTags() throws Exception {
    List<?> tags = tagNonTxService.getAll("owner", "demo");

    assertThat(tags).isEmpty();
  }

  @Test
  @DisplayName("get throws ItemNotFoundException when tag does not exist")
  void get_throws_whenTagMissing() {
    assertThatThrownBy(() -> tagNonTxService.get("owner", "demo", "v1.0.0"))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("tagNotFound");
  }

  @Test
  @DisplayName("create creates lightweight tag at HEAD when sha is omitted")
  void create_createsLightweightTag_atHead() throws Exception {
    CreateTagForm form = new CreateTagForm();
    form.setName("v1.0.0");

    var tagInfo = tagNonTxService.create("owner", "demo", form);

    assertThat(tagInfo.name()).isEqualTo("v1.0.0");
    assertThat(tagInfo.isAnnotated()).isFalse();
    assertThat(git.tagList().call().stream().map(org.eclipse.jgit.lib.Ref::getName).toList())
        .contains("refs/tags/v1.0.0");
  }

  @Test
  @DisplayName("create creates annotated tag when message is provided")
  void create_createsAnnotatedTag_whenMessageProvided() throws Exception {
    CreateTagForm form = new CreateTagForm();
    form.setName("v2.0.0");
    form.setMessage("Release 2.0");

    var tagInfo = tagNonTxService.create("owner", "demo", form);

    assertThat(tagInfo.isAnnotated()).isTrue();
    assertThat(tagInfo.tagMessage()).contains("Release 2.0");
  }

  @Test
  @DisplayName("create throws ItemAlreadyExistsException when tag name already exists")
  void create_throws_whenTagExists() throws Exception {
    git.tag().setName("dup").call();
    CreateTagForm form = new CreateTagForm();
    form.setName("dup");

    assertThatThrownBy(() -> tagNonTxService.create("owner", "demo", form))
        .isInstanceOf(ItemAlreadyExistsException.class)
        .hasMessageContaining("tagAlreadyExists");
  }

  @Test
  @DisplayName("create throws ItemNotFoundException when target sha cannot be resolved")
  void create_throws_whenShaNotFound() {
    CreateTagForm form = new CreateTagForm();
    form.setName("v-bad");
    form.setSha("deadbeef");

    assertThatThrownBy(() -> tagNonTxService.create("owner", "demo", form))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("commitNotFound");
  }

  @Test
  @DisplayName("delete removes tag from git and calls release cleanup")
  void delete_removesTag_andCleansRelease() throws Exception {
    git.tag().setName("remove-me").call();

    tagNonTxService.delete("owner", "demo", "remove-me");

    assertThat(git.tagList().call().stream().map(org.eclipse.jgit.lib.Ref::getName).toList())
        .doesNotContain("refs/tags/remove-me");
    verify(releaseTxService).deleteByTagName("owner", "demo", "remove-me");
  }

  @Test
  @DisplayName("delete throws ItemNotFoundException when tag is missing")
  void delete_throws_whenTagMissing() {
    assertThatThrownBy(() -> tagNonTxService.delete("owner", "demo", "ghost"))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("tagNotFound");
  }

  @Test
  @DisplayName("create throws ErrorOccurredException when default branch has no commits")
  void create_throws_emptyRepo_whenNoHeadCommit() throws Exception {
    Path emptyDir = Files.createTempDirectory("empty-repo");
    Git emptyGit = Git.init().setDirectory(emptyDir.toFile()).call();
    Path emptyGitDir = emptyDir.resolve(".git");
    String emptyBranch = emptyGit.getRepository().getBranch();
    Repo emptyRepo = repo(UUID.randomUUID(), emptyBranch);
    when(releaseTxService.findRepo("owner", "empty")).thenReturn(emptyRepo);
    when(gitProvider.open("owner", "empty"))
        .thenAnswer(
            inv ->
                new FileRepositoryBuilder()
                    .setGitDir(emptyGitDir.toFile())
                    .readEnvironment()
                    .build());

    CreateTagForm form = new CreateTagForm();
    form.setName("v0");

    assertThatThrownBy(() -> tagNonTxService.create("owner", "empty", form))
        .isInstanceOf(ErrorOccurredException.class)
        .hasMessageContaining("emptyRepo");

    emptyGit.close();
  }

  private static Repo repo(UUID id, String defaultBranch) {
    Repo repo = new Repo();
    repo.setId(id);
    repo.setDefaultBranch(defaultBranch);
    return repo;
  }
}
