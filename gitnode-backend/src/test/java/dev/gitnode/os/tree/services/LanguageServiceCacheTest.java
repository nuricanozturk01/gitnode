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
package dev.gitnode.os.tree.services;

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
import java.nio.file.Files;
import java.nio.file.Path;
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
@DisplayName("LanguageService cache tests")
class LanguageServiceCacheTest {

  @TempDir Path workDir;

  @Configuration
  @EnableCaching(proxyTargetClass = true)
  @Import(LanguageService.class)
  static class Config {
    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager(CacheNames.LANGUAGES);
    }

    @Bean
    GitProvider gitProvider() {
      return mock(GitProvider.class);
    }
  }

  @Autowired LanguageService service;
  @Autowired GitProvider gitProvider;
  @Autowired CacheManager cacheManager;

  private Git git;
  private String defaultBranch;

  @BeforeEach
  void setUp() throws Exception {
    reset(gitProvider);
    cacheManager.getCache(CacheNames.LANGUAGES).clear();

    git = Git.init().setDirectory(workDir.toFile()).call();
    Path gitDir = workDir.resolve(".git");
    defaultBranch = git.getRepository().getBranch();
    Files.writeString(workDir.resolve("Main.java"), "class Main {}");
    git.add().addFilepattern(".").call();
    git.commit().setMessage("init").call();

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
  @DisplayName(
      "detectLanguages returns cached result on second call — gitProvider not invoked again")
  void detectLanguages_returnsCachedResult_onSecondCall() throws Exception {
    service.detectLanguages("alice", "repo", defaultBranch);

    clearInvocations(gitProvider);

    service.detectLanguages("alice", "repo", defaultBranch);

    verify(gitProvider, never()).open(any(), any());
  }

  @Test
  @DisplayName("detectLanguages uses separate cache entries for different branches")
  void detectLanguages_separateCacheEntry_perBranch() throws Exception {
    git.branchCreate().setName("develop").setStartPoint(defaultBranch).call();

    service.detectLanguages("alice", "repo", defaultBranch);

    clearInvocations(gitProvider);

    // different branch key — must miss cache and call service again
    service.detectLanguages("alice", "repo", "develop");

    verify(gitProvider, times(1)).open("alice", "repo");
  }
}
