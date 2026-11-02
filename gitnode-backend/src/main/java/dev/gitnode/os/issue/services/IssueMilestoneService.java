package dev.gitnode.os.issue.services;

import dev.gitnode.os.issue.dtos.IssueMilestoneForm;
import dev.gitnode.os.issue.dtos.IssueMilestoneInfo;
import dev.gitnode.os.issue.entities.IssueMilestone;
import dev.gitnode.os.issue.repositories.IssueMilestoneRepository;
import dev.gitnode.os.shared.collaborator.dtos.CollaboratorPermission;
import dev.gitnode.os.shared.collaborator.services.CollaboratorAccessPort;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.repo.entities.Repo;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IssueMilestoneService {
  private final RepoRepository repos;
  private final IssueMilestoneRepository milestones;
  private final CollaboratorAccessPort access;

  public List<IssueMilestoneInfo> list(final String owner, final String repoName) {
    final var repo = this.findRepo(owner, repoName);
    return this.milestones.findAllByRepoIdOrderByTitleAsc(repo.getId()).stream()
        .map(IssueMilestoneInfo::from)
        .toList();
  }

  @Transactional
  public IssueMilestoneInfo save(
      final String owner,
      final String repoName,
      final UUID requester,
      final UUID milestoneId,
      final IssueMilestoneForm form) {
    final var repo = this.findRepo(owner, repoName);
    this.checkManage(repo, requester);
    final var milestone =
        milestoneId == null ? new IssueMilestone() : this.findMilestone(repo.getId(), milestoneId);
    final var name = form.title().trim();
    if (name.isEmpty()) throw new BadRequestException("Milestone name must not be blank");
    final var existing = this.milestones.findByRepoIdAndTitleIgnoreCase(repo.getId(), name);
    if (existing.isPresent() && !existing.get().getId().equals(milestoneId)) {
      throw new BadRequestException("Milestone name already exists");
    }
    milestone.setRepoId(repo.getId());
    milestone.setTitle(name);
    milestone.setDueDate(form.dueDate());
    milestone.setStatus(form.status());
    milestone.setDescription(form.description());
    return IssueMilestoneInfo.from(this.milestones.save(milestone));
  }

  @Transactional
  public void delete(
      final String owner, final String repoName, final UUID requester, final UUID milestoneId) {
    final var repo = this.findRepo(owner, repoName);
    this.checkManage(repo, requester);
    this.milestones.delete(this.findMilestone(repo.getId(), milestoneId));
  }

  private Repo findRepo(final String owner, final String name) {
    return this.repos
        .findByOwnerUsernameAndName(owner, name)
        .orElseThrow(() -> new ItemNotFoundException("Repo not found"));
  }

  private IssueMilestone findMilestone(final UUID repoId, final UUID id) {
    return this.milestones
        .findByIdAndRepoId(id, repoId)
        .orElseThrow(() -> new ItemNotFoundException("Milestone not found"));
  }

  private void checkManage(final Repo repo, final UUID requester) {
    if (!repo.getOwner().getId().equals(requester)
        && !this.access.hasPermission(
            repo.getId(), requester, CollaboratorPermission.ISSUE_MANAGE)) {
      throw new AccessNotAllowedException("notAuthorized");
    }
  }
}
