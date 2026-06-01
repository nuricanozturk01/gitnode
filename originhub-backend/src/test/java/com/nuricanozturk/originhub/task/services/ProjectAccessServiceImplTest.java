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
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.shared.project.dtos.ProjectSummary;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.task.entities.Project;
import com.nuricanozturk.originhub.task.repositories.ProjectRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectAccessServiceImpl unit tests")
class ProjectAccessServiceImplTest {

  @Mock private ProjectRepository projectRepository;

  @InjectMocks private ProjectAccessServiceImpl projectAccessService;

  @Test
  @DisplayName("findByOwnerAndCode returns empty when project not found")
  void findByOwnerAndCode_returnsEmpty_whenMissing() {
    when(projectRepository.findByOwnerUsernameAndCodePrefix("alice", "APP"))
        .thenReturn(Optional.empty());

    Optional<ProjectSummary> result = projectAccessService.findByOwnerAndCode("alice", "APP");

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("findByOwnerAndCode returns summary with id and owner username")
  void findByOwnerAndCode_returnsSummary_whenFound() {
    UUID projectId = UUID.randomUUID();
    Tenant owner = new Tenant();
    owner.setUsername("alice");
    Project project = new Project();
    project.setId(projectId);
    project.setOwner(owner);
    when(projectRepository.findByOwnerUsernameAndCodePrefix("alice", "APP"))
        .thenReturn(Optional.of(project));

    Optional<ProjectSummary> result = projectAccessService.findByOwnerAndCode("alice", "APP");

    assertThat(result)
        .hasValueSatisfying(
            summary -> {
              assertThat(summary.id()).isEqualTo(projectId);
              assertThat(summary.ownerUsername()).isEqualTo("alice");
            });
  }
}
