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

import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

@Slf4j
@Service
@NullMarked
@RequiredArgsConstructor
public class RepoMigrationService {

  private static final String DEFAULT_BRANCH = "main";

  private final RepoRepository repoService;
  private final RepoStorageService gitProvider;

  @Value("${originhub.git.repo-root}")
  private String repoRoot;

  public void migrateFromGithub(
      final String repoName, final String owner, final Tenant tenant, final String accessToken)
      throws IOException {

    final var targetPath = Path.of(this.repoRoot, tenant.getUsername(), repoName + ".git");
    final var tmpDir = Files.createTempDirectory("originhub-migration-");

    try {
      this.cloneAndMoveRepo(owner, repoName, tmpDir, targetPath, accessToken);
      this.createRepo(tenant, repoName);
    } catch (final Exception e) {
      this.gitProvider.deleteRepo(tenant.getUsername(), repoName);
      throw e;
    } finally {
      if (Files.exists(tmpDir)) {
        this.gitProvider.deleteDirectory(tmpDir);
      }
    }
  }

  private void createRepo(final Tenant tenant, final String repoName) {

    final var repoOpt = this.repoService.findByOwnerIdAndName(tenant.getId(), repoName);

    if (repoOpt.isPresent()) {
      return;
    }

    final var repoObj = new Repo();
    repoObj.setOwner(tenant);
    repoObj.setName(repoName);
    repoObj.setDefaultBranch(DEFAULT_BRANCH);

    this.repoService.save(repoObj);
  }

  private void cloneAndMoveRepo(
      final String owner,
      final String repoName,
      final Path tmpDir,
      final Path targetPath,
      final String accessToken)
      throws IOException {

    final var cloneUrl = "https://github.com/%s/%s.git".formatted(owner, repoName);
    final var githubCreds = new UsernamePasswordCredentialsProvider(accessToken, "");

    try (final var git =
        Git.cloneRepository()
            .setURI(cloneUrl)
            .setDirectory(tmpDir.toFile())
            .setBare(true)
            .setMirror(true)
            .setCredentialsProvider(githubCreds)
            .call()) {

      git.remoteRemove().setRemoteName("origin").call();

      final var repo = git.getRepository();
      repo.updateRef("HEAD").link("refs/heads/main");

    } catch (final Exception e) {
      log.error("Clone failed for {}/{}: {}", owner, repoName, e.getMessage(), e);
      throw new IOException("Failed to clone repository: " + cloneUrl, e);
    }

    Files.createDirectories(targetPath.getParent());
    FileSystemUtils.deleteRecursively(targetPath);

    try {
      Files.move(tmpDir, targetPath);
    } catch (final Exception _) {
      FileSystemUtils.copyRecursively(tmpDir, targetPath);
      FileSystemUtils.deleteRecursively(tmpDir);
    }
  }
}
