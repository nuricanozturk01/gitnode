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

import com.nuricanozturk.originhub.actions.entities.WorkflowArtifact;
import com.nuricanozturk.originhub.actions.repositories.WorkflowArtifactRepository;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class ArtifactStoreService {

  private static final int CLEANUP_BATCH_SIZE = 100;

  @Value("${originhub.actions.artifacts.local-path}")
  private String artifactsBasePath;

  private final WorkflowArtifactRepository artifactRepository;

  @Transactional
  public WorkflowArtifact upload(
      final UUID runId,
      final @Nullable UUID jobId,
      final String name,
      final MultipartFile file,
      final int retentionDays) {

    final Path dir = Path.of(this.artifactsBasePath, "runs", runId.toString());
    try {
      Files.createDirectories(dir);
    } catch (final IOException ex) {
      throw new ErrorOccurredException("Failed to create artifact directory: " + ex.getMessage());
    }

    final String filename = this.sanitizeName(name) + ".tar.gz";
    final Path target = dir.resolve(filename);

    try {
      file.transferTo(target);
    } catch (final IOException ex) {
      throw new ErrorOccurredException("Failed to store artifact: " + ex.getMessage());
    }

    final Instant expiresAt =
        retentionDays > 0 ? Instant.now().plus(retentionDays, ChronoUnit.DAYS) : null;

    final var existing = this.artifactRepository.findByRunIdAndName(runId, name);
    final WorkflowArtifact artifact = existing.orElseGet(WorkflowArtifact::new);
    artifact.setRunId(runId);
    artifact.setJobId(jobId);
    artifact.setName(name);
    artifact.setFilePath(target.toString());
    artifact.setSizeBytes(target.toFile().length());
    artifact.setContentType("application/gzip");
    artifact.setExpiresAt(expiresAt);

    final var saved = this.artifactRepository.save(artifact);
    log.info("Artifact stored: run={} name={} size={}", runId, name, saved.getSizeBytes());
    return saved;
  }

  @Transactional(readOnly = true)
  public Resource download(final UUID runId, final String name) {
    final var artifact =
        this.artifactRepository
            .findByRunIdAndName(runId, name)
            .orElseThrow(
                () ->
                    new ItemNotFoundException(
                        "Artifact not found: runId=" + runId + " name=" + name));

    final Path path = Path.of(artifact.getFilePath());
    if (!Files.exists(path)) {
      throw new ItemNotFoundException("Artifact file missing from disk: " + path);
    }
    return new FileSystemResource(path);
  }

  @Transactional(readOnly = true)
  public Page<WorkflowArtifact> listByRun(final UUID runId, final Pageable pageable) {
    return this.artifactRepository.findAllByRunIdOrderByCreatedAtDesc(runId, pageable);
  }

  @Scheduled(cron = "0 0 2 * * *")
  @Transactional
  public void cleanupExpiredArtifacts() {
    final var now = Instant.now();
    final var batchSize = PageRequest.of(0, CLEANUP_BATCH_SIZE);
    int deleted = 0;

    while (true) {
      final var batch = this.artifactRepository.findAllByExpiresAtBefore(now, batchSize);
      if (batch.isEmpty()) {
        break;
      }
      for (var artifact : batch) {
        try {
          Files.deleteIfExists(Path.of(artifact.getFilePath()));
        } catch (IOException ex) {
          log.warn(
              "Failed to delete artifact file {}: {}", artifact.getFilePath(), ex.getMessage());
        }
      }
      final var ids = batch.stream().map(WorkflowArtifact::getId).toList();
      this.artifactRepository.deleteAllById(ids);
      deleted += batch.size();
      if (batch.size() < CLEANUP_BATCH_SIZE) {
        break;
      }
    }

    if (deleted > 0) {
      log.info("Artifact retention: deleted {} expired artifacts", deleted);
    }
  }

  private String sanitizeName(final String name) {
    return name.replaceAll("[^a-zA-Z0-9._-]", "_");
  }
}
