package dev.gitnode.os.issue.services;

import dev.gitnode.os.issue.dtos.IssueLabelForm;
import dev.gitnode.os.issue.dtos.IssueLabelInfo;
import dev.gitnode.os.issue.entities.IssueLabel;
import dev.gitnode.os.issue.repositories.IssueLabelRepository;
import dev.gitnode.os.repo.api.CollaboratorAccessPort;
import dev.gitnode.os.repo.api.CollaboratorPermission;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueLabelService {
  private final RepoLookupPort repos;
  private final IssueLabelRepository labels;
  private final CollaboratorAccessPort access;

  public List<IssueLabelInfo> list(final String owner, final String repoName) {
    final var repo = this.findRepo(owner, repoName);
    return this.labels.findAllByRepoIdOrderByNameAsc(repo.id()).stream()
        .map(IssueLabelInfo::from)
        .toList();
  }

  @Transactional
  public IssueLabelInfo save(
      final String owner,
      final String repoName,
      final UUID requester,
      final UUID labelId,
      final IssueLabelForm form) {
    final var repo = this.findRepo(owner, repoName);
    this.checkManage(repo, requester);
    final var label = labelId == null ? new IssueLabel() : this.findLabel(repo.id(), labelId);
    final var name = form.name().trim();
    if (name.isEmpty()) throw new BadRequestException("Label name must not be blank");
    final var existing = this.labels.findByRepoIdAndNameIgnoreCase(repo.id(), name);
    if (existing.isPresent() && !existing.get().getId().equals(labelId)) {
      throw new BadRequestException("Label name already exists");
    }
    label.setRepoId(repo.id());
    label.setName(name);
    label.setColor(form.color().toLowerCase(Locale.ROOT));
    label.setDescription(form.description());
    return IssueLabelInfo.from(this.labels.save(label));
  }

  @Transactional
  public void delete(
      final String owner, final String repoName, final UUID requester, final UUID labelId) {
    final var repo = this.findRepo(owner, repoName);
    this.checkManage(repo, requester);
    this.labels.delete(this.findLabel(repo.id(), labelId));
  }

  private RepoRef findRepo(final String owner, final String name) {
    return this.repos
        .find(owner, name)
        .orElseThrow(() -> new ItemNotFoundException("Repo not found"));
  }

  private IssueLabel findLabel(final UUID repoId, final UUID id) {
    return this.labels
        .findByIdAndRepoId(id, repoId)
        .orElseThrow(() -> new ItemNotFoundException("Label not found"));
  }

  private void checkManage(final RepoRef repo, final UUID requester) {
    if (!repo.ownerId().equals(requester)
        && !this.access.hasPermission(repo.id(), requester, CollaboratorPermission.ISSUE_MANAGE)) {
      throw new AccessNotAllowedException("notAuthorized");
    }
  }
}
