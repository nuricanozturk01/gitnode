package dev.gitnode.os.pr.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.pr.entities.PullRequest;
import dev.gitnode.os.pr.repositories.PrRepository;
import dev.gitnode.os.repo.api.CollaboratorAccessPort;
import dev.gitnode.os.repo.api.CollaboratorPermission;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
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
  @Mock TenantQueryPort tenants;
  @Mock CollaboratorAccessPort access;
  @Mock org.springframework.context.ApplicationEventPublisher events;
  @InjectMocks PrReviewerService service;

  private final java.util.Map<UUID, TenantRef> accounts = new java.util.HashMap<>();

  private TenantRef user(String name) {
    return user(name, true);
  }

  private TenantRef user(String name, boolean enabled) {
    var user = new TenantRef(UUID.randomUUID(), name, name + "@example.org", null, null, enabled);
    accounts.put(user.id(), user);
    return user;
  }

  @org.junit.jupiter.api.BeforeEach
  void lookupAccounts() {
    org.mockito.Mockito.lenient()
        .when(tenants.findAllByIds(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            i ->
                ((java.util.Collection<UUID>) i.getArgument(0))
                    .stream()
                        .filter(accounts::containsKey)
                        .collect(
                            java.util.stream.Collectors.toMap(id -> id, id -> accounts.get(id))));
  }

  @Test
  void ownerCanAssignMultipleReviewersIncludingMergeCapableCollaborator() {
    var owner = user("owner");
    var reviewer = user("reviewer");
    var repo = new RepoRef(UUID.randomUUID(), owner.id(), "owner", "repo", false);
    var pr = new PullRequest();
    pr.setAuthorId(owner.id());
    pr.setStatus("OPEN");
    when(finder.findRepo("owner", "repo")).thenReturn(repo);
    when(finder.findPrForUpdate(repo.id(), 1)).thenReturn(pr);
    when(tenants.findByUsername("owner")).thenReturn(Optional.of(owner));
    when(tenants.findByUsername("reviewer")).thenReturn(Optional.of(reviewer));
    when(access.hasPermission(repo.id(), reviewer.id(), CollaboratorPermission.PULL_REQUEST_MERGE))
        .thenReturn(true);
    var result = service.replace("owner", "repo", 1, owner.id(), List.of("owner", "reviewer"));
    assertThat(result).hasSize(2).allMatch(PrReviewerService.Reviewer::canMerge);
    assertThat(pr.getReviewerIds()).containsExactlyInAnyOrder(owner.id(), reviewer.id());
  }

  @Test
  void reviewerMustRetainMergePermissionWhenMerging() {
    var owner = user("owner");
    var reviewer = user("reviewer");
    var repo = new RepoRef(UUID.randomUUID(), owner.id(), "owner", "repo", false);
    var pr = new PullRequest();
    pr.getReviewerIds().add(reviewer.id());
    assertThatThrownBy(() -> service.assertEligibleReviewer(repo, pr))
        .isInstanceOf(BadRequestException.class);
    when(access.hasPermission(repo.id(), reviewer.id(), CollaboratorPermission.PULL_REQUEST_MERGE))
        .thenReturn(true);
    service.assertEligibleReviewer(repo, pr);
    accounts.put(
        reviewer.id(),
        new TenantRef(reviewer.id(), "reviewer", "reviewer@example.org", null, null, false));
    assertThatThrownBy(() -> service.assertEligibleReviewer(repo, pr))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void strangerCannotChangeReviewers() {
    var owner = user("owner");
    var repo = new RepoRef(UUID.randomUUID(), owner.id(), "owner", "repo", false);
    var pr = new PullRequest();
    pr.setAuthorId(owner.id());
    when(finder.findRepo("owner", "repo")).thenReturn(repo);
    when(finder.findPrForUpdate(repo.id(), 1)).thenReturn(pr);
    assertThatThrownBy(
            () -> service.replace("owner", "repo", 1, UUID.randomUUID(), List.of("owner")))
        .isInstanceOf(AccessNotAllowedException.class);
  }

  @Test
  void assignmentWithoutMergeCapableReviewerIsRejected() {
    var owner = user("owner");
    var reviewer = user("reviewer");
    var repo = new RepoRef(UUID.randomUUID(), owner.id(), "owner", "repo", false);
    var pr = new PullRequest();
    pr.setAuthorId(owner.id());
    pr.setStatus("OPEN");
    when(finder.findRepo("owner", "repo")).thenReturn(repo);
    when(finder.findPrForUpdate(repo.id(), 1)).thenReturn(pr);
    when(tenants.findByUsername("reviewer")).thenReturn(Optional.of(reviewer));
    when(access.hasPermission(repo.id(), reviewer.id(), CollaboratorPermission.PULL_REQUEST_MERGE))
        .thenReturn(false);
    when(access.hasPermission(repo.id(), reviewer.id(), CollaboratorPermission.PULL_REQUEST_REVIEW))
        .thenReturn(true);
    assertThatThrownBy(() -> service.replace("owner", "repo", 1, owner.id(), List.of("reviewer")))
        .isInstanceOf(BadRequestException.class);
    assertThat(pr.getReviewerIds()).isEmpty();
  }
}
