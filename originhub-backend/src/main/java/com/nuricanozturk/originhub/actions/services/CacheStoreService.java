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
package com.nuricanozturk.originhub.actions.services;

import com.nuricanozturk.originhub.actions.entities.WorkflowCache;
import com.nuricanozturk.originhub.actions.repositories.WorkflowCacheRepository;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores and retrieves build caches on the local filesystem.
 *
 * <p>Restore strategy: exact key match first, then first prefix (restore-key) match.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class CacheStoreService {

  private static final int ENTRY_SIZE = 50;

  @Value("${originhub.actions.artifacts.local-path}")
  private String artifactsBasePath;

  @Value("${originhub.actions.cache.max-size-gb:10}")
  private long maxSizeGb;

  private final WorkflowCacheRepository cacheRepository;

  /**
   * Stores a cache archive for the given key. If the key already exists the file is replaced.
   *
   * @param restoreKeys optional prefix keys used as fallback during restore
   */
  @Transactional
  public WorkflowCache put(
      final UUID repoId,
      final String key,
      final @Nullable List<String> restoreKeys,
      final MultipartFile file) {

    final Path dir = Path.of(this.artifactsBasePath, "cache", repoId.toString());
    try {
      Files.createDirectories(dir);
    } catch (final IOException ex) {
      throw new ErrorOccurredException("Failed to create cache directory: " + ex.getMessage());
    }

    final Path target = dir.resolve(this.sanitizeKey(key) + ".tar.gz");
    try {
      file.transferTo(target);
    } catch (final IOException ex) {
      throw new ErrorOccurredException("Failed to store cache: " + ex.getMessage());
    }

    final var existing = this.cacheRepository.findByRepoIdAndCacheKey(repoId, key);
    final WorkflowCache entry = existing.orElseGet(WorkflowCache::new);
    entry.setRepoId(repoId);
    entry.setCacheKey(key);
    entry.setRestoreKeys(restoreKeys);
    entry.setFilePath(target.toString());
    entry.setSizeBytes(target.toFile().length());
    entry.setAccessedAt(Instant.now());

    final var saved = this.cacheRepository.save(entry);
    log.info("Cache stored: repo={} key={} size={}", repoId, key, saved.getSizeBytes());
    return saved;
  }

  /**
   * Retrieves the best cache hit as a {@link Resource}. Returns {@link Optional#empty()} when no
   * match is found.
   *
   * @param key exact cache key
   * @param restoreKeys fallback prefix keys (tried in order)
   */
  @Transactional
  public Optional<Resource> get(
      final UUID repoId, final String key, final @Nullable List<String> restoreKeys) {

    // Exact key match
    final var exact = this.cacheRepository.findByRepoIdAndCacheKey(repoId, key);
    if (exact.isPresent()) {
      return this.touchAndReturn(exact.get());
    }

    // Prefix (restore-key) fallback
    if (restoreKeys != null) {
      for (final String prefix : restoreKeys) {
        final var candidates =
            this.cacheRepository.findByRepoIdAndCacheKeyStartingWith(repoId, prefix);
        if (!candidates.isEmpty()) {
          return this.touchAndReturn(candidates.getFirst());
        }
      }
    }

    return Optional.empty();
  }

  @Scheduled(cron = "0 30 2 * * *")
  @Transactional
  public void evictByLruAndSizeLimit() {
    final long maxBytes = this.maxSizeGb * 1024L * 1024L * 1024L;
    long totalBytes = this.cacheRepository.sumTotalSizeBytes();
    if (totalBytes <= maxBytes) {
      return;
    }

    int evicted = 0;
    final var batch = PageRequest.of(0, ENTRY_SIZE);

    while (totalBytes > maxBytes) {
      final var entries = this.cacheRepository.findAllByOrderByAccessedAtAsc(batch);
      if (entries.isEmpty()) {
        break;
      }
      final long[] result = this.deleteEntries(entries, maxBytes, totalBytes);
      evicted += (int) result[0];
      totalBytes = result[1];
      if (entries.size() < ENTRY_SIZE) {
        break;
      }
    }

    if (evicted > 0) {
      log.info("Cache eviction: removed {} LRU entries", evicted);
    }
  }

  private long[] deleteEntries(
      final List<WorkflowCache> entries, final long maxBytes, final long initialTotal) {
    long totalBytes = initialTotal;
    int evicted = 0;
    for (final var entry : entries) {
      if (totalBytes <= maxBytes) {
        break;
      }
      try {
        Files.deleteIfExists(Path.of(entry.getFilePath()));
      } catch (final IOException ex) {
        log.warn("Failed to delete cache file {}: {}", entry.getFilePath(), ex.getMessage());
      }
      final long size = entry.getSizeBytes() != null ? entry.getSizeBytes() : 0L;
      this.cacheRepository.delete(entry);
      totalBytes -= size;
      evicted++;
    }
    return new long[] {evicted, totalBytes};
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private Optional<Resource> touchAndReturn(final WorkflowCache entry) {
    final Path path = Path.of(entry.getFilePath());
    if (!Files.exists(path)) {
      log.warn("Cache file missing from disk: {}", path);
      return Optional.empty();
    }
    entry.setAccessedAt(Instant.now());
    this.cacheRepository.save(entry);
    return Optional.of(new FileSystemResource(path));
  }

  private String sanitizeKey(final String key) {
    return key.replaceAll("[^a-zA-Z0-9._-]", "_");
  }
}
