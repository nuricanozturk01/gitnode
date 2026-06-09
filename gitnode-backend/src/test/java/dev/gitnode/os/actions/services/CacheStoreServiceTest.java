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
package dev.gitnode.os.actions.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import dev.gitnode.os.actions.entities.WorkflowCache;
import dev.gitnode.os.actions.repositories.WorkflowCacheRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("CacheStoreService unit tests")
class CacheStoreServiceTest {

  @TempDir Path tempDir;

  @Mock private WorkflowCacheRepository cacheRepository;

  @InjectMocks private CacheStoreService service;

  private static final UUID REPO_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "artifactsBasePath", tempDir.toString());
  }

  @Test
  @DisplayName("put stores cache file and returns entry")
  void put_storesFile() {
    final var file =
        new MockMultipartFile("file", "cache.tar.gz", "application/gzip", new byte[] {4, 5, 6});
    final var saved = new WorkflowCache();
    saved.setRepoId(REPO_ID);
    saved.setCacheKey("maven-abc123");

    when(cacheRepository.findByRepoIdAndCacheKey(REPO_ID, "maven-abc123"))
        .thenReturn(Optional.empty());
    when(cacheRepository.save(any())).thenReturn(saved);

    final var result = service.put(REPO_ID, "maven-abc123", List.of("maven-"), file);

    assertThat(result).isNotNull();
    assertThat(result.getCacheKey()).isEqualTo("maven-abc123");
  }

  @Test
  @DisplayName("get returns empty when no cache match")
  void get_emptyOnMiss() {
    when(cacheRepository.findByRepoIdAndCacheKey(REPO_ID, "no-key")).thenReturn(Optional.empty());
    when(cacheRepository.findByRepoIdAndCacheKeyStartingWith(eq(REPO_ID), any()))
        .thenReturn(List.of());

    final var result = service.get(REPO_ID, "no-key", List.of("maven-"));
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("get returns resource on exact key match")
  void get_exactKeyMatch() throws Exception {
    final Path dir = tempDir.resolve("cache").resolve(REPO_ID.toString());
    Files.createDirectories(dir);
    final Path cacheFile = dir.resolve("maven-abc123.tar.gz");
    Files.write(cacheFile, new byte[] {1});

    final var entry = new WorkflowCache();
    entry.setRepoId(REPO_ID);
    entry.setCacheKey("maven-abc123");
    entry.setFilePath(cacheFile.toString());

    when(cacheRepository.findByRepoIdAndCacheKey(REPO_ID, "maven-abc123"))
        .thenReturn(Optional.of(entry));
    when(cacheRepository.save(any())).thenReturn(entry);

    final var result = service.get(REPO_ID, "maven-abc123", null);
    assertThat(result).isPresent();
  }

  @Test
  @DisplayName("get uses prefix fallback when exact key misses")
  void get_prefixFallback() throws Exception {
    final Path dir = tempDir.resolve("cache").resolve(REPO_ID.toString());
    Files.createDirectories(dir);
    final Path cacheFile = dir.resolve("maven-old.tar.gz");
    Files.write(cacheFile, new byte[] {2});

    final var entry = new WorkflowCache();
    entry.setRepoId(REPO_ID);
    entry.setCacheKey("maven-old");
    entry.setFilePath(cacheFile.toString());

    when(cacheRepository.findByRepoIdAndCacheKey(REPO_ID, "maven-new"))
        .thenReturn(Optional.empty());
    when(cacheRepository.findByRepoIdAndCacheKeyStartingWith(REPO_ID, "maven-"))
        .thenReturn(List.of(entry));
    when(cacheRepository.save(any())).thenReturn(entry);

    final var result = service.get(REPO_ID, "maven-new", List.of("maven-"));
    assertThat(result).isPresent();
  }
}
