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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.events.pr.GithubPullRequestMigrationRequestedEvent;
import com.nuricanozturk.originhub.events.tag.GithubTagReleaseMigrationRequestedEvent;
import com.nuricanozturk.originhub.migration.dtos.MigrationItem;
import com.nuricanozturk.originhub.migration.dtos.MigrationServiceProvider;
import com.nuricanozturk.originhub.migration.dtos.MigrationStatus;
import com.nuricanozturk.originhub.migration.entities.MigrationJob;
import com.nuricanozturk.originhub.migration.repositories.MigrationJobRepository;
import com.nuricanozturk.originhub.shared.repo.services.RepoMigrationService;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("GitHubMigrationService unit tests")
class GitHubMigrationServiceTest {

  @Mock private MigrationJobRepository jobRepository;
  @Mock private RepoMigrationService repoMigrationService;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private GitHubMigrationService gitHubMigrationService;

  @Test
  @DisplayName("process completes job when repository migration succeeds")
  void process_marksCompleted_whenRepositoryMigrationSucceeds() throws IOException {
    UUID requesterId = UUID.randomUUID();
    MigrationJob job = baseJob(requesterId, List.of(MigrationItem.REPOSITORIES));
    Tenant tenant = tenant(requesterId, "alice");
    when(jobRepository.save(any(MigrationJob.class))).thenAnswer(inv -> inv.getArgument(0));

    gitHubMigrationService.process(job, "gh-token", tenant);

    assertThat(job.getStatus()).isEqualTo(MigrationStatus.COMPLETED);
    assertThat(job.getCompletedAt()).isNotNull();
    verify(repoMigrationService).migrateFromGithub("demo", "acme", tenant, "gh-token");
  }

  @Test
  @DisplayName("process publishes PR migration event for PULL_REQUESTS item")
  void process_publishesPrMigrationEvent_whenPullRequestsRequested() {
    UUID requesterId = UUID.randomUUID();
    MigrationJob job = baseJob(requesterId, List.of(MigrationItem.PULL_REQUESTS));
    Tenant tenant = tenant(requesterId, "alice");
    when(jobRepository.save(any(MigrationJob.class))).thenAnswer(inv -> inv.getArgument(0));

    gitHubMigrationService.process(job, "gh-token", tenant);

    ArgumentCaptor<GithubPullRequestMigrationRequestedEvent> captor =
        ArgumentCaptor.forClass(GithubPullRequestMigrationRequestedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().getRemoteRepoOwner()).isEqualTo("acme");
    assertThat(captor.getValue().getRemoteRepoName()).isEqualTo("demo");
    assertThat(job.getStatus()).isEqualTo(MigrationStatus.COMPLETED);
  }

  @Test
  @DisplayName("process publishes tag/release migration event for TAGS_AND_RELEASES item")
  void process_publishesTagReleaseEvent_whenTagsRequested() {
    UUID requesterId = UUID.randomUUID();
    MigrationJob job = baseJob(requesterId, List.of(MigrationItem.TAGS_AND_RELEASES));
    Tenant tenant = tenant(requesterId, "alice");
    when(jobRepository.save(any(MigrationJob.class))).thenAnswer(inv -> inv.getArgument(0));

    gitHubMigrationService.process(job, "gh-token", tenant);

    verify(eventPublisher).publishEvent(any(GithubTagReleaseMigrationRequestedEvent.class));
    assertThat(job.getStatus()).isEqualTo(MigrationStatus.COMPLETED);
  }

  @Test
  @DisplayName("process marks job failed when repository migration throws")
  void process_marksFailed_whenRepositoryMigrationFails() throws IOException {
    UUID requesterId = UUID.randomUUID();
    MigrationJob job = baseJob(requesterId, List.of(MigrationItem.REPOSITORIES));
    Tenant tenant = tenant(requesterId, "alice");
    when(jobRepository.save(any(MigrationJob.class))).thenAnswer(inv -> inv.getArgument(0));
    doThrow(new IOException("network"))
        .when(repoMigrationService)
        .migrateFromGithub("demo", "acme", tenant, "gh-token");

    gitHubMigrationService.process(job, "gh-token", tenant);

    assertThat(job.getStatus()).isEqualTo(MigrationStatus.FAILED);
    assertThat(job.getErrorMessage()).contains("network");
    assertThat(job.getCompletedAt()).isNotNull();
  }

  private static MigrationJob baseJob(UUID requesterId, List<MigrationItem> items) {
    return MigrationJob.builder()
        .id(UUID.randomUUID())
        .service(MigrationServiceProvider.GITHUB)
        .status(MigrationStatus.PENDING)
        .owner("acme")
        .repoName("demo")
        .requesterId(requesterId)
        .migrationItems(items)
        .build();
  }

  private static Tenant tenant(UUID id, String username) {
    Tenant tenant = new Tenant();
    tenant.setId(id);
    tenant.setUsername(username);
    return tenant;
  }
}
