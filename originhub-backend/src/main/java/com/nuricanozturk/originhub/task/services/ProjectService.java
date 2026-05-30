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
package com.nuricanozturk.originhub.task.services;

import com.nuricanozturk.originhub.pr.entities.PrStatus;
import com.nuricanozturk.originhub.pr.repositories.PrRepository;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.AccessNotAllowedException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.project.events.ProjectCreatedEvent;
import com.nuricanozturk.originhub.shared.project.events.ProjectDeletedEvent;
import com.nuricanozturk.originhub.shared.project.events.ProjectUpdatedEvent;
import com.nuricanozturk.originhub.shared.repo.dtos.PageResponse;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import com.nuricanozturk.originhub.task.dtos.OpenPrInfo;
import com.nuricanozturk.originhub.task.dtos.ProjectForm;
import com.nuricanozturk.originhub.task.dtos.ProjectInfo;
import com.nuricanozturk.originhub.task.dtos.ProjectRepoInfo;
import com.nuricanozturk.originhub.task.dtos.ProjectUpdateForm;
import com.nuricanozturk.originhub.task.entities.Project;
import com.nuricanozturk.originhub.task.mappers.ProjectMapper;
import com.nuricanozturk.originhub.task.repositories.ProjectRepository;
import com.nuricanozturk.originhub.task.repositories.TaskRepository;
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
  private final TenantRepository tenantRepository;
  private final RepoRepository repoRepository;
  private final PrRepository prRepository;
  private final ProjectMapper projectMapper;
  private final TaskRepository taskRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public ProjectInfo create(
      final String ownerUsername, final Tenant caller, final ProjectForm form) {

    if (!caller.getUsername().equals(ownerUsername)) {
      throw new AccessNotAllowedException("accessDenied");
    }

    if (this.projectRepository.existsByOwnerIdAndName(caller.getId(), form.getName())) {
      throw new ErrorOccurredException("Project with this name already exists");
    }

    if (this.projectRepository.existsByOwnerIdAndCodePrefix(caller.getId(), form.getCodePrefix())) {
      throw new ErrorOccurredException("Project with this code prefix already exists");
    }

    final var project = new Project();
    project.setOwner(caller);
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
      final String ownerUsername, final @Nullable Tenant viewer, final int page, final int size) {

    final boolean isOwner = viewer != null && viewer.getUsername().equals(ownerUsername);
    final var pageable = PageRequest.of(page, size);
    final var projects =
        isOwner
            ? this.projectRepository.findAllByOwnerUsernameOrderByCreatedAtDesc(
                ownerUsername, pageable)
            : this.projectRepository.findAllByOwnerUsernameAndIsPublicTrueOrderByCreatedAtDesc(
                ownerUsername, pageable);

    return PageResponse.from(
        projects.map(
            p -> this.projectMapper.toInfo(p, this.taskRepository.countByProjectId(p.getId()))));
  }

  public ProjectInfo get(
      final String ownerUsername, final String codePrefix, final @Nullable Tenant viewer) {

    final var project = this.findProjectAsViewer(ownerUsername, codePrefix, viewer);
    return this.projectMapper.toInfo(
        project, this.taskRepository.countByProjectId(project.getId()));
  }

  @Transactional
  public ProjectInfo update(
      final String ownerUsername,
      final String codePrefix,
      final Tenant caller,
      final ProjectUpdateForm form) {

    if (!caller.getUsername().equals(ownerUsername)) {
      throw new AccessNotAllowedException("accessDenied");
    }

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
  public void delete(final String ownerUsername, final String codePrefix, final Tenant caller) {

    if (!caller.getUsername().equals(ownerUsername)) {
      throw new AccessNotAllowedException("accessDenied");
    }

    final var project = this.findProject(ownerUsername, codePrefix);
    this.eventPublisher.publishEvent(
        new ProjectDeletedEvent(project.getId(), ownerUsername, project.getName()));
    this.projectRepository.delete(project);
  }

  @Transactional
  public void linkRepo(
      final String ownerUsername, final String codePrefix, final UUID repoId, final Tenant caller) {

    if (!caller.getUsername().equals(ownerUsername)) {
      throw new AccessNotAllowedException("accessDenied");
    }

    final var project = this.findProject(ownerUsername, codePrefix);
    final var repo =
        this.repoRepository
            .findById(repoId)
            .orElseThrow(() -> new ItemNotFoundException("Repository not found"));

    final boolean alreadyLinked =
        project.getRepos().stream().anyMatch(r -> r.getId().equals(repoId));
    if (alreadyLinked) {
      throw new ErrorOccurredException("Repository is already linked to this project");
    }

    project.getRepos().add(repo);
    this.projectRepository.save(project);
  }

  @Transactional
  public void unlinkRepo(
      final String ownerUsername, final String codePrefix, final UUID repoId, final Tenant caller) {

    if (!caller.getUsername().equals(ownerUsername)) {
      throw new AccessNotAllowedException("accessDenied");
    }

    final var project = this.findProject(ownerUsername, codePrefix);
    final boolean removed = project.getRepos().removeIf(r -> r.getId().equals(repoId));
    if (!removed) {
      throw new ErrorOccurredException("Repository is not linked to this project");
    }

    this.projectRepository.save(project);
  }

  public List<ProjectRepoInfo> getLinkedRepos(
      final String ownerUsername, final String codePrefix, final @Nullable Tenant viewer) {

    final var project = this.findProjectAsViewer(ownerUsername, codePrefix, viewer);
    final var repos = project.getRepos();

    return repos.stream()
        .map(
            repo -> {
              final var openPrs =
                  this.prRepository.findAllByRepoIdAndStatusOrderByCreatedAtDesc(
                      repo.getId(), PrStatus.OPEN.name());
              final var openPrInfos =
                  openPrs.stream()
                      .map(
                          pr ->
                              OpenPrInfo.builder()
                                  .id(pr.getId())
                                  .number(pr.getNumber())
                                  .title(pr.getTitle())
                                  .sourceBranch(pr.getSourceBranch())
                                  .targetBranch(pr.getTargetBranch())
                                  .build())
                      .toList();

              return ProjectRepoInfo.builder()
                  .id(repo.getId())
                  .name(repo.getName())
                  .ownerUsername(repo.getOwner().getUsername())
                  .description(repo.getDescription())
                  .defaultBranch(repo.getDefaultBranch())
                  .openPullRequests(openPrInfos)
                  .build();
            })
        .toList();
  }

  Project findProject(final String ownerUsername, final String codePrefix) {
    return this.projectRepository
        .findByOwnerUsernameAndCodePrefix(ownerUsername, codePrefix)
        .orElseThrow(() -> new ItemNotFoundException("Project not found: " + codePrefix));
  }

  public PageResponse<ProjectInfo> getLinkedProjects(
      final UUID repoId, final @Nullable Tenant viewer, final int page, final int size) {

    final var repo =
        this.repoRepository
            .findById(repoId)
            .orElseThrow(() -> new ItemNotFoundException("repoNotFound"));

    final boolean isRepoOwnerOrAdmin =
        viewer != null && (viewer.getId().equals(repo.getOwner().getId()) || viewer.isAdmin());

    if (repo.isPrivate() && !isRepoOwnerOrAdmin) {
      throw new AccessNotAllowedException("repoAccessDenied");
    }

    final var pageable = PageRequest.of(page, size);
    final var projects =
        viewer != null
            ? this.projectRepository.findVisibleByRepoId(repoId, viewer.getUsername(), pageable)
            : this.projectRepository.findPublicByRepoId(repoId, pageable);

    return PageResponse.from(
        projects.map(
            p -> this.projectMapper.toInfo(p, this.taskRepository.countByProjectId(p.getId()))));
  }

  Project findProjectAsViewer(
      final String ownerUsername, final String codePrefix, final @Nullable Tenant viewer) {

    final var project =
        this.projectRepository
            .findByOwnerUsernameAndCodePrefix(ownerUsername, codePrefix)
            .orElseThrow(() -> new ItemNotFoundException("Project not found: " + codePrefix));

    if (!project.isPublic()) {
      final boolean isOwner = viewer != null && viewer.getUsername().equals(ownerUsername);
      if (!isOwner) {
        throw new AccessNotAllowedException("accessDenied");
      }
    }

    return project;
  }
}
