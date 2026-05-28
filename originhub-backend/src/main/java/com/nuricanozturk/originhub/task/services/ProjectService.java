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
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
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
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class ProjectService {

  private final @NonNull ProjectRepository projectRepository;
  private final @NonNull TenantRepository tenantRepository;
  private final @NonNull RepoRepository repoRepository;
  private final @NonNull PrRepository prRepository;
  private final @NonNull ProjectMapper projectMapper;
  private final @NonNull TaskRepository taskRepository;

  @Transactional
  public @NonNull ProjectInfo create(
      final @NonNull String ownerUsername, final @NonNull ProjectForm form) {

    final var owner =
        this.tenantRepository
            .findByUsernameOrEmail(ownerUsername)
            .orElseThrow(() -> new ItemNotFoundException("User not found: " + ownerUsername));

    if (this.projectRepository.existsByOwnerIdAndName(owner.getId(), form.getName())) {
      throw new ErrorOccurredException("Project with this name already exists");
    }

    if (this.projectRepository.existsByOwnerIdAndCodePrefix(owner.getId(), form.getCodePrefix())) {
      throw new ErrorOccurredException("Project with this code prefix already exists");
    }

    final var project = new Project();
    project.setOwner(owner);
    project.setName(form.getName());
    project.setDescription(form.getDescription());
    project.setCodePrefix(form.getCodePrefix());
    project.setTaskSeq(0);
    project.setSyncTaskStatusOnPrMerge(true);

    final var saved = this.projectRepository.save(project);
    return this.projectMapper.toInfo(saved, this.taskRepository.countByProjectId(saved.getId()));
  }

  public @NonNull List<ProjectInfo> getAll(final @NonNull String ownerUsername) {
    return this.projectRepository.findAllByOwnerUsernameOrderByCreatedAtDesc(ownerUsername).stream()
        .map(p -> this.projectMapper.toInfo(p, this.taskRepository.countByProjectId(p.getId())))
        .toList();
  }

  public @NonNull ProjectInfo get(
      final @NonNull String ownerUsername, final @NonNull String codePrefix) {
    final var project = this.findProject(ownerUsername, codePrefix);
    return this.projectMapper.toInfo(
        project, this.taskRepository.countByProjectId(project.getId()));
  }

  @Transactional
  public @NonNull ProjectInfo update(
      final @NonNull String ownerUsername,
      final @NonNull String codePrefix,
      final @NonNull ProjectUpdateForm form) {

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

    final var updated = this.projectRepository.save(project);
    return this.projectMapper.toInfo(
        updated, this.taskRepository.countByProjectId(updated.getId()));
  }

  @Transactional
  public void delete(final @NonNull String ownerUsername, final @NonNull String codePrefix) {
    final var project = this.findProject(ownerUsername, codePrefix);
    this.projectRepository.delete(project);
  }

  @Transactional
  public void linkRepo(
      final @NonNull String ownerUsername,
      final @NonNull String codePrefix,
      final @NonNull UUID repoId) {

    final var project = this.findProject(ownerUsername, codePrefix);

    final var repo =
        this.repoRepository
            .findById(repoId)
            .orElseThrow(() -> new ItemNotFoundException("Repository not found"));

    if (repo.getProjectId() != null) {
      throw new ErrorOccurredException("Repository is already linked to a project");
    }

    this.repoRepository.linkToProject(repoId, project.getId());
  }

  @Transactional
  public void unlinkRepo(
      final @NonNull String ownerUsername,
      final @NonNull String codePrefix,
      final @NonNull UUID repoId) {

    final var project = this.findProject(ownerUsername, codePrefix);

    final var repo =
        this.repoRepository
            .findById(repoId)
            .orElseThrow(() -> new ItemNotFoundException("Repository not found"));

    if (!project.getId().equals(repo.getProjectId())) {
      throw new ErrorOccurredException("Repository is not linked to this project");
    }

    this.repoRepository.unlinkFromProject(repoId);
  }

  public @NonNull List<ProjectRepoInfo> getLinkedRepos(
      final @NonNull String ownerUsername, final @NonNull String codePrefix) {

    final var project = this.findProject(ownerUsername, codePrefix);
    final var repos = this.repoRepository.findAllByProjectId(project.getId());

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

  @NonNull Project findProject(
      final @NonNull String ownerUsername, final @NonNull String codePrefix) {
    return this.projectRepository
        .findByOwnerUsernameAndCodePrefix(ownerUsername, codePrefix)
        .orElseThrow(() -> new ItemNotFoundException("Project not found: " + codePrefix));
  }
}
