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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.actions.entities.Runner;
import dev.gitnode.os.actions.entities.WorkflowRun;
import dev.gitnode.os.actions.repositories.RunnerRepository;
import dev.gitnode.os.actions.repositories.WorkflowRunRepository;
import dev.gitnode.os.actions.services.ActionsStorageAccess.Mode;
import dev.gitnode.os.identity.services.JwtUtils;
import dev.gitnode.os.repo.api.CollaboratorPermission;
import dev.gitnode.os.repo.api.RepoAccessPort;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.errorhandling.exceptions.TokenExpiredException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActionsStorageAccess")
class ActionsStorageAccessTest {

  private static final UUID RUNNER_ID = UUID.randomUUID();
  private static final UUID OWNER_ID = UUID.randomUUID();
  private static final UUID REPO_ID = UUID.randomUUID();
  private static final UUID RUN_ID = UUID.randomUUID();
  private static final String RUNNER_HEADER = "Bearer runner-jwt";

  @Mock private RunnerTokenService runnerTokens;
  @Mock private RunnerRepository runnerRepository;
  @Mock private WorkflowRunRepository runRepository;
  @Mock private RepoLookupPort repoLookup;
  @Mock private RepoAccessPort repoAccess;
  @Mock private JwtUtils jwtUtils;

  private ActionsStorageAccess access;

  @BeforeEach
  void setUp() {
    this.access =
        new ActionsStorageAccess(
            this.runnerTokens,
            this.runnerRepository,
            this.runRepository,
            this.repoLookup,
            this.repoAccess,
            this.jwtUtils);
  }

  private void runnerOf(final UUID tenantId) {
    final var runner = new Runner();
    runner.setId(RUNNER_ID);
    runner.setTenantId(tenantId);
    when(this.runnerTokens.extractRunnerId("runner-jwt")).thenReturn(RUNNER_ID);
    when(this.runnerRepository.findById(RUNNER_ID)).thenReturn(Optional.of(runner));
  }

  private void repoOwnedBy(final UUID ownerId) {
    when(this.repoLookup.findById(REPO_ID))
        .thenReturn(Optional.of(new RepoRef(REPO_ID, ownerId, "alice", "app", false)));
  }

  @Test
  @DisplayName("a runner may use the storage of its owner's repositories")
  void runnerOfTheOwnerIsAllowed() {
    this.runnerOf(OWNER_ID);
    this.repoOwnedBy(OWNER_ID);

    assertThatCode(() -> this.access.authorizeForRepo(RUNNER_HEADER, REPO_ID, Mode.READ))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("a runner of another owner is refused")
  void runnerOfAnotherOwnerIsRefused() {
    this.runnerOf(UUID.randomUUID());
    this.repoOwnedBy(OWNER_ID);

    assertThatThrownBy(() -> this.access.authorizeForRepo(RUNNER_HEADER, REPO_ID, Mode.READ))
        .isInstanceOf(AccessNotAllowedException.class);
  }

  @Test
  @DisplayName("a run resolves to its repository")
  void runResolvesToRepository() {
    final var run = new WorkflowRun();
    run.setId(RUN_ID);
    run.setRepoId(REPO_ID);
    when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    this.runnerOf(OWNER_ID);
    this.repoOwnedBy(OWNER_ID);

    assertThatCode(() -> this.access.authorizeForRun(RUNNER_HEADER, RUN_ID, Mode.READ))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("an unknown run is not found")
  void unknownRun() {
    when(this.runRepository.findById(RUN_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> this.access.authorizeForRun(RUNNER_HEADER, RUN_ID, Mode.READ))
        .isInstanceOf(ItemNotFoundException.class);
  }

  @Test
  @DisplayName("a user needs read access to download")
  void userReadChecksRepositoryAccess() {
    final var userId = UUID.randomUUID();
    when(this.runnerTokens.extractRunnerId("user-jwt"))
        .thenThrow(new TokenExpiredException("invalidRunnerToken"));
    when(this.jwtUtils.extractUserId("Bearer user-jwt")).thenReturn(userId);
    this.repoOwnedBy(OWNER_ID);

    this.access.authorizeForRepo("Bearer user-jwt", REPO_ID, Mode.READ);

    verify(this.repoAccess).assertUserCanAccessRepo(userId, "alice", "app");
  }

  @Test
  @DisplayName("a user needs Actions write permission to upload")
  void userWriteNeedsActionsWrite() {
    final var userId = UUID.randomUUID();
    when(this.runnerTokens.extractRunnerId("user-jwt"))
        .thenThrow(new TokenExpiredException("invalidRunnerToken"));
    when(this.jwtUtils.extractUserId("Bearer user-jwt")).thenReturn(userId);
    this.repoOwnedBy(OWNER_ID);

    this.access.authorizeForRepo("Bearer user-jwt", REPO_ID, Mode.WRITE);

    verify(this.repoAccess)
        .assertUserHasPermission(userId, "alice", "app", CollaboratorPermission.ACTIONS_WRITE);
  }

  @Test
  @DisplayName("a token that is neither runner nor user token is rejected")
  void invalidTokenIsRejected() {
    when(this.runnerTokens.extractRunnerId("junk"))
        .thenThrow(new TokenExpiredException("invalidRunnerToken"));
    when(this.jwtUtils.extractUserId("Bearer junk"))
        .thenThrow(new TokenExpiredException("invalidToken"));
    this.repoOwnedBy(OWNER_ID);

    assertThatThrownBy(() -> this.access.authorizeForRepo("Bearer junk", REPO_ID, Mode.READ))
        .isInstanceOf(TokenExpiredException.class);
  }
}
