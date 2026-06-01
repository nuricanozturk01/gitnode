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
package com.nuricanozturk.originhub.tree.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.git.provider.GitProvider;
import com.nuricanozturk.originhub.tree.dtos.LanguageStats;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
@DisplayName("LanguageService.detectLanguages unit tests")
class LanguageServiceDetectLanguagesTest {

  @TempDir Path workDir;

  @Mock private GitProvider gitProvider;

  @InjectMocks private LanguageService languageService;

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
  @DisplayName("detectLanguages throws ItemNotFoundException when branch is missing")
  void detectLanguages_throws_whenBranchMissing() {
    assertThatThrownBy(() -> languageService.detectLanguages("owner", "demo", "no-such-branch"))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("branchNotFound");
  }

  @Test
  @DisplayName("detectLanguages returns empty list when only plaintext files exist")
  void detectLanguages_returnsEmpty_whenOnlyPlaintext() throws Exception {
    Files.writeString(workDir.resolve("notes.txt"), "plain text only");
    git.add().addFilepattern("notes.txt").call();
    git.commit().setMessage("add notes").call();

    List<LanguageStats> stats = languageService.detectLanguages("owner", "demo", branch);

    assertThat(stats).isEmpty();
  }

  @Test
  @DisplayName("detectLanguages aggregates byte counts and percentages by language")
  void detectLanguages_returnsSortedStats_withPercentages() throws Exception {
    Files.writeString(workDir.resolve("Main.java"), "x".repeat(100));
    Files.writeString(workDir.resolve("app.ts"), "y".repeat(50));
    git.add().addFilepattern("Main.java").addFilepattern("app.ts").call();
    git.commit().setMessage("mixed languages").call();

    List<LanguageStats> stats = languageService.detectLanguages("owner", "demo", branch);

    assertThat(stats).hasSize(2);
    assertThat(stats.getFirst().language()).isEqualTo("java");
    assertThat(stats.getFirst().bytes()).isEqualTo(100L);
    assertThat(stats.getFirst().percentage())
        .isCloseTo(66.67, org.assertj.core.data.Offset.offset(0.1));
    assertThat(stats.get(1).language()).isEqualTo("typescript");
    assertThat(stats.get(1).bytes()).isEqualTo(50L);
  }
}
