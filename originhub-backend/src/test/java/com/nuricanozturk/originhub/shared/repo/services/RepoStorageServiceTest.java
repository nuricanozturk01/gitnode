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
package com.nuricanozturk.originhub.shared.repo.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.nuricanozturk.originhub.events.repo.RepoInitRollbackRequestedEvent;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.git.provider.GitProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("RepoStorageService unit tests")
class RepoStorageServiceTest {

  @TempDir Path repoRoot;

  @Mock private GitProvider gitProvider;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private RepoStorageService repoStorageService;

  @BeforeEach
  void setRepoRoot() {
    ReflectionTestUtils.setField(repoStorageService, "repoRoot", repoRoot.toString());
  }

  @Test
  @DisplayName("initRepo creates bare repository on disk")
  void initRepo_createsRepo_whenSuccessful() throws IOException {
    repoStorageService.initRepo("alice", "demo");

    Path repoPath = repoRoot.resolve("alice").resolve("demo.git");
    assertThat(Files.exists(repoPath)).isTrue();
    verify(gitProvider).createJGitRepo(repoPath);
  }

  @Test
  @DisplayName("initRepo publishes rollback and throws when git init fails")
  void initRepo_publishesRollback_whenGitInitFails() throws IOException {
    doThrow(new IOException("disk full")).when(gitProvider).createJGitRepo(any(Path.class));

    assertThatThrownBy(() -> repoStorageService.initRepo("alice", "fail"))
        .isInstanceOf(ErrorOccurredException.class)
        .hasMessageContaining("Failed to initialize git repository");

    verify(eventPublisher).publishEvent(any(RepoInitRollbackRequestedEvent.class));
  }

  @Test
  @DisplayName("renameRepo moves repository directory")
  void renameRepo_movesDirectory_whenSuccessful() throws IOException {
    Path oldPath = repoRoot.resolve("alice").resolve("old.git");
    Files.createDirectories(oldPath);
    Files.writeString(oldPath.resolve("marker"), "x");

    repoStorageService.renameRepo("alice", "old", "new");

    assertThat(Files.exists(repoRoot.resolve("alice").resolve("new.git"))).isTrue();
    assertThat(Files.exists(oldPath)).isFalse();
  }

  @Test
  @DisplayName("deleteRepo removes repository directory")
  void deleteRepo_removesDirectory_whenExists() throws IOException {
    Path repoPath = repoRoot.resolve("alice").resolve("demo.git");
    Files.createDirectories(repoPath);

    repoStorageService.deleteRepo("alice", "demo");

    assertThat(Files.exists(repoPath)).isFalse();
  }

  @Test
  @DisplayName("deleteTenantRepos removes owner directory")
  void deleteTenantRepos_removesOwnerDirectory() throws IOException {
    Path ownerPath = repoRoot.resolve("alice");
    Files.createDirectories(ownerPath.resolve("a.git"));

    repoStorageService.deleteTenantRepos("alice");

    assertThat(Files.exists(ownerPath)).isFalse();
  }
}
