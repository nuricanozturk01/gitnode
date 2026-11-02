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

import dev.gitnode.os.events.pr.PullRequestMetadataChangedEvent;
import dev.gitnode.os.issue.api.RepoMetadataQueryPort;
import dev.gitnode.os.pr.dtos.PrMetadata;
import dev.gitnode.os.pr.entities.PullRequest;
import dev.gitnode.os.pr.repositories.PrRepository;
import dev.gitnode.os.shared.collaborator.dtos.CollaboratorPermission;
import dev.gitnode.os.shared.collaborator.services.CollaboratorAccessPort;
import dev.gitnode.os.shared.commit.dtos.AuthorInfo;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.repo.entities.Repo;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Labels, assignees and milestone of pull requests. */
@Service
@RequiredArgsConstructor
@NullMarked
public class PrMetadataService {

  private final PrFinder finder;
  private final PrRepository pullRequests;
  private final TenantRepository tenants;
  private final RepoMetadataQueryPort repoMetadata;
  private final CollaboratorAccessPort access;
  private final ApplicationEventPublisher events;

  @Transactional(readOnly = true)
  public PrMetadata get(
      final String owner, final String repoName, final int number, final @Nullable UUID viewerId) {
    final var repo = this.finder.findRepo(owner, repoName);
    final var pr = this.finder.findPr(repo.getId(), number);
    return this.toMetadata(repo, pr, viewerId != null && this.canEdit(repo, pr, viewerId));
  }

  @Transactional
  public PrMetadata update(
      final String owner,
      final String repoName,
      final int number,
      final UUID requesterId,
      final PrMetadata.Form form) {
    final var repo = this.finder.findRepo(owner, repoName);
    final var pr = this.finder.findPrForUpdate(repo.getId(), number);
    if (!this.canEdit(repo, pr, requesterId)) {
      throw new AccessNotAllowedException("notAuthorized");
    }
    final Set<UUID> labelIds = new HashSet<>(form.labelIds());
    final var labels = this.repoMetadata.findLabels(repo.getId(), labelIds);
    if (labels.size() != labelIds.size()) {
      throw new BadRequestException("Unknown label");
    }
    if (form.milestoneId() != null
        && this.repoMetadata.findMilestone(repo.getId(), form.milestoneId()).isEmpty()) {
      throw new BadRequestException("Unknown milestone");
    }
    final Set<Tenant> assignees = new HashSet<>();
    for (final var name : form.assignees()) {
      assignees.add(this.requireAssignee(repo, name));
    }
    final var oldAssigneeIds =
        pr.getAssignees().stream().map(Tenant::getId).collect(Collectors.toSet());
    final var labelsChanged = !pr.getLabelIds().equals(labelIds);
    final var milestoneChanged = !Objects.equals(pr.getMilestoneId(), form.milestoneId());
    pr.getLabelIds().clear();
    pr.getLabelIds().addAll(labelIds);
    pr.getAssignees().clear();
    pr.getAssignees().addAll(assignees);
    pr.setMilestoneId(form.milestoneId());
    this.pullRequests.save(pr);
    this.notifyChanges(repo, pr, requesterId, oldAssigneeIds, labelsChanged || milestoneChanged);
    return this.toMetadata(repo, pr, true);
  }

  private void notifyChanges(
      final Repo repo,
      final PullRequest pr,
      final UUID actorId,
      final Set<UUID> oldAssigneeIds,
      final boolean labelsOrMilestoneChanged) {
    final Set<UUID> added =
        pr.getAssignees().stream()
            .map(Tenant::getId)
            .filter(id -> !oldAssigneeIds.contains(id) && !id.equals(actorId))
            .collect(Collectors.toSet());
    if (!added.isEmpty()) {
      this.publish(repo, pr, actorId, "ASSIGNED", added);
    }
    if (labelsOrMilestoneChanged) {
      final Set<UUID> watchers =
          new HashSet<>(
              pr.getAuthor().getId().equals(actorId) ? Set.of() : Set.of(pr.getAuthor().getId()));
      pr.getAssignees().forEach(t -> watchers.add(t.getId()));
      watchers.remove(actorId);
      watchers.removeAll(added);
      if (!watchers.isEmpty()) {
        this.publish(repo, pr, actorId, "UPDATED", watchers);
      }
    }
  }

  private void publish(
      final Repo repo,
      final PullRequest pr,
      final UUID actorId,
      final String kind,
      final Set<UUID> recipients) {
    this.events.publishEvent(
        new PullRequestMetadataChangedEvent(
            pr.getId(),
            pr.getNumber(),
            repo.getId(),
            actorId,
            repo.getOwner().getUsername(),
            repo.getName(),
            kind,
            Set.copyOf(recipients)));
  }

  private Tenant requireAssignee(final Repo repo, final String username) {
    final var user =
        this.tenants
            .findByUsername(username.strip().toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new ItemNotFoundException("Assignee not found: " + username));
    final var isOwner = repo.getOwner().getId().equals(user.getId());
    if (!user.isEnabled()
        || (!isOwner && !this.access.isActiveCollaborator(repo.getId(), user.getId()))) {
      throw new BadRequestException("Assignee must be the owner or a collaborator: " + username);
    }
    return user;
  }

  private boolean canEdit(final Repo repo, final PullRequest pr, final UUID userId) {
    return pr.getAuthor().getId().equals(userId)
        || repo.getOwner().getId().equals(userId)
        || this.access.hasPermission(
            repo.getId(), userId, CollaboratorPermission.PULL_REQUEST_MERGE);
  }

  private PrMetadata toMetadata(final Repo repo, final PullRequest pr, final boolean canEdit) {
    final var labels =
        this.repoMetadata.findLabels(repo.getId(), pr.getLabelIds()).stream()
            .map(l -> new PrMetadata.Label(l.id(), l.name(), l.color(), l.description()))
            .sorted(Comparator.comparing(PrMetadata.Label::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    final PrMetadata.Milestone milestone =
        pr.getMilestoneId() == null
            ? null
            : this.repoMetadata
                .findMilestone(repo.getId(), pr.getMilestoneId())
                .map(m -> new PrMetadata.Milestone(m.id(), m.title(), m.status()))
                .orElse(null);
    final List<AuthorInfo> assignees =
        pr.getAssignees().stream()
            .sorted(Comparator.comparing(Tenant::getUsername, String.CASE_INSENSITIVE_ORDER))
            .map(
                t ->
                    new AuthorInfo(
                        t.getDisplayName() != null ? t.getDisplayName() : t.getUsername(),
                        t.getEmail(),
                        t.getUsername(),
                        t.getAvatarUrl() == null ? "" : t.getAvatarUrl()))
            .toList();
    return new PrMetadata(labels, assignees, milestone, canEdit);
  }
}
