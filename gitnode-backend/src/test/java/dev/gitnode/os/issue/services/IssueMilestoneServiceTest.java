package dev.gitnode.os.issue.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.issue.dtos.IssueMilestoneForm;
import dev.gitnode.os.issue.entities.IssueMilestone;
import dev.gitnode.os.issue.repositories.IssueMilestoneRepository;
import dev.gitnode.os.shared.collaborator.services.CollaboratorAccessPort;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.repo.entities.Repo;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IssueMilestoneServiceTest {
  @Mock private RepoRepository repos;
  @Mock private IssueMilestoneRepository milestones;
  @Mock private CollaboratorAccessPort access;
  @InjectMocks private IssueMilestoneService service;

  private Repo repo(UUID ownerId, UUID repoId) {
    var owner = new Tenant();
    owner.setId(ownerId);
    var repo = new Repo();
    repo.setId(repoId);
    repo.setOwner(owner);
    return repo;
  }

  @Test
  void ownerCreatesNormalizedMilestone() {
    var owner = UUID.randomUUID();
    var repo = repo(owner, UUID.randomUUID());
    when(repos.findByOwnerUsernameAndName("owner", "repo")).thenReturn(Optional.of(repo));
    when(milestones.save(any())).thenAnswer(call -> call.getArgument(0));
    var result =
        service.save(
            "owner", "repo", owner, null, new IssueMilestoneForm(" Bug ", "A bug", null, "OPEN"));
    assertThat(result.title()).isEqualTo("Bug");
    assertThat(result.status()).isEqualTo("OPEN");
  }

  @Test
  void unauthorizedUserCannotCreateMilestone() {
    var repo = repo(UUID.randomUUID(), UUID.randomUUID());
    when(repos.findByOwnerUsernameAndName("owner", "repo")).thenReturn(Optional.of(repo));
    assertThatThrownBy(
            () ->
                service.save(
                    "owner",
                    "repo",
                    UUID.randomUUID(),
                    null,
                    new IssueMilestoneForm("bug", null, null, "OPEN")))
        .isInstanceOf(AccessNotAllowedException.class);
    verify(milestones, never()).save(any());
  }

  @Test
  void duplicateNameIsRejectedIgnoringCase() {
    var owner = UUID.randomUUID();
    var repo = repo(owner, UUID.randomUUID());
    var existing = new IssueMilestone();
    existing.setId(UUID.randomUUID());
    when(repos.findByOwnerUsernameAndName("owner", "repo")).thenReturn(Optional.of(repo));
    when(milestones.findByRepoIdAndTitleIgnoreCase(repo.getId(), "BUG"))
        .thenReturn(Optional.of(existing));
    assertThatThrownBy(
            () ->
                service.save(
                    "owner",
                    "repo",
                    owner,
                    null,
                    new IssueMilestoneForm("BUG", null, null, "OPEN")))
        .isInstanceOf(BadRequestException.class);
    verify(milestones, never()).save(any());
  }

  @Test
  void cannotDeleteMilestoneFromAnotherRepository() {
    var owner = UUID.randomUUID();
    var repo = repo(owner, UUID.randomUUID());
    var id = UUID.randomUUID();
    when(repos.findByOwnerUsernameAndName("owner", "repo")).thenReturn(Optional.of(repo));
    when(milestones.findByIdAndRepoId(id, repo.getId())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.delete("owner", "repo", owner, id))
        .isInstanceOf(ItemNotFoundException.class);
    verify(milestones, never()).delete(any());
  }
}
