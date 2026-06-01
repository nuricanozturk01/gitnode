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
package com.nuricanozturk.originhub.migration.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.migration.dtos.MigrationForm;
import com.nuricanozturk.originhub.migration.dtos.MigrationItem;
import com.nuricanozturk.originhub.migration.dtos.MigrationServiceProvider;
import com.nuricanozturk.originhub.migration.dtos.MigrationStatus;
import com.nuricanozturk.originhub.migration.entities.MigrationJob;
import com.nuricanozturk.originhub.migration.repositories.MigrationJobRepository;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GitMigrationService unit tests")
class GitMigrationServiceTest {

  @Mock private MigrationJobRepository jobRepository;
  @Mock private CloudMigrationService cloudMigrationService;
  @Mock private TenantRepository tenantRepository;

  @InjectMocks private GitMigrationService gitMigrationService;

  @Test
  @DisplayName("migrate throws ItemNotFoundException when tenant is missing")
  void migrate_throws_whenTenantMissing() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    MigrationForm form = migrationForm();
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> gitMigrationService.migrate(form, tenantId, jobId))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("Tenant not found");

    verify(jobRepository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  @DisplayName("migrate saves pending job and delegates to cloud migration service")
  void migrate_savesJobAndProcesses_whenTenantExists() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    tenant.setUsername("alice");
    MigrationForm form = migrationForm();
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

    gitMigrationService.migrate(form, tenantId, jobId);

    ArgumentCaptor<MigrationJob> jobCaptor = ArgumentCaptor.forClass(MigrationJob.class);
    verify(jobRepository).save(jobCaptor.capture());
    MigrationJob saved = jobCaptor.getValue();
    assertThat(saved.getId()).isEqualTo(jobId);
    assertThat(saved.getStatus()).isEqualTo(MigrationStatus.PENDING);
    assertThat(saved.getRepoUrl()).isEqualTo(form.getUrl());
    assertThat(saved.getOwner()).isEqualTo("acme");
    assertThat(saved.getRepoName()).isEqualTo("demo");
    verify(cloudMigrationService).process(eq(saved), eq("token"), eq(tenant));
  }

  @Test
  @DisplayName("migrate marks job failed and rethrows when cloud processing fails")
  void migrate_marksFailedAndRethrows_whenProcessingFails() {
    UUID tenantId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    MigrationForm form = migrationForm();
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    doThrow(new RuntimeException("clone failed"))
        .when(cloudMigrationService)
        .process(any(MigrationJob.class), eq("token"), eq(tenant));

    assertThatThrownBy(() -> gitMigrationService.migrate(form, tenantId, jobId))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("clone failed");

    ArgumentCaptor<MigrationJob> jobCaptor = ArgumentCaptor.forClass(MigrationJob.class);
    verify(jobRepository, org.mockito.Mockito.atLeast(2)).save(jobCaptor.capture());
    assertThat(jobCaptor.getAllValues()).anyMatch(j -> j.getStatus() == MigrationStatus.FAILED);
  }

  @Test
  @DisplayName("findJob returns job from repository")
  void findJob_returnsOptionalFromRepository() {
    UUID jobId = UUID.randomUUID();
    MigrationJob job = MigrationJob.builder().id(jobId).build();
    when(jobRepository.findByJobId(jobId)).thenReturn(Optional.of(job));

    Optional<MigrationJob> result = gitMigrationService.findJob(jobId);

    assertThat(result).contains(job);
  }

  private static MigrationForm migrationForm() {
    MigrationForm form = new MigrationForm();
    form.setService(MigrationServiceProvider.GITHUB);
    form.setUrl("https://github.com/acme/demo");
    form.setAccessToken("token");
    form.setOwner("acme");
    form.setRepoName("demo");
    form.setMigrationItems(List.of(MigrationItem.REPOSITORIES));
    return form;
  }
}
