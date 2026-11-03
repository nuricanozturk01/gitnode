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
import dev.gitnode.os.repo.api.CollaboratorAccessPort;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IssueMilestoneServiceTest {
  @Mock private RepoLookupPort repos;
  @Mock private IssueMilestoneRepository milestones;
  @Mock private CollaboratorAccessPort access;
  @InjectMocks private IssueMilestoneService service;

  private RepoRef repo(UUID ownerId, UUID repoId) {
    return new RepoRef(repoId, ownerId, "owner", "repo", false);
  }

  @Test
  void ownerCreatesNormalizedMilestone() {
    var owner = UUID.randomUUID();
    var repo = repo(owner, UUID.randomUUID());
    when(repos.find("owner", "repo")).thenReturn(Optional.of(repo));
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
    when(repos.find("owner", "repo")).thenReturn(Optional.of(repo));
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
    when(repos.find("owner", "repo")).thenReturn(Optional.of(repo));
    when(milestones.findByRepoIdAndTitleIgnoreCase(repo.id(), "BUG"))
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
    when(repos.find("owner", "repo")).thenReturn(Optional.of(repo));
    when(milestones.findByIdAndRepoId(id, repo.id())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.delete("owner", "repo", owner, id))
        .isInstanceOf(ItemNotFoundException.class);
    verify(milestones, never()).delete(any());
  }
}
