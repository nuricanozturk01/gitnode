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

import dev.gitnode.os.actions.repositories.RunnerRepository;
import dev.gitnode.os.actions.repositories.WorkflowRunRepository;
import dev.gitnode.os.identity.api.TokenUserResolver;
import dev.gitnode.os.repo.api.CollaboratorPermission;
import dev.gitnode.os.repo.api.RepoAccessPort;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.errorhandling.exceptions.TokenExpiredException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may touch the artifacts and caches of a repository. Artifacts and caches are written and read
 * by the runner with its own token, and read by signed-in users from the UI.
 *
 * <ul>
 *   <li>A runner may use the storage of repositories owned by the account it is registered for.
 *   <li>A user needs read access to the repository to download, and Actions write permission to
 *       upload.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@NullMarked
public class ActionsStorageAccess {

  private static final String BEARER = "Bearer ";

  private final RunnerTokenService runnerTokens;
  private final RunnerRepository runnerRepository;
  private final WorkflowRunRepository runRepository;
  private final RepoLookupPort repoLookup;
  private final RepoAccessPort repoAccess;
  private final TokenUserResolver jwtUtils;

  /** What the caller wants to do with the storage. */
  public enum Mode {
    READ,
    WRITE
  }

  /** Access to the storage of the run's repository. */
  @Transactional(readOnly = true)
  public void authorizeForRun(final String authHeader, final UUID runId, final Mode mode) {
    final var run =
        this.runRepository
            .findById(runId)
            .orElseThrow(() -> new ItemNotFoundException("Workflow run not found: " + runId));
    this.authorizeForRepo(authHeader, run.getRepoId(), mode);
  }

  /** Access to the storage of a repository. */
  @Transactional(readOnly = true)
  public void authorizeForRepo(final String authHeader, final UUID repoId, final Mode mode) {

    final var token =
        authHeader.startsWith(BEARER) ? authHeader.substring(BEARER.length()) : authHeader;
    final UUID runnerId;
    try {
      runnerId = this.runnerTokens.extractRunnerId(token);
    } catch (final TokenExpiredException notARunnerToken) {
      this.authorizeUser(authHeader, this.requireRepo(repoId), mode);
      return;
    }

    final var runner =
        this.runnerRepository
            .findById(runnerId)
            .orElseThrow(() -> new AccessNotAllowedException("Unknown runner"));
    if (!this.requireRepo(repoId).ownerId().equals(runner.getTenantId())) {
      throw new AccessNotAllowedException(
          "The runner is not registered for this repository's owner");
    }
  }

  private RepoRef requireRepo(final UUID repoId) {
    return this.repoLookup
        .findById(repoId)
        .orElseThrow(() -> new ItemNotFoundException("Repository not found: " + repoId));
  }

  private void authorizeUser(final String authHeader, final RepoRef repo, final Mode mode) {
    final var userId = this.jwtUtils.extractUserId(authHeader);
    if (mode == Mode.WRITE) {
      this.repoAccess.assertUserHasPermission(
          userId, repo.ownerUsername(), repo.name(), CollaboratorPermission.ACTIONS_WRITE);
    } else {
      this.repoAccess.assertUserCanAccessRepo(userId, repo.ownerUsername(), repo.name());
    }
  }
}
