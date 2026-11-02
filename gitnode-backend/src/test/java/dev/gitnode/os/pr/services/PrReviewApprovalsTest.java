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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.gitnode.os.pr.entities.PrReview;
import dev.gitnode.os.pr.entities.PullRequest;
import dev.gitnode.os.pr.repositories.PrReviewRepository;
import dev.gitnode.os.pr.services.PrReviewService.BranchHeads;
import dev.gitnode.os.shared.collaborator.dtos.CollaboratorPermission;
import dev.gitnode.os.shared.collaborator.services.CollaboratorAccessPort;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.git.provider.GitProvider;
import dev.gitnode.os.shared.repo.entities.Repo;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PrReviewService.assertApprovals")
class PrReviewApprovalsTest {

  private static final String SOURCE = "a".repeat(40);
  private static final String TARGET = "b".repeat(40);

  private final PrReviewRepository reviews = mock(PrReviewRepository.class);
  private final CollaboratorAccessPort access = mock(CollaboratorAccessPort.class);
  private final PrReviewService service =
      new PrReviewService(
          mock(PrFinder.class),
          reviews,
          access,
          mock(GitProvider.class),
          mock(org.springframework.context.ApplicationEventPublisher.class));
  private final Repo repo = new Repo();
  private final PullRequest pr = new PullRequest();
  private final List<PrReview> history = new ArrayList<>();
  private final BranchHeads heads = new BranchHeads(SOURCE, TARGET);

  @BeforeEach
  void setUp() {
    repo.setId(UUID.randomUUID());
    repo.setOwner(user("owner"));
    pr.setId(UUID.randomUUID());
    pr.setAuthor(user("author"));
    when(reviews.findAllByPrId(any())).thenReturn(history);
    when(access.hasPermission(any(), any(), eq(CollaboratorPermission.PULL_REQUEST_REVIEW)))
        .thenReturn(true);
  }

  private Tenant user(final String name) {
    final var tenant = new Tenant();
    tenant.setId(UUID.randomUUID());
    tenant.setUsername(name);
    tenant.setEnabled(true);
    return tenant;
  }

  private void review(final Tenant author, final String state, final String sha) {
    final var review = new PrReview();
    review.setAuthor(author);
    review.setState(state);
    review.setCommitSha(sha);
    review.setTargetSha(TARGET);
    history.add(review);
  }

  @Test
  @DisplayName("another reviewer's change request blocks despite enough approvals")
  void changeRequestBlocksOtherApprovals() {
    final var alice = user("alice");
    final var bob = user("bob");
    review(alice, "APPROVED", SOURCE);
    review(bob, "CHANGES_REQUESTED", "old".repeat(10));

    assertThatThrownBy(() -> service.assertApprovals(repo, pr, heads, 1))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("bob");
  }

  @Test
  @DisplayName("the reviewer clears their own change request by approving later")
  void laterApprovalClearsChangeRequest() {
    final var bob = user("bob");
    review(bob, "CHANGES_REQUESTED", SOURCE);
    review(bob, "APPROVED", SOURCE);

    assertThatCode(() -> service.assertApprovals(repo, pr, heads, 1)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("a change request from someone who lost review access is ignored")
  void lostAccessIgnored() {
    final var alice = user("alice");
    final var bob = user("bob");
    review(alice, "APPROVED", SOURCE);
    review(bob, "CHANGES_REQUESTED", SOURCE);
    when(access.hasPermission(any(), eq(bob.getId()), any())).thenReturn(false);

    assertThatCode(() -> service.assertApprovals(repo, pr, heads, 1)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("rules without required approvals do not enforce reviews")
  void zeroApprovalsSkipsChecks() {
    review(user("bob"), "CHANGES_REQUESTED", SOURCE);

    assertThatCode(() -> service.assertApprovals(repo, pr, heads, 0)).doesNotThrowAnyException();
    assertThat(history).hasSize(1);
  }
}
