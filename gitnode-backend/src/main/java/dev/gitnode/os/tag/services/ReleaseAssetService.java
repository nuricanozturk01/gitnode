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

import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.tag.dtos.ReleaseAssetInfo;
import dev.gitnode.os.tag.entities.ReleaseAsset;
import dev.gitnode.os.tag.repositories.ReleaseAssetRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Stores the binary files attached to releases. Permission checks happen in the controller. */
@Slf4j
@Service
@NullMarked
public class ReleaseAssetService {

  private static final int MAX_NAME_LENGTH = 255;
  private static final int MAX_CONTENT_TYPE_LENGTH = 200;
  private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9._+()\\- ]");

  private final ReleaseAssetRepository assets;
  private final Path root;
  private final long maxBytes;
  private final int maxPerRelease;
  private final long maxTotalBytes;

  public ReleaseAssetService(
      final ReleaseAssetRepository assets,
      @Value("${gitnode.releases.assets.local-path}") final String localPath,
      @Value("${gitnode.releases.assets.max-bytes:524288000}") final long maxBytes,
      @Value("${gitnode.releases.assets.max-per-release:25}") final int maxPerRelease,
      @Value("${gitnode.releases.assets.max-total-bytes:5368709120}") final long maxTotalBytes) {
    this.assets = assets;
    this.root = Path.of(localPath).toAbsolutePath().normalize();
    this.maxBytes = maxBytes;
    this.maxPerRelease = maxPerRelease;
    this.maxTotalBytes = maxTotalBytes;
  }

  /** A download: the stored file plus the data needed for the response headers. */
  public record Download(Resource resource, ReleaseAssetInfo info) {}

  @Transactional(readOnly = true)
  public List<ReleaseAssetInfo> list(final UUID releaseId) {
    return this.assets.findAllByReleaseIdOrderByNameAsc(releaseId).stream()
        .map(ReleaseAssetService::toInfo)
        .toList();
  }

  @Transactional
  public ReleaseAssetInfo upload(
      final UUID releaseId,
      final UUID uploaderId,
      final MultipartFile file,
      final @Nullable String requestedName) {
    final var name =
        this.safeName(requestedName != null ? requestedName : file.getOriginalFilename());
    this.assertAcceptable(releaseId, file, name);

    final var asset = new ReleaseAsset();
    asset.setReleaseId(releaseId);
    asset.setName(name);
    asset.setContentType(this.contentType(file.getContentType()));
    asset.setUploaderId(uploaderId);
    asset.setCreatedAt(Instant.now());
    asset.setSha256("");
    asset.setSizeBytes(0);
    final var saved = this.assets.saveAndFlush(asset);

    final var target = this.pathOf(releaseId, saved.getId());
    try {
      Files.createDirectories(target.getParent());
      final var digest = MessageDigest.getInstance("SHA-256");
      try (final InputStream in = new DigestInputStream(file.getInputStream(), digest)) {
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
      }
      saved.setSha256(HexFormat.of().formatHex(digest.digest()));
      saved.setSizeBytes(Files.size(target));
    } catch (final IOException | NoSuchAlgorithmException ex) {
      this.deleteQuietly(target);
      throw new ErrorOccurredException("Failed to store the file: " + ex.getMessage());
    }
    return toInfo(this.assets.save(saved));
  }

  /** Opens an asset and counts the download. */
  @Transactional
  public Download open(final UUID releaseId, final UUID assetId) {
    final var asset = this.find(releaseId, assetId);
    final var path = this.pathOf(releaseId, assetId);
    if (!Files.isRegularFile(path)) {
      throw new ItemNotFoundException("The file is no longer available");
    }
    this.assets.incrementDownloads(assetId);
    return new Download(new FileSystemResource(path), toInfo(asset));
  }

  @Transactional
  public void delete(final UUID releaseId, final UUID assetId) {
    final var asset = this.find(releaseId, assetId);
    this.assets.delete(asset);
    this.deleteQuietly(this.pathOf(releaseId, assetId));
  }

  /** Removes every file of a release that is being deleted; rows go with the release. */
  public void purgeFiles(final UUID releaseId) {
    final var dir = this.root.resolve(releaseId.toString()).normalize();
    if (!dir.startsWith(this.root) || !Files.isDirectory(dir)) {
      return;
    }
    try (final var files = Files.walk(dir)) {
      files.sorted(java.util.Comparator.reverseOrder()).forEach(this::deleteQuietly);
    } catch (final IOException ex) {
      log.warn("Could not remove release files {}: {}", dir, ex.getMessage());
    }
  }

  private ReleaseAsset find(final UUID releaseId, final UUID assetId) {
    return this.assets
        .findByIdAndReleaseId(assetId, releaseId)
        .orElseThrow(() -> new ItemNotFoundException("Release file not found"));
  }

  private Path pathOf(final UUID releaseId, final UUID assetId) {
    final var path =
        this.root.resolve(releaseId.toString()).resolve(assetId.toString()).normalize();
    if (!path.startsWith(this.root)) {
      throw new ErrorOccurredException("Invalid storage path");
    }
    return path;
  }

  private void deleteQuietly(final Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (final IOException ex) {
      log.warn("Could not delete {}: {}", path, ex.getMessage());
    }
  }

  private void assertAcceptable(final UUID releaseId, final MultipartFile file, final String name) {
    if (file.isEmpty()) {
      throw new BadRequestException("The file is empty");
    }
    if (file.getSize() > this.maxBytes) {
      throw new BadRequestException("The file is larger than " + this.maxBytes + " bytes");
    }
    if (this.assets.countByReleaseId(releaseId) >= this.maxPerRelease) {
      throw new BadRequestException("A release can have at most " + this.maxPerRelease + " files");
    }
    if (this.assets.sumSizeInRepositoryOf(releaseId) + file.getSize() > this.maxTotalBytes) {
      throw new BadRequestException(
          "This repository's release files would exceed the " + this.maxTotalBytes + " byte quota");
    }
    if (this.assets.existsByName(releaseId, name)) {
      throw new BadRequestException("A file named " + name + " is already attached");
    }
  }

  /** Strips any path and unusual characters so the name is safe to show and to send back. */
  String safeName(final @Nullable String original) {
    var name = original == null ? "" : original.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1).strip();
    name = UNSAFE.matcher(name).replaceAll("_");
    if (name.length() > MAX_NAME_LENGTH) {
      name = name.substring(name.length() - MAX_NAME_LENGTH);
    }
    if (name.isBlank() || name.chars().allMatch(c -> c == '.')) {
      throw new BadRequestException("The file needs a valid name");
    }
    return name;
  }

  private String contentType(final @Nullable String declared) {
    return declared == null || declared.isBlank() || declared.length() > MAX_CONTENT_TYPE_LENGTH
        ? "application/octet-stream"
        : declared;
  }

  private static ReleaseAssetInfo toInfo(final ReleaseAsset a) {
    return new ReleaseAssetInfo(
        a.getId(),
        a.getName(),
        a.getContentType(),
        a.getSizeBytes(),
        a.getSha256(),
        a.getDownloadCount(),
        a.getCreatedAt());
  }
}
