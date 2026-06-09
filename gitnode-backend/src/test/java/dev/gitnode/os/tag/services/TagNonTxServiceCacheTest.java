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
package dev.gitnode.os.tag.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.shared.cache.CacheNames;
import dev.gitnode.os.shared.git.provider.GitProvider;
import dev.gitnode.os.shared.repo.entities.Repo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig
@DisplayName("TagNonTxService cache tests")
class TagNonTxServiceCacheTest {

  @TempDir Path workDir;

  @Configuration
  @EnableCaching(proxyTargetClass = true)
  @Import(TagNonTxService.class)
  static class Config {
    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager(CacheNames.TAGS);
    }

    @Bean
    ReleaseTxService releaseTxService() {
      return mock(ReleaseTxService.class);
    }

    @Bean
    GitProvider gitProvider() {
      return mock(GitProvider.class);
    }
  }

  @Autowired TagNonTxService service;
  @Autowired ReleaseTxService releaseTxService;
  @Autowired GitProvider gitProvider;
  @Autowired CacheManager cacheManager;

  private Git git;
  private Repo repo;

  @BeforeEach
  void setUp() throws Exception {
    reset(releaseTxService, gitProvider);
    cacheManager.getCache(CacheNames.TAGS).clear();

    git = Git.init().setDirectory(workDir.toFile()).call();
    Path gitDir = workDir.resolve(".git");
    Files.writeString(workDir.resolve("README.md"), "init");
    git.add().addFilepattern(".").call();
    git.commit().setMessage("init").call();

    repo = new Repo();
    repo.setId(UUID.randomUUID());
    repo.setDefaultBranch(git.getRepository().getBranch());

    when(releaseTxService.findRepo(any(), any())).thenReturn(repo);
    when(releaseTxService.findByRepoIdAndTagName(any(), any())).thenReturn(Optional.empty());
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
  @DisplayName("getAll returns cached result on second call — service not invoked again")
  void getAll_returnsCachedResult_onSecondCall() throws Exception {
    service.getAll("alice", "repo");

    clearInvocations(releaseTxService, gitProvider);

    service.getAll("alice", "repo");

    verify(releaseTxService, never()).findRepo(any(), any());
    verify(gitProvider, never()).open(any(), any());
  }

  @Test
  @DisplayName("getAll uses separate cache entries for different repos")
  void getAll_separateCacheEntry_perRepo() throws Exception {
    service.getAll("alice", "repo");
    service.getAll("alice", "other");

    verify(releaseTxService, times(1)).findRepo("alice", "repo");
    verify(releaseTxService, times(1)).findRepo("alice", "other");
  }

  @Test
  @DisplayName("getAll re-fetches from service after delete evicts cache")
  void getAll_refetchesFromService_afterDeleteEvictsCache() throws Exception {
    git.tag().setName("v1.0.0").call();

    service.getAll("alice", "repo");
    service.delete("alice", "repo", "v1.0.0");

    clearInvocations(releaseTxService, gitProvider);

    service.getAll("alice", "repo");

    verify(releaseTxService, times(1)).findRepo("alice", "repo");
  }
}
