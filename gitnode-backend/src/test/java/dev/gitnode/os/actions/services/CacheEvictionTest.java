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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.actions.entities.WorkflowCache;
import dev.gitnode.os.actions.repositories.WorkflowCacheRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("CacheStoreService eviction tests")
class CacheEvictionTest {

  @TempDir Path tempDir;

  @Mock private WorkflowCacheRepository cacheRepository;

  @InjectMocks private CacheStoreService service;

  // 1 GB limit for most tests
  private static final long MAX_SIZE_GB = 1L;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "artifactsBasePath", tempDir.toString());
    ReflectionTestUtils.setField(service, "maxSizeGb", MAX_SIZE_GB);
  }

  @Test
  @DisplayName("evictByLruAndSizeLimit skips eviction when total size is under the limit")
  void evictByLru_skipsWhenUnderLimit() {
    // 100 bytes — well under 1 GB
    when(cacheRepository.sumTotalSizeBytes()).thenReturn(100L);

    service.evictByLruAndSizeLimit();

    verify(cacheRepository, never()).findAllByOrderByAccessedAtAsc(any(Pageable.class));
    verify(cacheRepository, never()).delete(any());
  }

  @Test
  @DisplayName("evictByLruAndSizeLimit evicts LRU entries until total size is under limit")
  void evictByLru_evictsLruEntries() throws Exception {
    final long oneGb = 1024L * 1024L * 1024L;
    // 3 GB total → need to free at least 2 GB
    when(cacheRepository.sumTotalSizeBytes()).thenReturn(3 * oneGb);

    final var file1 = tempDir.resolve("cache1.tar.gz");
    final var file2 = tempDir.resolve("cache2.tar.gz");
    final var file3 = tempDir.resolve("cache3.tar.gz");
    Files.write(file1, new byte[] {1});
    Files.write(file2, new byte[] {2});
    Files.write(file3, new byte[] {3});

    final var e1 = cacheEntry(file1, oneGb, Instant.now().minusSeconds(300));
    final var e2 = cacheEntry(file2, oneGb, Instant.now().minusSeconds(200));
    final var e3 = cacheEntry(file3, oneGb, Instant.now().minusSeconds(100));

    when(cacheRepository.findAllByOrderByAccessedAtAsc(any(Pageable.class)))
        .thenReturn(List.of(e1, e2, e3));

    service.evictByLruAndSizeLimit();

    // After deleting e1 (1 GB freed → 2 GB left, still over 1 GB limit)
    // After deleting e2 (2 GB freed → 1 GB left, at limit → stop)
    verify(cacheRepository, times(2)).delete(any(WorkflowCache.class));
  }

  @Test
  @DisplayName("evictByLruAndSizeLimit continues to delete from db even when file is missing")
  void evictByLru_continuesWhenFileMissing() {
    final long oneGb = 1024L * 1024L * 1024L;
    when(cacheRepository.sumTotalSizeBytes()).thenReturn(2 * oneGb);

    final var nonExistentPath = tempDir.resolve("missing-cache.tar.gz");
    final var e1 = cacheEntry(nonExistentPath, oneGb, Instant.now().minusSeconds(500));

    when(cacheRepository.findAllByOrderByAccessedAtAsc(any(Pageable.class)))
        .thenReturn(List.of(e1));

    service.evictByLruAndSizeLimit();

    verify(cacheRepository).delete(e1);
  }

  @Test
  @DisplayName("evictByLruAndSizeLimit stops evicting as soon as size drops below limit")
  void evictByLru_stopsWhenUnderLimit() throws Exception {
    final long oneGb = 1024L * 1024L * 1024L;
    // 1.5 GB total → removing 1 entry (1 GB) brings it to 512 MB — below 1 GB limit
    when(cacheRepository.sumTotalSizeBytes()).thenReturn(oneGb + oneGb / 2);

    final var file1 = tempDir.resolve("evict1.tar.gz");
    final var file2 = tempDir.resolve("keep2.tar.gz");
    final var file3 = tempDir.resolve("keep3.tar.gz");
    Files.write(file1, new byte[] {1});
    Files.write(file2, new byte[] {2});
    Files.write(file3, new byte[] {3});

    final var e1 = cacheEntry(file1, oneGb, Instant.now().minusSeconds(300));
    final var e2 = cacheEntry(file2, oneGb / 4, Instant.now().minusSeconds(200));
    final var e3 = cacheEntry(file3, oneGb / 4, Instant.now().minusSeconds(100));

    when(cacheRepository.findAllByOrderByAccessedAtAsc(any(Pageable.class)))
        .thenReturn(List.of(e1, e2, e3));

    service.evictByLruAndSizeLimit();

    verify(cacheRepository, times(1)).delete(any(WorkflowCache.class));
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private WorkflowCache cacheEntry(
      final Path filePath, final long sizeBytes, final Instant accessedAt) {
    final var entry = new WorkflowCache();
    entry.setId(UUID.randomUUID());
    entry.setRepoId(UUID.randomUUID());
    entry.setCacheKey("key-" + UUID.randomUUID());
    entry.setFilePath(filePath.toString());
    entry.setSizeBytes(sizeBytes);
    entry.setAccessedAt(accessedAt);
    return entry;
  }
}
