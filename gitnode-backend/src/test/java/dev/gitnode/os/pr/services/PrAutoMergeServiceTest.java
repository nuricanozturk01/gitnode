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
package dev.gitnode.os.pr.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.pr.dtos.MergeStrategy;
import dev.gitnode.os.pr.dtos.PrMergeForm;
import dev.gitnode.os.pr.dtos.PrMergeRequirements;
import dev.gitnode.os.pr.entities.PrStatus;
import dev.gitnode.os.pr.entities.PullRequest;
import dev.gitnode.os.pr.repositories.PrRepository;
import dev.gitnode.os.repo.api.CollaboratorAccessPort;
import dev.gitnode.os.repo.api.CollaboratorPermission;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PrAutoMergeService unit tests")
class PrAutoMergeServiceTest {

  @Mock private PrFinder finder;
  @Mock private PrRepository pullRequests;
  @Mock private PrReviewerService reviewers;
  @Mock private PullRequestService prService;
  @Mock private CollaboratorAccessPort access;
  @Mock private RepoLookupPort repos;

  @InjectMocks private PrAutoMergeService autoMerge;

  private final UUID ownerId = UUID.randomUUID();
  private final UUID actorId = UUID.randomUUID();
  private RepoRef repo;
  private PullRequest pr;

  @BeforeEach
  void setUp() {
    this.repo = new RepoRef(UUID.randomUUID(), this.ownerId, "alice", "demo", false);
    this.pr = new PullRequest();
    this.pr.setId(UUID.randomUUID());
    this.pr.setRepoId(this.repo.id());
    this.pr.setNumber(7);
    this.pr.setStatus(PrStatus.OPEN.name());
    this.pr.setAutoMergeStrategy("SQUASH");
    this.pr.setAutoMergeById(this.actorId);
  }

  private static PrMergeRequirements requirements(final boolean protectedBranch, final boolean ok) {
    return new PrMergeRequirements(
        protectedBranch,
        1,
        0,
        List.of(),
        List.of(),
        false,
        0,
        ok,
        false,
        false,
        List.of(),
        null,
        null);
  }

  private void actorMayMerge() {
    when(this.pullRequests.findById(this.pr.getId())).thenReturn(Optional.of(this.pr));
    when(this.repos.findById(this.repo.id())).thenReturn(Optional.of(this.repo));
    when(this.finder.findTenants(any()))
        .thenReturn(
            Map.of(this.actorId, new TenantRef(this.actorId, "bob", "b@x.io", null, null, true)));
    when(this.access.hasPermission(
            this.repo.id(), this.actorId, CollaboratorPermission.PULL_REQUEST_MERGE))
        .thenReturn(true);
  }

  @Test
  @DisplayName("attempt merges as the enabler without owner bypass once requirements pass")
  void attempt_merges_whenSatisfied() throws IOException {
    actorMayMerge();
    when(this.prService.mergeRequirements("alice", "demo", 7, null))
        .thenReturn(requirements(true, true));

    this.autoMerge.attempt(this.pr.getId());

    final var form = ArgumentCaptor.forClass(PrMergeForm.class);
    verify(this.prService)
        .merge(eq("alice"), eq("demo"), eq(7), eq(this.actorId), form.capture(), eq(false));
    org.assertj.core.api.Assertions.assertThat(form.getValue().getStrategy())
        .isEqualTo(MergeStrategy.SQUASH);
  }

  @Test
  @DisplayName("attempt waits while requirements are unmet")
  void attempt_waits_whenNotSatisfied() throws IOException {
    actorMayMerge();
    when(this.prService.mergeRequirements("alice", "demo", 7, null))
        .thenReturn(requirements(true, false));

    this.autoMerge.attempt(this.pr.getId());

    verify(this.prService, never())
        .merge(anyString(), anyString(), anyInt(), any(), any(), anyBoolean());
    verify(this.pullRequests, never()).clearAutoMerge(any());
  }

  @Test
  @DisplayName("attempt switches auto-merge off on a merge conflict")
  void attempt_clears_onConflict() throws IOException {
    actorMayMerge();
    when(this.prService.mergeRequirements("alice", "demo", 7, null))
        .thenReturn(requirements(true, true));
    when(this.prService.merge(anyString(), anyString(), anyInt(), any(), any(), anyBoolean()))
        .thenThrow(new ErrorOccurredException("Merge conflict detected"));

    this.autoMerge.attempt(this.pr.getId());

    verify(this.pullRequests).clearAutoMerge(this.pr.getId());
  }

  @Test
  @DisplayName("attempt keeps auto-merge on after a transient failure")
  void attempt_retries_onTransientFailure() throws IOException {
    actorMayMerge();
    when(this.prService.mergeRequirements("alice", "demo", 7, null))
        .thenReturn(requirements(true, true));
    when(this.prService.merge(anyString(), anyString(), anyInt(), any(), any(), anyBoolean()))
        .thenThrow(new BadRequestException("Pull request branches changed; retry the merge"));

    this.autoMerge.attempt(this.pr.getId());

    verify(this.pullRequests, never()).clearAutoMerge(any());
  }

  @Test
  @DisplayName("attempt switches auto-merge off when the enabler lost merge permission")
  void attempt_clears_whenEnablerLostPermission() throws IOException {
    when(this.pullRequests.findById(this.pr.getId())).thenReturn(Optional.of(this.pr));
    when(this.repos.findById(this.repo.id())).thenReturn(Optional.of(this.repo));
    when(this.finder.findTenants(any()))
        .thenReturn(
            Map.of(this.actorId, new TenantRef(this.actorId, "bob", "b@x.io", null, null, true)));
    when(this.access.hasPermission(
            this.repo.id(), this.actorId, CollaboratorPermission.PULL_REQUEST_MERGE))
        .thenReturn(false);

    this.autoMerge.attempt(this.pr.getId());

    verify(this.pullRequests).clearAutoMerge(this.pr.getId());
    verify(this.prService, never())
        .merge(anyString(), anyString(), anyInt(), any(), any(), anyBoolean());
  }

  @Test
  @DisplayName("enable refuses a pull request that can already merge")
  void enable_rejects_whenAlreadyMergeable() throws IOException {
    when(this.finder.findRepo("alice", "demo")).thenReturn(this.repo);
    when(this.finder.findPrForUpdate(this.repo.id(), 7)).thenReturn(this.pr);
    when(this.prService.mergeRequirements("alice", "demo", 7, this.actorId))
        .thenReturn(requirements(true, true));

    assertThatThrownBy(
            () -> this.autoMerge.enable("alice", "demo", 7, this.actorId, MergeStrategy.SQUASH))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("already mergeable");
  }

  @Test
  @DisplayName("enable refuses an unprotected target branch")
  void enable_rejects_whenUnprotected() throws IOException {
    when(this.finder.findRepo("alice", "demo")).thenReturn(this.repo);
    when(this.finder.findPrForUpdate(this.repo.id(), 7)).thenReturn(this.pr);
    when(this.prService.mergeRequirements("alice", "demo", 7, this.actorId))
        .thenReturn(requirements(false, true));

    assertThatThrownBy(
            () -> this.autoMerge.enable("alice", "demo", 7, this.actorId, MergeStrategy.SQUASH))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("protected target branch");
  }

  @Test
  @DisplayName("enable stores the strategy and the account that will merge")
  void enable_storesStrategyAndActor() throws IOException {
    this.pr.clearAutoMerge();
    when(this.finder.findRepo("alice", "demo")).thenReturn(this.repo);
    when(this.finder.findPrForUpdate(this.repo.id(), 7)).thenReturn(this.pr);
    when(this.prService.mergeRequirements("alice", "demo", 7, this.actorId))
        .thenReturn(requirements(true, false));

    this.autoMerge.enable("alice", "demo", 7, this.actorId, MergeStrategy.REBASE);

    org.assertj.core.api.Assertions.assertThat(this.pr.getAutoMergeStrategy()).isEqualTo("REBASE");
    org.assertj.core.api.Assertions.assertThat(this.pr.getAutoMergeById()).isEqualTo(this.actorId);
    verify(this.pullRequests).save(this.pr);
  }
}
