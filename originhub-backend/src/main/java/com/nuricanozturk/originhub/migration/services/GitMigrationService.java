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

import com.nuricanozturk.originhub.migration.dtos.MigrationForm;
import com.nuricanozturk.originhub.migration.dtos.MigrationStatus;
import com.nuricanozturk.originhub.migration.entities.MigrationJob;
import com.nuricanozturk.originhub.migration.repositories.MigrationJobRepository;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@NullMarked
public class GitMigrationService {

  private final MigrationJobRepository jobRepository;
  private final CloudMigrationService cloudMigrationService;
  private final TenantRepository tenantRepository;

  @Async
  public void migrate(final MigrationForm form, final UUID tenantId, final UUID jobId) {

    final var tenant =
        this.tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new ItemNotFoundException("Tenant not found"));

    final var job =
        MigrationJob.builder()
            .id(jobId)
            .service(form.getService())
            .status(MigrationStatus.PENDING)
            .repoUrl(form.getUrl())
            .owner(form.getOwner())
            .repoName(form.getRepoName())
            .requesterId(tenantId)
            .migrationItems(form.getMigrationItems())
            .build();

    this.jobRepository.save(job);

    try {
      this.cloudMigrationService.process(job, form.getAccessToken(), tenant);
    } catch (final Exception ex) {
      job.setStatus(MigrationStatus.FAILED);
      this.jobRepository.save(job);
      throw ex;
    }
  }

  public Optional<MigrationJob> findJob(final UUID jobId) {

    return this.jobRepository.findByJobId(jobId);
  }
}
