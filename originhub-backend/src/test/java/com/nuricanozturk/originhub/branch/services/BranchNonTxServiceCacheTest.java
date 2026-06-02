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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.shared.branch.dtos.BranchForm;
import com.nuricanozturk.originhub.shared.cache.CacheNames;
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
@DisplayName("BranchNonTxService cache tests")
class BranchNonTxServiceCacheTest {

  @TempDir Path workDir;

  @Configuration
  @EnableCaching(proxyTargetClass = true)
  @Import(BranchNonTxService.class)
  static class Config {
    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager(CacheNames.BRANCHES);
    }

    @Bean
    BranchTxService branchTxService() {
      return mock(BranchTxService.class);
    }

    @Bean
    GitProvider gitProvider() {
      return mock(GitProvider.class);
    }
  }

  @Autowired BranchNonTxService service;
  @Autowired BranchTxService branchTxService;
  @Autowired GitProvider gitProvider;
  @Autowired CacheManager cacheManager;

  private Git git;
  private Repo repo;
  private String defaultBranch;

  @BeforeEach
  void setUp() throws Exception {
    reset(branchTxService, gitProvider);
    cacheManager.getCache(CacheNames.BRANCHES).clear();

    git = Git.init().setDirectory(workDir.toFile()).call();
    Path gitDir = workDir.resolve(".git");
    defaultBranch = git.getRepository().getBranch();
    Files.writeString(workDir.resolve("README.md"), "init");
    git.add().addFilepattern(".").call();
    git.commit().setMessage("init").call();

    Tenant owner = new Tenant();
    owner.setUsername("alice");
    repo = new Repo();
    repo.setId(UUID.randomUUID());
    repo.setName("repo");
    repo.setOwner(owner);
    repo.setDefaultBranch(defaultBranch);

    when(branchTxService.findRepoByOwnerAndRepoName(any(), any())).thenReturn(repo);
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

    clearInvocations(branchTxService, gitProvider);

    service.getAll("alice", "repo");

    verify(branchTxService, never()).findRepoByOwnerAndRepoName(any(), any());
    verify(gitProvider, never()).open(any(), any());
  }

  @Test
  @DisplayName("getAll uses separate cache entries for different repos")
  void getAll_separateCacheEntry_perRepo() throws Exception {
    Tenant owner = new Tenant();
    owner.setUsername("alice");
    Repo other = new Repo();
    other.setId(UUID.randomUUID());
    other.setName("other");
    other.setOwner(owner);
    other.setDefaultBranch(defaultBranch);
    when(branchTxService.findRepoByOwnerAndRepoName("alice", "other")).thenReturn(other);

    service.getAll("alice", "repo");
    service.getAll("alice", "other");

    verify(branchTxService, times(1)).findRepoByOwnerAndRepoName("alice", "repo");
    verify(branchTxService, times(1)).findRepoByOwnerAndRepoName("alice", "other");
  }

  @Test
  @DisplayName("getAll re-fetches from service after create evicts cache")
  void getAll_refetchesFromService_afterCreateEvictsCache() throws Exception {
    service.getAll("alice", "repo");
    service.create("alice", "repo", new BranchForm("feature", defaultBranch));

    clearInvocations(branchTxService, gitProvider);

    service.getAll("alice", "repo");

    verify(branchTxService, times(1)).findRepoByOwnerAndRepoName("alice", "repo");
  }

  @Test
  @DisplayName("getAll re-fetches from service after delete evicts cache")
  void getAll_refetchesFromService_afterDeleteEvictsCache() throws Exception {
    git.branchCreate().setName("feature").call();

    service.getAll("alice", "repo");
    service.delete("alice", "repo", "feature");

    clearInvocations(branchTxService, gitProvider);

    service.getAll("alice", "repo");

    verify(branchTxService, times(1)).findRepoByOwnerAndRepoName("alice", "repo");
  }
}
