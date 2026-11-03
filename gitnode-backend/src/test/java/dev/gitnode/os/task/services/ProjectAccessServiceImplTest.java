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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.task.api.ProjectSummary;
import dev.gitnode.os.task.entities.Project;
import dev.gitnode.os.task.repositories.ProjectRepository;
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
  @Mock private TenantQueryPort tenants;

  @InjectMocks private ProjectAccessServiceImpl projectAccessService;

  @Test
  @DisplayName("findByOwnerAndCode returns empty when project not found")
  void findByOwnerAndCode_returnsEmpty_whenMissing() {
    UUID ownerId = UUID.randomUUID();
    when(tenants.findByUsername("alice")).thenReturn(Optional.of(alice(ownerId)));
    when(projectRepository.findByOwnerIdAndCodePrefix(ownerId, "APP")).thenReturn(Optional.empty());

    Optional<ProjectSummary> result = projectAccessService.findByOwnerAndCode("alice", "APP");

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("findByOwnerAndCode returns summary with id and owner username")
  void findByOwnerAndCode_returnsSummary_whenFound() {
    UUID projectId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    Project project = new Project();
    project.setId(projectId);
    project.setOwnerId(ownerId);
    when(tenants.findByUsername("alice")).thenReturn(Optional.of(alice(ownerId)));
    when(projectRepository.findByOwnerIdAndCodePrefix(ownerId, "APP"))
        .thenReturn(Optional.of(project));

    Optional<ProjectSummary> result = projectAccessService.findByOwnerAndCode("alice", "APP");

    assertThat(result)
        .hasValueSatisfying(
            summary -> {
              assertThat(summary.id()).isEqualTo(projectId);
              assertThat(summary.ownerUsername()).isEqualTo("alice");
            });
  }

  private static TenantQueryPort.TenantRef alice(UUID id) {
    return new TenantQueryPort.TenantRef(id, "alice", "alice@example.org", null, null, true);
  }
}
