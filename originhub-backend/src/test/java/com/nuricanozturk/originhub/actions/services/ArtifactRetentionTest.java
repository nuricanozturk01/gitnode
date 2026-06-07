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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.actions.entities.WorkflowArtifact;
import com.nuricanozturk.originhub.actions.repositories.WorkflowArtifactRepository;
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
@DisplayName("ArtifactStoreService retention / cleanup tests")
class ArtifactRetentionTest {

  @TempDir Path tempDir;

  @Mock private WorkflowArtifactRepository artifactRepository;

  @InjectMocks private ArtifactStoreService service;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "artifactsBasePath", tempDir.toString());
  }

  @Test
  @DisplayName("cleanupExpiredArtifacts deletes expired artifacts from db and disk")
  void cleanupExpired_deletesExpiredArtifactsFromDbAndDisk() throws Exception {
    final var file1 = tempDir.resolve("artifact1.tar.gz");
    final var file2 = tempDir.resolve("artifact2.tar.gz");
    Files.write(file1, new byte[] {1, 2, 3});
    Files.write(file2, new byte[] {4, 5, 6});

    final var a1 = new WorkflowArtifact();
    a1.setId(UUID.randomUUID());
    a1.setRunId(UUID.randomUUID());
    a1.setName("artifact1");
    a1.setFilePath(file1.toString());
    a1.setExpiresAt(Instant.now().minusSeconds(3600));

    final var a2 = new WorkflowArtifact();
    a2.setId(UUID.randomUUID());
    a2.setRunId(UUID.randomUUID());
    a2.setName("artifact2");
    a2.setFilePath(file2.toString());
    a2.setExpiresAt(Instant.now().minusSeconds(7200));

    when(artifactRepository.findAllByExpiresAtBefore(any(Instant.class), any(Pageable.class)))
        .thenReturn(List.of(a1, a2));

    service.cleanupExpiredArtifacts();

    verify(artifactRepository).deleteAllById(eq(List.of(a1.getId(), a2.getId())));
    assertThat(Files.exists(file1)).isFalse();
    assertThat(Files.exists(file2)).isFalse();
  }

  @Test
  @DisplayName("cleanupExpiredArtifacts skips delete when no expired artifacts exist")
  void cleanupExpired_skipsWhenNoneExpired() {
    when(artifactRepository.findAllByExpiresAtBefore(any(Instant.class), any(Pageable.class)))
        .thenReturn(List.of());

    service.cleanupExpiredArtifacts();

    verify(artifactRepository, never()).deleteAllById(any());
  }

  @Test
  @DisplayName("cleanupExpiredArtifacts still calls repo.delete even when artifact file is missing")
  void cleanupExpired_continuesWhenFileMissing() {
    final var nonExistentPath = tempDir.resolve("ghost-artifact.tar.gz").toString();

    final var a1 = new WorkflowArtifact();
    a1.setId(UUID.randomUUID());
    a1.setRunId(UUID.randomUUID());
    a1.setName("ghost");
    a1.setFilePath(nonExistentPath);
    a1.setExpiresAt(Instant.now().minusSeconds(3600));

    when(artifactRepository.findAllByExpiresAtBefore(any(Instant.class), any(Pageable.class)))
        .thenReturn(List.of(a1));

    service.cleanupExpiredArtifacts();

    verify(artifactRepository).deleteAllById(eq(List.of(a1.getId())));
  }
}
