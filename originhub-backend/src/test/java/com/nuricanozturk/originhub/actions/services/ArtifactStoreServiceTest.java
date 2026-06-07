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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.actions.entities.WorkflowArtifact;
import com.nuricanozturk.originhub.actions.repositories.WorkflowArtifactRepository;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArtifactStoreService unit tests")
class ArtifactStoreServiceTest {

  @TempDir java.nio.file.Path tempDir;

  @Mock private WorkflowArtifactRepository artifactRepository;

  @InjectMocks private ArtifactStoreService service;

  private static final UUID RUN_ID = UUID.randomUUID();
  private static final UUID JOB_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(service, "artifactsBasePath", tempDir.toString());
  }

  @Test
  @DisplayName("upload stores file and returns persisted artifact")
  void upload_storesFileAndReturns() {
    final var file =
        new MockMultipartFile("file", "test.tar.gz", "application/gzip", new byte[] {1, 2, 3});
    final var saved = new WorkflowArtifact();
    saved.setId(UUID.randomUUID());
    saved.setRunId(RUN_ID);
    saved.setName("test-results");

    when(artifactRepository.findByRunIdAndName(RUN_ID, "test-results"))
        .thenReturn(Optional.empty());
    when(artifactRepository.save(any())).thenReturn(saved);

    final var result = service.upload(RUN_ID, JOB_ID, "test-results", file, 30);

    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo("test-results");
  }

  @Test
  @DisplayName("download returns resource for existing artifact")
  void download_returnsResource() throws Exception {
    // Write a real file to tempDir so the resource check passes
    final var dir = tempDir.resolve("runs").resolve(RUN_ID.toString());
    java.nio.file.Files.createDirectories(dir);
    final var filePath = dir.resolve("test-results.tar.gz");
    java.nio.file.Files.write(filePath, new byte[] {1, 2, 3});

    final var artifact = new WorkflowArtifact();
    artifact.setName("test-results");
    artifact.setFilePath(filePath.toString());
    when(artifactRepository.findByRunIdAndName(RUN_ID, "test-results"))
        .thenReturn(Optional.of(artifact));

    final var resource = service.download(RUN_ID, "test-results");
    assertThat(resource.exists()).isTrue();
  }

  @Test
  @DisplayName("download throws when artifact not found")
  void download_throwsWhenMissing() {
    when(artifactRepository.findByRunIdAndName(RUN_ID, "ghost")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.download(RUN_ID, "ghost"))
        .isInstanceOf(ItemNotFoundException.class);
  }

  @Test
  @DisplayName("listByRun returns all artifacts for run")
  void listByRun_returnsAll() {
    final var a1 = new WorkflowArtifact();
    a1.setName("a1");
    final var a2 = new WorkflowArtifact();
    a2.setName("a2");
    when(artifactRepository.findAllByRunIdOrderByCreatedAtDesc(RUN_ID, Pageable.unpaged()))
        .thenReturn(new PageImpl<>(List.of(a1, a2)));

    assertThat(service.listByRun(RUN_ID, Pageable.unpaged()).getContent()).hasSize(2);
  }
}
