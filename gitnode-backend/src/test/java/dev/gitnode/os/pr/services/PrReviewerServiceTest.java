package dev.gitnode.os.pr.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.gitnode.os.pr.entities.PullRequest;
import dev.gitnode.os.pr.repositories.PrRepository;
import dev.gitnode.os.shared.collaborator.dtos.CollaboratorPermission;
import dev.gitnode.os.shared.collaborator.services.CollaboratorAccessPort;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.repo.entities.Repo;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrReviewerServiceTest {
  @Mock PrFinder finder;
  @Mock PrRepository pullRequests;
  @Mock TenantRepository tenants;
  @Mock CollaboratorAccessPort access;
  @Mock org.springframework.context.ApplicationEventPublisher events;
  @InjectMocks PrReviewerService service;

  private Tenant user(String name) {
    var user = new Tenant();
    user.setId(UUID.randomUUID());
    user.setUsername(name);
    return user;
  }

  @Test
  void ownerCanAssignMultipleReviewersIncludingMergeCapableCollaborator() {
    var owner = user("owner");
    var reviewer = user("reviewer");
    var repo = new Repo();
    repo.setId(UUID.randomUUID());
    repo.setOwner(owner);
    var pr = new PullRequest();
    pr.setAuthor(owner);
    pr.setStatus("OPEN");
    when(finder.findRepo("owner", "repo")).thenReturn(repo);
    when(finder.findPrForUpdate(repo.getId(), 1)).thenReturn(pr);
    when(tenants.findByUsername("owner")).thenReturn(Optional.of(owner));
    when(tenants.findByUsername("reviewer")).thenReturn(Optional.of(reviewer));
    when(access.hasPermission(
            repo.getId(), reviewer.getId(), CollaboratorPermission.PULL_REQUEST_MERGE))
        .thenReturn(true);
    var result = service.replace("owner", "repo", 1, owner.getId(), List.of("owner", "reviewer"));
    assertThat(result).hasSize(2).allMatch(PrReviewerService.Reviewer::canMerge);
    assertThat(pr.getReviewers()).containsExactlyInAnyOrder(owner, reviewer);
  }

  @Test
  void reviewerMustRetainMergePermissionWhenMerging() {
    var owner = user("owner");
    var reviewer = user("reviewer");
    var repo = new Repo();
    repo.setId(UUID.randomUUID());
    repo.setOwner(owner);
    var pr = new PullRequest();
    pr.getReviewers().add(reviewer);
    assertThatThrownBy(() -> service.assertEligibleReviewer(repo, pr))
        .isInstanceOf(BadRequestException.class);
    when(access.hasPermission(
            repo.getId(), reviewer.getId(), CollaboratorPermission.PULL_REQUEST_MERGE))
        .thenReturn(true);
    service.assertEligibleReviewer(repo, pr);
    reviewer.setEnabled(false);
    assertThatThrownBy(() -> service.assertEligibleReviewer(repo, pr))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void strangerCannotChangeReviewers() {
    var owner = user("owner");
    var repo = new Repo();
    repo.setId(UUID.randomUUID());
    repo.setOwner(owner);
    var pr = new PullRequest();
    pr.setAuthor(owner);
    when(finder.findRepo("owner", "repo")).thenReturn(repo);
    when(finder.findPrForUpdate(repo.getId(), 1)).thenReturn(pr);
    assertThatThrownBy(
            () -> service.replace("owner", "repo", 1, UUID.randomUUID(), List.of("owner")))
        .isInstanceOf(AccessNotAllowedException.class);
  }

  @Test
  void assignmentWithoutMergeCapableReviewerIsRejected() {
    var owner = user("owner");
    var reviewer = user("reviewer");
    var repo = new Repo();
    repo.setId(UUID.randomUUID());
    repo.setOwner(owner);
    var pr = new PullRequest();
    pr.setAuthor(owner);
    pr.setStatus("OPEN");
    when(finder.findRepo("owner", "repo")).thenReturn(repo);
    when(finder.findPrForUpdate(repo.getId(), 1)).thenReturn(pr);
    when(tenants.findByUsername("reviewer")).thenReturn(Optional.of(reviewer));
    when(access.hasPermission(
            repo.getId(), reviewer.getId(), CollaboratorPermission.PULL_REQUEST_MERGE))
        .thenReturn(false);
    when(access.hasPermission(
            repo.getId(), reviewer.getId(), CollaboratorPermission.PULL_REQUEST_REVIEW))
        .thenReturn(true);
    assertThatThrownBy(
            () -> service.replace("owner", "repo", 1, owner.getId(), List.of("reviewer")))
        .isInstanceOf(BadRequestException.class);
    assertThat(pr.getReviewers()).isEmpty();
  }
}
