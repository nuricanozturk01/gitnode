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
package com.nuricanozturk.originhub.migration.service;

import com.nuricanozturk.originhub.migration.dtos.MigrationService;
import com.nuricanozturk.originhub.migration.dtos.MigrationStatus;
import com.nuricanozturk.originhub.migration.entities.MigrationJob;
import com.nuricanozturk.originhub.migration.repositories.MigrationJobRepository;
import com.nuricanozturk.originhub.shared.pr.events.GithubPullRequestMigrationRequestedEvent;
import com.nuricanozturk.originhub.shared.repo.services.RepoMigrationService;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class GitHubMigrationService implements CloudMigrationService {

  private final MigrationJobRepository jobRepository;
  private final RepoMigrationService repoMigrationService;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public void process(final MigrationJob job, final String accessToken, final Tenant tenant) {

    this.updateStatus(job, MigrationStatus.IN_PROGRESS, null);

    try {
      for (final var item : job.getMigrationItems()) {
        switch (item) {
          case REPOSITORIES -> this.migrateRepository(job, accessToken, tenant);
          case PULL_REQUESTS -> this.migratePullRequests(job, accessToken, tenant);
          default -> throw new UnsupportedOperationException("unsupportedItem");
        }
      }

      this.updateStatus(job, MigrationStatus.COMPLETED, null);
    } catch (final Exception e) {
      this.updateStatus(job, MigrationStatus.FAILED, e.getMessage());
    }
  }

  private void migrateRepository(
      final MigrationJob job, final String accessToken, final Tenant tenant) throws IOException {

    if (job.getService() == MigrationService.GITHUB) {
      this.repoMigrationService.migrateFromGithub(
          job.getRepoName(), job.getOwner(), tenant, accessToken);
    }
  }

  private void migratePullRequests(
      final MigrationJob job, final String token, final Tenant tenant) {

    final var event =
        GithubPullRequestMigrationRequestedEvent.builder()
            .accessToken(token)
            .remoteRepoOwner(job.getOwner())
            .remoteRepoName(job.getRepoName())
            .tenantId(job.getRequesterId())
            .tenantUsername(tenant.getUsername())
            .build();

    this.eventPublisher.publishEvent(event);
  }

  private void updateStatus(
      final MigrationJob job, final MigrationStatus status, @Nullable final String error) {

    job.setStatus(status);
    job.setErrorMessage(error);

    if (status == MigrationStatus.COMPLETED || status == MigrationStatus.FAILED) {
      job.setCompletedAt(Instant.now());
    }

    this.jobRepository.save(job);
  }
}
