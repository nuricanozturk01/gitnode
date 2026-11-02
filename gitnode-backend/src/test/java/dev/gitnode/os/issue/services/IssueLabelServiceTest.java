package dev.gitnode.os.issue.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.issue.dtos.IssueLabelForm;
import dev.gitnode.os.issue.entities.IssueLabel;
import dev.gitnode.os.issue.repositories.IssueLabelRepository;
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
class IssueLabelServiceTest {
  @Mock private RepoRepository repos;
  @Mock private IssueLabelRepository labels;
  @Mock private CollaboratorAccessPort access;
  @InjectMocks private IssueLabelService service;

  private Repo repo(UUID ownerId, UUID repoId) {
    var owner = new Tenant();
    owner.setId(ownerId);
    var repo = new Repo();
    repo.setId(repoId);
    repo.setOwner(owner);
    return repo;
  }

  @Test
  void ownerCreatesNormalizedLabel() {
    var owner = UUID.randomUUID();
    var repo = repo(owner, UUID.randomUUID());
    when(repos.findByOwnerUsernameAndName("owner", "repo")).thenReturn(Optional.of(repo));
    when(labels.save(any())).thenAnswer(call -> call.getArgument(0));
    var result =
        service.save("owner", "repo", owner, null, new IssueLabelForm(" Bug ", "FF0000", "A bug"));
    assertThat(result.name()).isEqualTo("Bug");
    assertThat(result.color()).isEqualTo("ff0000");
  }

  @Test
  void unauthorizedUserCannotCreateLabel() {
    var repo = repo(UUID.randomUUID(), UUID.randomUUID());
    when(repos.findByOwnerUsernameAndName("owner", "repo")).thenReturn(Optional.of(repo));
    assertThatThrownBy(
            () ->
                service.save(
                    "owner",
                    "repo",
                    UUID.randomUUID(),
                    null,
                    new IssueLabelForm("bug", "ff0000", null)))
        .isInstanceOf(AccessNotAllowedException.class);
    verify(labels, never()).save(any());
  }

  @Test
  void duplicateNameIsRejectedIgnoringCase() {
    var owner = UUID.randomUUID();
    var repo = repo(owner, UUID.randomUUID());
    var existing = new IssueLabel();
    existing.setId(UUID.randomUUID());
    when(repos.findByOwnerUsernameAndName("owner", "repo")).thenReturn(Optional.of(repo));
    when(labels.findByRepoIdAndNameIgnoreCase(repo.getId(), "BUG"))
        .thenReturn(Optional.of(existing));
    assertThatThrownBy(
            () ->
                service.save(
                    "owner", "repo", owner, null, new IssueLabelForm("BUG", "ff0000", null)))
        .isInstanceOf(BadRequestException.class);
    verify(labels, never()).save(any());
  }

  @Test
  void cannotDeleteLabelFromAnotherRepository() {
    var owner = UUID.randomUUID();
    var repo = repo(owner, UUID.randomUUID());
    var id = UUID.randomUUID();
    when(repos.findByOwnerUsernameAndName("owner", "repo")).thenReturn(Optional.of(repo));
    when(labels.findByIdAndRepoId(id, repo.getId())).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.delete("owner", "repo", owner, id))
        .isInstanceOf(ItemNotFoundException.class);
    verify(labels, never()).delete(any());
  }
}
