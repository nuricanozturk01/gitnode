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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.tag.entities.ReleaseAsset;
import dev.gitnode.os.tag.repositories.ReleaseAssetRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

@DisplayName("ReleaseAssetService with real files")
class ReleaseAssetServiceTest {

  @TempDir Path dir;

  private final ReleaseAssetRepository repository = mock(ReleaseAssetRepository.class);
  private final List<ReleaseAsset> store = new ArrayList<>();
  private final UUID releaseId = UUID.randomUUID();
  private final UUID uploader = UUID.randomUUID();
  private ReleaseAssetService service;

  @BeforeEach
  void setUp() {
    service = new ReleaseAssetService(repository, dir.toString(), 1024, 2, 100);
    when(repository.saveAndFlush(any(ReleaseAsset.class)))
        .thenAnswer(
            i -> {
              final ReleaseAsset asset = i.getArgument(0);
              asset.setId(UUID.randomUUID());
              store.add(asset);
              return asset;
            });
    when(repository.save(any(ReleaseAsset.class))).thenAnswer(i -> i.getArgument(0));
    when(repository.countByReleaseId(releaseId)).thenAnswer(i -> (long) store.size());
    when(repository.existsByName(any(), any()))
        .thenAnswer(
            i ->
                store.stream()
                    .anyMatch(a -> a.getName().equalsIgnoreCase(i.<String>getArgument(1))));
    when(repository.findByIdAndReleaseId(any(), any()))
        .thenAnswer(
            i -> store.stream().filter(a -> a.getId().equals(i.getArgument(0))).findFirst());
  }

  private MockMultipartFile file(final String name, final String content) {
    return new MockMultipartFile(
        "file", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("an upload is stored on disk with its size and SHA-256 and can be opened")
  void uploadAndOpen() throws Exception {
    final var info = service.upload(releaseId, uploader, file("notes.txt", "hello"), null);

    assertThat(info.name()).isEqualTo("notes.txt");
    assertThat(info.size()).isEqualTo(5);
    assertThat(info.sha256())
        .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    final var download = service.open(releaseId, info.id());
    assertThat(Files.readString(download.resource().getFile().toPath())).isEqualTo("hello");
    org.mockito.Mockito.verify(repository).incrementDownloads(info.id());
  }

  @Test
  @DisplayName("names are stripped of paths and unusual characters")
  void safeNames() {
    assertThat(service.safeName("../../etc/passwd")).isEqualTo("passwd");
    assertThat(service.safeName("C:\\temp\\build<1>.zip")).isEqualTo("build_1_.zip");
    assertThat(service.safeName("app v1.0 (final).tar.gz")).isEqualTo("app v1.0 (final).tar.gz");
    assertThatThrownBy(() -> service.safeName("..")).isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> service.safeName("   ")).isInstanceOf(BadRequestException.class);
  }

  @Test
  @DisplayName("empty, oversized, duplicate and too many files are rejected")
  void limits() {
    assertThatThrownBy(() -> service.upload(releaseId, uploader, file("a.txt", ""), null))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(
            () -> service.upload(releaseId, uploader, file("big.bin", "x".repeat(2000)), null))
        .isInstanceOf(BadRequestException.class);

    service.upload(releaseId, uploader, file("one.txt", "1"), null);
    assertThatThrownBy(() -> service.upload(releaseId, uploader, file("ONE.txt", "1"), null))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("already attached");
    service.upload(releaseId, uploader, file("two.txt", "2"), null);
    assertThatThrownBy(() -> service.upload(releaseId, uploader, file("three.txt", "3"), null))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("at most 2");
  }

  @Test
  @DisplayName("an upload that would exceed the repository quota is rejected")
  void quota() {
    when(repository.sumSizeInRepositoryOf(releaseId)).thenReturn(95L);

    assertThatThrownBy(() -> service.upload(releaseId, uploader, file("q.txt", "0123456789"), null))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("quota");
    assertThat(store).isEmpty();
  }

  @Test
  @DisplayName("deleting an asset removes its file and unknown assets are not found")
  void deleteAndMissing() throws Exception {
    final var info = service.upload(releaseId, uploader, file("gone.txt", "bye"), null);
    final var stored = dir.resolve(releaseId.toString()).resolve(info.id().toString());
    assertThat(stored).exists();

    service.delete(releaseId, info.id());

    assertThat(stored).doesNotExist();
    org.mockito.Mockito.verify(repository).delete(any(ReleaseAsset.class));
    when(repository.findByIdAndReleaseId(any(), any())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.open(releaseId, UUID.randomUUID()))
        .isInstanceOf(ItemNotFoundException.class);
  }

  @Test
  @DisplayName("purging a release removes all of its files")
  void purge() throws Exception {
    final var info = service.upload(releaseId, uploader, file("a.txt", "a"), null);
    final var folder = dir.resolve(releaseId.toString());
    assertThat(folder.resolve(info.id().toString())).exists();

    service.purgeFiles(releaseId);

    assertThat(folder).doesNotExist();
  }
}
