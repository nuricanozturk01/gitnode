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
package com.nuricanozturk.originhub.shared.git.provider;

import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class GitProvider {

  private static final String DEFAULT_BRANCH = "main";

  @Value("${originhub.git.repo-root}")
  private String repoRoot;

  public Repository open(final String owner, final String repoName) throws IOException {

    final var path = Path.of(this.repoRoot, owner, repoName + ".git");

    if (!Files.exists(path)) {
      log.warn("Repo Not found in storage while opening.");
      throw new ItemNotFoundException("repoNotFound in storage.");
    }

    return new FileRepositoryBuilder().setGitDir(path.toFile()).readEnvironment().build();
  }

  public void createJGitRepo(final Path repoPath) throws IOException {

    try (final var repo = new FileRepositoryBuilder().setGitDir(repoPath.toFile()).build()) {

      repo.create(true);

      final var headUpdate = repo.updateRef(Constants.HEAD);
      headUpdate.link(Constants.R_HEADS + DEFAULT_BRANCH);

      this.createInitialCommit(repo);
    }
  }

  private void createInitialCommit(final Repository repo) throws IOException {

    try (final var inserter = repo.newObjectInserter()) {

      final var treeId = inserter.insert(new TreeFormatter());

      final var identity =
          new PersonIdent(
              "OriginHub", "noreply@originhub.local", Instant.now(), java.time.ZoneOffset.UTC);

      final var commit = new CommitBuilder();
      commit.setTreeId(treeId);
      commit.setAuthor(identity);
      commit.setCommitter(identity);
      commit.setMessage("Initial commit\n");
      commit.setEncoding(StandardCharsets.UTF_8);

      final var commitId = inserter.insert(commit);
      inserter.flush();

      final var refUpdate = repo.updateRef(Constants.R_HEADS + DEFAULT_BRANCH);
      refUpdate.setNewObjectId(commitId);
      refUpdate.update();
    }
  }
}
