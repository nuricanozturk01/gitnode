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

import dev.gitnode.os.commit.api.AuthorInfo;
import dev.gitnode.os.events.pr.PullRequestMetadataChangedEvent;
import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.issue.api.RepoMetadataQueryPort;
import dev.gitnode.os.pr.dtos.PrMetadata;
import dev.gitnode.os.pr.entities.PullRequest;
import dev.gitnode.os.pr.repositories.PrRepository;
import dev.gitnode.os.repo.api.CollaboratorAccessPort;
import dev.gitnode.os.repo.api.CollaboratorPermission;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
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
  private final TenantQueryPort tenants;
  private final RepoMetadataQueryPort repoMetadata;
  private final CollaboratorAccessPort access;
  private final ApplicationEventPublisher events;

  @Transactional(readOnly = true)
  public PrMetadata get(
      final String owner, final String repoName, final int number, final @Nullable UUID viewerId) {
    final var repo = this.finder.findRepo(owner, repoName);
    final var pr = this.finder.findPr(repo.id(), number);
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
    final var pr = this.finder.findPrForUpdate(repo.id(), number);
    if (!this.canEdit(repo, pr, requesterId)) {
      throw new AccessNotAllowedException("notAuthorized");
    }
    final Set<UUID> labelIds = new HashSet<>(form.labelIds());
    final var labels = this.repoMetadata.findLabels(repo.id(), labelIds);
    if (labels.size() != labelIds.size()) {
      throw new BadRequestException("Unknown label");
    }
    if (form.milestoneId() != null
        && this.repoMetadata.findMilestone(repo.id(), form.milestoneId()).isEmpty()) {
      throw new BadRequestException("Unknown milestone");
    }
    final Set<TenantRef> assignees = new HashSet<>();
    for (final var name : form.assignees()) {
      assignees.add(this.requireAssignee(repo, name));
    }
    final Set<UUID> oldAssigneeIds = new HashSet<>(pr.getAssigneeIds());
    final var labelsChanged = !pr.getLabelIds().equals(labelIds);
    final var milestoneChanged = !Objects.equals(pr.getMilestoneId(), form.milestoneId());
    pr.getLabelIds().clear();
    pr.getLabelIds().addAll(labelIds);
    pr.getAssigneeIds().clear();
    assignees.forEach(assignee -> pr.getAssigneeIds().add(assignee.id()));
    pr.setMilestoneId(form.milestoneId());
    this.pullRequests.save(pr);
    this.notifyChanges(repo, pr, requesterId, oldAssigneeIds, labelsChanged || milestoneChanged);
    return this.toMetadata(repo, pr, true);
  }

  private void notifyChanges(
      final RepoRef repo,
      final PullRequest pr,
      final UUID actorId,
      final Set<UUID> oldAssigneeIds,
      final boolean labelsOrMilestoneChanged) {
    final Set<UUID> added =
        pr.getAssigneeIds().stream()
            .filter(id -> !oldAssigneeIds.contains(id) && !id.equals(actorId))
            .collect(Collectors.toSet());
    if (!added.isEmpty()) {
      this.publish(repo, pr, actorId, "ASSIGNED", added);
    }
    if (labelsOrMilestoneChanged) {
      final Set<UUID> watchers =
          new HashSet<>(pr.getAuthorId().equals(actorId) ? Set.of() : Set.of(pr.getAuthorId()));
      watchers.addAll(pr.getAssigneeIds());
      watchers.remove(actorId);
      watchers.removeAll(added);
      if (!watchers.isEmpty()) {
        this.publish(repo, pr, actorId, "UPDATED", watchers);
      }
    }
  }

  private void publish(
      final RepoRef repo,
      final PullRequest pr,
      final UUID actorId,
      final String kind,
      final Set<UUID> recipients) {
    this.events.publishEvent(
        new PullRequestMetadataChangedEvent(
            pr.getId(),
            pr.getNumber(),
            repo.id(),
            actorId,
            repo.ownerUsername(),
            repo.name(),
            kind,
            Set.copyOf(recipients)));
  }

  private TenantRef requireAssignee(final RepoRef repo, final String username) {
    final var user =
        this.tenants
            .findByUsername(username.strip().toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new ItemNotFoundException("Assignee not found: " + username));
    final var isOwner = repo.ownerId().equals(user.id());
    if (!user.enabled() || (!isOwner && !this.access.isActiveCollaborator(repo.id(), user.id()))) {
      throw new BadRequestException("Assignee must be the owner or a collaborator: " + username);
    }
    return user;
  }

  private boolean canEdit(final RepoRef repo, final PullRequest pr, final UUID userId) {
    return pr.getAuthorId().equals(userId)
        || repo.ownerId().equals(userId)
        || this.access.hasPermission(repo.id(), userId, CollaboratorPermission.PULL_REQUEST_MERGE);
  }

  private PrMetadata toMetadata(final RepoRef repo, final PullRequest pr, final boolean canEdit) {
    final var labels =
        this.repoMetadata.findLabels(repo.id(), pr.getLabelIds()).stream()
            .map(l -> new PrMetadata.Label(l.id(), l.name(), l.color(), l.description()))
            .sorted(Comparator.comparing(PrMetadata.Label::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    final PrMetadata.Milestone milestone =
        pr.getMilestoneId() == null
            ? null
            : this.repoMetadata
                .findMilestone(repo.id(), pr.getMilestoneId())
                .map(m -> new PrMetadata.Milestone(m.id(), m.title(), m.status()))
                .orElse(null);
    final List<AuthorInfo> assignees =
        this.tenants.findAllByIds(pr.getAssigneeIds()).values().stream()
            .sorted(Comparator.comparing(TenantRef::username, String.CASE_INSENSITIVE_ORDER))
            .map(
                t ->
                    new AuthorInfo(
                        t.displayName() != null ? t.displayName() : t.username(),
                        t.email(),
                        t.username(),
                        t.avatarUrl() == null ? "" : t.avatarUrl()))
            .toList();
    return new PrMetadata(labels, assignees, milestone, canEdit);
  }
}
