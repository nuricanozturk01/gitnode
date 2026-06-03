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
package com.nuricanozturk.originhub.shared.repo.services;

import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.git.provider.GitProvider;
import com.nuricanozturk.originhub.shared.repo.events.RepoInitRollbackRequestedEvent;
import com.nuricanozturk.originhub.shared.repo.events.RepoRenameRollbackRequstedEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class RepoStorageService {

  private final GitProvider gitProvider;
  private final ApplicationEventPublisher eventPublisher;

  @Value("${originhub.git.repo-root}")
  private String repoRoot;

  public void initRepo(final String repoOwner, final String repoName) {

    final var repoPath = Path.of(this.repoRoot, repoOwner, repoName + ".git");

    try {
      log.info("Repo Creating for {}, repo name: {}", repoOwner, repoName);

      Files.createDirectories(repoPath);
      this.gitProvider.createJGitRepo(repoPath);
    } catch (final IOException _) {
      FileSystemUtils.deleteRecursively(repoPath.toFile());
      this.eventPublisher.publishEvent(new RepoInitRollbackRequestedEvent(repoOwner, repoName));
      throw new ErrorOccurredException("Failed to initialize git repository");
    }
  }

  public void renameRepo(final String owner, final String oldName, final String newRepoName) {

    try {
      final var oldPath = Path.of(this.repoRoot, owner, oldName + ".git");
      final var newPath = Path.of(this.repoRoot, owner, newRepoName + ".git");
      Files.move(oldPath, newPath, StandardCopyOption.ATOMIC_MOVE);
    } catch (final IOException _) {
      log.warn("Repo Rename operation was unsuccessful!. Rollback operation started.");
      this.eventPublisher.publishEvent(
          new RepoRenameRollbackRequstedEvent(oldName, oldName, newRepoName));
      throw new ErrorOccurredException("Failed to rename repository on disk");
    }
  }

  public void deleteRepo(final String owner, final String repoName) {

    try {
      final var path = Path.of(this.repoRoot, owner, repoName + ".git");
      log.info("Deleting Repo: {}", path);
      FileSystemUtils.deleteRecursively(path);
    } catch (final IOException e) {
      log.warn("IOException Occurred while repo deleting:", e);
      throw new ErrorOccurredException("Repo Delete Error: %s".formatted(e.getMessage()));
    }
  }

  public void renameBaseDir(final String oldUsername, final String newUsername) {

    if (oldUsername.equals(newUsername)) {
      return;
    }

    final var oldPath = Path.of(this.repoRoot, oldUsername);
    final var newPath = Path.of(this.repoRoot, newUsername);

    try {
      FileSystemUtils.deleteRecursively(newPath);
      Files.move(oldPath, newPath, StandardCopyOption.ATOMIC_MOVE);
    } catch (final IOException e) {
      log.error("IO Exception: ", e);
      throw new ErrorOccurredException("IO Exception Occurred: " + e.getMessage());
    }
  }

  public void deleteTenantRepos(final String username) {

    try {
      final var path = Path.of(this.repoRoot, username);

      FileSystemUtils.deleteRecursively(path);
    } catch (final IOException ex) {
      log.error("IOException Occurred while deleting a {} repos.", username);
      throw new ErrorOccurredException("IO Exception Occurred: " + ex.getMessage());
    }
  }

  public void forkRepo(
      final String sourceOwner,
      final String sourceName,
      final String destOwner,
      final String destName) {

    final var sourcePath = Path.of(this.repoRoot, sourceOwner, sourceName + ".git");
    final var destPath = Path.of(this.repoRoot, destOwner, destName + ".git");

    try {
      Files.createDirectories(destPath.getParent());
      try (final var git =
          Git.cloneRepository()
              .setURI(sourcePath.toUri().toString())
              .setDirectory(destPath.toFile())
              .setBare(true)
              .setMirror(true)
              .call()) {
        git.remoteRemove().setRemoteName("origin").call();
      }
    } catch (final Exception e) {
      FileSystemUtils.deleteRecursively(destPath.toFile());
      log.error(
          "Fork failed for {}/{} -> {}/{}: {}",
          sourceOwner,
          sourceName,
          destOwner,
          destName,
          e.getMessage(),
          e);
      throw new ErrorOccurredException("Failed to fork repository");
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void deleteDirectory(final Path path) throws IOException {

    try (var walk = Files.walk(path)) {

      walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
    }
  }
}
