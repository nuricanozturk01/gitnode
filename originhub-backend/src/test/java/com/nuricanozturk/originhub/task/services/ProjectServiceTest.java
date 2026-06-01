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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.pr.api.PrQueryPort;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.AccessNotAllowedException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.project.events.ProjectCreatedEvent;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.task.dtos.ProjectForm;
import com.nuricanozturk.originhub.task.dtos.ProjectInfo;
import com.nuricanozturk.originhub.task.entities.Project;
import com.nuricanozturk.originhub.task.mappers.ProjectMapper;
import com.nuricanozturk.originhub.task.repositories.ProjectRepository;
import com.nuricanozturk.originhub.task.repositories.TaskRepository;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectService unit tests")
class ProjectServiceTest {

  @Mock private ProjectRepository projectRepository;
  @Mock private RepoRepository repoRepository;
  @Mock private PrQueryPort prQueryPort;
  @Mock private ProjectMapper projectMapper;
  @Mock private TaskRepository taskRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private ProjectService projectService;

  @Test
  @DisplayName("create throws AccessNotAllowedException when caller is not owner")
  void create_throws_whenCallerNotOwner() {
    Tenant caller = tenant("bob");
    ProjectForm form = new ProjectForm("App", null, "APP", false);

    assertThatThrownBy(() -> projectService.create("alice", caller, form))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining("accessDenied");
  }

  @Test
  @DisplayName("create throws ErrorOccurredException when project name already exists")
  void create_throws_whenNameExists() {
    Tenant caller = tenant("alice");
    ProjectForm form = new ProjectForm("App", null, "APP", false);
    when(projectRepository.existsByOwnerIdAndName(caller.getId(), "App")).thenReturn(true);

    assertThatThrownBy(() -> projectService.create("alice", caller, form))
        .isInstanceOf(ErrorOccurredException.class)
        .hasMessageContaining("name already exists");
  }

  @Test
  @DisplayName("create saves project and publishes event")
  void create_savesProject_whenValid() {
    Tenant caller = tenant("alice");
    caller.setId(UUID.randomUUID());
    ProjectForm form = new ProjectForm("App", "desc", "APP", true);
    when(projectRepository.existsByOwnerIdAndName(caller.getId(), "App")).thenReturn(false);
    when(projectRepository.existsByOwnerIdAndCodePrefix(caller.getId(), "APP")).thenReturn(false);
    Project saved = new Project();
    saved.setId(UUID.randomUUID());
    saved.setName("App");
    saved.setCodePrefix("APP");
    when(projectRepository.save(any(Project.class))).thenReturn(saved);
    when(taskRepository.countByProjectId(saved.getId())).thenReturn(0L);
    ProjectInfo info = ProjectInfo.builder().name("App").codePrefix("APP").build();
    when(projectMapper.toInfo(saved, 0L)).thenReturn(info);

    ProjectInfo result = projectService.create("alice", caller, form);

    assertThat(result.name()).isEqualTo("App");
    verify(eventPublisher).publishEvent(any(ProjectCreatedEvent.class));
  }

  @Test
  @DisplayName("get throws AccessNotAllowedException for private project when viewer is not owner")
  void get_throws_whenPrivateAndNotOwner() {
    Project project = project("alice", "APP", false);
    when(projectRepository.findByOwnerUsernameAndCodePrefix("alice", "APP"))
        .thenReturn(Optional.of(project));
    Tenant viewer = tenant("bob");

    assertThatThrownBy(() -> projectService.get("alice", "APP", viewer))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining("accessDenied");
  }

  @Test
  @DisplayName("linkRepo throws when repository already linked")
  void linkRepo_throws_whenAlreadyLinked() {
    Tenant caller = tenant("alice");
    UUID repoId = UUID.randomUUID();
    Project project = project("alice", "APP", true);
    Repo repo = new Repo();
    repo.setId(repoId);
    project.getRepos().add(repo);
    when(projectRepository.findByOwnerUsernameAndCodePrefix("alice", "APP"))
        .thenReturn(Optional.of(project));
    when(repoRepository.findById(repoId)).thenReturn(Optional.of(repo));

    assertThatThrownBy(() -> projectService.linkRepo("alice", "APP", repoId, caller))
        .isInstanceOf(ErrorOccurredException.class)
        .hasMessageContaining("already linked");
  }

  @Test
  @DisplayName("unlinkRepo throws when repository is not linked")
  void unlinkRepo_throws_whenNotLinked() {
    Tenant caller = tenant("alice");
    Project project = project("alice", "APP", true);
    project.setRepos(new LinkedHashSet<>());
    when(projectRepository.findByOwnerUsernameAndCodePrefix("alice", "APP"))
        .thenReturn(Optional.of(project));

    assertThatThrownBy(() -> projectService.unlinkRepo("alice", "APP", UUID.randomUUID(), caller))
        .isInstanceOf(ErrorOccurredException.class)
        .hasMessageContaining("not linked");
  }

  @Test
  @DisplayName("findProject throws ItemNotFoundException when project missing")
  void findProject_throws_whenMissing() {
    when(projectRepository.findByOwnerUsernameAndCodePrefix("alice", "X"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> projectService.findProject("alice", "X"))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("Project not found");
  }

  @Test
  @DisplayName("getLinkedProjects throws AccessNotAllowedException for private repo non-owner")
  void getLinkedProjects_throws_whenPrivateRepoAndNotOwner() {
    UUID repoId = UUID.randomUUID();
    Repo repo = new Repo();
    repo.setId(repoId);
    repo.setPrivate(true);
    Tenant owner = tenant("alice");
    repo.setOwner(owner);
    when(repoRepository.findById(repoId)).thenReturn(Optional.of(repo));
    Tenant viewer = tenant("bob");

    assertThatThrownBy(() -> projectService.getLinkedProjects(repoId, viewer, 0, 10))
        .isInstanceOf(AccessNotAllowedException.class)
        .hasMessageContaining("repoAccessDenied");

    verify(projectRepository, never()).findVisibleByRepoId(any(), any(), any());
  }

  private static Tenant tenant(String username) {
    Tenant t = new Tenant();
    t.setId(UUID.randomUUID());
    t.setUsername(username);
    return t;
  }

  private static Project project(String ownerUsername, String code, boolean isPublic) {
    Tenant owner = tenant(ownerUsername);
    Project p = new Project();
    p.setId(UUID.randomUUID());
    p.setOwner(owner);
    p.setCodePrefix(code);
    p.setPublic(isPublic);
    p.setRepos(new LinkedHashSet<>());
    return p;
  }
}
