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
package dev.gitnode.os.task.services;

import dev.gitnode.os.events.project.ProjectCreatedEvent;
import dev.gitnode.os.events.project.ProjectDeletedEvent;
import dev.gitnode.os.events.project.ProjectUpdatedEvent;
import dev.gitnode.os.identity.api.AuthenticatedUser;
import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.pr.api.PrQueryPort;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.dtos.PageResponse;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.task.dtos.OpenPrInfo;
import dev.gitnode.os.task.dtos.ProjectForm;
import dev.gitnode.os.task.dtos.ProjectInfo;
import dev.gitnode.os.task.dtos.ProjectRepoInfo;
import dev.gitnode.os.task.dtos.ProjectUpdateForm;
import dev.gitnode.os.task.entities.Project;
import dev.gitnode.os.task.mappers.ProjectMapper;
import dev.gitnode.os.task.repositories.ProjectRepository;
import dev.gitnode.os.task.repositories.TaskRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
@NullMarked
public class ProjectService {

  private final ProjectRepository projectRepository;
  private final RepoLookupPort repoLookup;
  private final TenantQueryPort tenants;
  private final PrQueryPort prQueryPort;
  private final ProjectMapper projectMapper;
  private final TaskRepository taskRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public ProjectInfo create(
      final String ownerUsername, final AuthenticatedUser caller, final ProjectForm form) {

    this.requireCallerIsOwner(caller, ownerUsername);

    if (this.projectRepository.existsByOwnerIdAndName(caller.getId(), form.getName())) {
      throw new ErrorOccurredException("Project with this name already exists");
    }

    if (this.projectRepository.existsByOwnerIdAndCodePrefix(caller.getId(), form.getCodePrefix())) {
      throw new ErrorOccurredException("Project with this code prefix already exists");
    }

    final var project = new Project();
    project.setOwnerId(caller.getId());
    project.setName(form.getName());
    project.setDescription(form.getDescription());
    project.setCodePrefix(form.getCodePrefix());
    project.setTaskSeq(0);
    project.setSyncTaskStatusOnPrMerge(true);
    project.setPublic(form.isPublic());

    final var saved = this.projectRepository.save(project);
    this.eventPublisher.publishEvent(
        new ProjectCreatedEvent(saved.getId(), ownerUsername, saved.getName()));
    return this.projectMapper.toInfo(saved, this.taskRepository.countByProjectId(saved.getId()));
  }

  public PageResponse<ProjectInfo> getAll(
      final String ownerUsername,
      final @Nullable AuthenticatedUser viewer,
      final int page,
      final int size) {

    final boolean isOwner = viewer != null && viewer.getUsername().equals(ownerUsername);
    final var pageable = PageRequest.of(page, size);
    final var owner = this.tenants.findByUsername(ownerUsername);
    if (owner.isEmpty()) {
      return PageResponse.from(org.springframework.data.domain.Page.empty(pageable));
    }
    final var projects =
        isOwner
            ? this.projectRepository.findAllByOwnerIdOrderByCreatedAtDesc(
                owner.get().id(), pageable)
            : this.projectRepository.findAllByOwnerIdAndIsPublicTrueOrderByCreatedAtDesc(
                owner.get().id(), pageable);

    return PageResponse.from(
        projects.map(
            p -> this.projectMapper.toInfo(p, this.taskRepository.countByProjectId(p.getId()))));
  }

  public ProjectInfo get(
      final String ownerUsername,
      final String codePrefix,
      final @Nullable AuthenticatedUser viewer) {

    final var project = this.findProjectAsViewer(ownerUsername, codePrefix, viewer);
    return this.projectMapper.toInfo(
        project, this.taskRepository.countByProjectId(project.getId()));
  }

  @Transactional
  public ProjectInfo update(
      final String ownerUsername,
      final String codePrefix,
      final AuthenticatedUser caller,
      final ProjectUpdateForm form) {

    this.requireCallerIsOwner(caller, ownerUsername);

    final var project = this.findProject(ownerUsername, codePrefix);

    if (form.getName() != null) {
      project.setName(form.getName());
    }

    if (form.getDescription() != null) {
      project.setDescription(form.getDescription());
    }

    if (form.getSyncTaskStatusOnPrMerge() != null) {
      project.setSyncTaskStatusOnPrMerge(form.getSyncTaskStatusOnPrMerge());
    }

    if (form.getIsPublic() != null) {
      project.setPublic(form.getIsPublic());
    }

    final var updated = this.projectRepository.save(project);
    this.eventPublisher.publishEvent(
        new ProjectUpdatedEvent(updated.getId(), ownerUsername, updated.getName()));
    return this.projectMapper.toInfo(
        updated, this.taskRepository.countByProjectId(updated.getId()));
  }

  @Transactional
  public void delete(
      final String ownerUsername, final String codePrefix, final AuthenticatedUser caller) {

    this.requireCallerIsOwner(caller, ownerUsername);

    final var project = this.findProject(ownerUsername, codePrefix);
    this.eventPublisher.publishEvent(
        new ProjectDeletedEvent(project.getId(), ownerUsername, project.getName()));
    this.projectRepository.delete(project);
  }

  @Transactional
  public void linkRepo(
      final String ownerUsername,
      final String codePrefix,
      final UUID repoId,
      final AuthenticatedUser caller) {

    this.requireCallerIsOwner(caller, ownerUsername);

    final var project = this.findProject(ownerUsername, codePrefix);
    final var repo =
        this.repoLookup
            .findById(repoId)
            .orElseThrow(() -> new ItemNotFoundException("Repository not found"));

    if (project.getRepoIds().contains(repo.id())) {
      throw new ErrorOccurredException("Repository is already linked to this project");
    }

    project.getRepoIds().add(repo.id());
    this.projectRepository.save(project);
  }

  @Transactional
  public void unlinkRepo(
      final String ownerUsername,
      final String codePrefix,
      final UUID repoId,
      final AuthenticatedUser caller) {

    this.requireCallerIsOwner(caller, ownerUsername);

    final var project = this.findProject(ownerUsername, codePrefix);
    final boolean removed = project.getRepoIds().remove(repoId);
    if (!removed) {
      throw new ErrorOccurredException("Repository is not linked to this project");
    }

    this.projectRepository.save(project);
  }

  public List<ProjectRepoInfo> getLinkedRepos(
      final String ownerUsername,
      final String codePrefix,
      final @Nullable AuthenticatedUser viewer) {

    final var project = this.findProjectAsViewer(ownerUsername, codePrefix, viewer);
    final var found = this.repoLookup.findAllByIds(project.getRepoIds());

    return project.getRepoIds().stream()
        .map(found::get)
        .filter(java.util.Objects::nonNull)
        .map(
            repo -> {
              final var openPrInfos =
                  this.prQueryPort.findOpenByRepoId(repo.id()).stream()
                      .map(
                          pr ->
                              OpenPrInfo.builder()
                                  .id(pr.id())
                                  .number(pr.number())
                                  .title(pr.title())
                                  .sourceBranch(pr.sourceBranch())
                                  .targetBranch(pr.targetBranch())
                                  .build())
                      .toList();

              return ProjectRepoInfo.builder()
                  .id(repo.id())
                  .name(repo.name())
                  .ownerUsername(repo.ownerUsername())
                  .description(repo.description())
                  .defaultBranch(repo.defaultBranch())
                  .openPullRequests(openPrInfos)
                  .build();
            })
        .toList();
  }

  private void requireCallerIsOwner(final AuthenticatedUser caller, final String ownerUsername) {
    if (!caller.getUsername().equals(ownerUsername)) {
      throw new AccessNotAllowedException("accessDenied");
    }
  }

  Project findProject(final String ownerUsername, final String codePrefix) {
    return this.findByOwnerAndCode(ownerUsername, codePrefix);
  }

  /** The project of the user name, or "not found" when the user or the project is unknown. */
  private Project findByOwnerAndCode(final String ownerUsername, final String codePrefix) {
    final var owner =
        this.tenants
            .findByUsername(ownerUsername)
            .orElseThrow(() -> new ItemNotFoundException("Project not found: " + codePrefix));
    return this.projectRepository
        .findByOwnerIdAndCodePrefix(owner.id(), codePrefix)
        .orElseThrow(() -> new ItemNotFoundException("Project not found: " + codePrefix));
  }

  public PageResponse<ProjectInfo> getLinkedProjects(
      final UUID repoId, final @Nullable AuthenticatedUser viewer, final int page, final int size) {

    final var repo =
        this.repoLookup
            .findById(repoId)
            .orElseThrow(() -> new ItemNotFoundException("repoNotFound"));

    final boolean isRepoOwner = viewer != null && viewer.getId().equals(repo.ownerId());

    if (repo.isPrivate() && !isRepoOwner) {
      throw new AccessNotAllowedException("repoAccessDenied");
    }

    final var pageable = PageRequest.of(page, size);
    final var projects =
        viewer != null
            ? this.projectRepository.findVisibleByRepoId(repoId, viewer.getId(), pageable)
            : this.projectRepository.findPublicByRepoId(repoId, pageable);

    return PageResponse.from(
        projects.map(
            p -> this.projectMapper.toInfo(p, this.taskRepository.countByProjectId(p.getId()))));
  }

  Project findProjectAsViewer(
      final String ownerUsername,
      final String codePrefix,
      final @Nullable AuthenticatedUser viewer) {

    final var project = this.findByOwnerAndCode(ownerUsername, codePrefix);

    if (!project.isPublic()) {
      final boolean isOwner = viewer != null && viewer.getUsername().equals(ownerUsername);
      if (!isOwner) {
        throw new AccessNotAllowedException("accessDenied");
      }
    }

    return project;
  }
}
