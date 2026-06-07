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
package com.nuricanozturk.originhub.actions.services;

import com.nuricanozturk.originhub.actions.api.ActionsAdminPort;
import com.nuricanozturk.originhub.actions.api.RunnerAdminData;
import com.nuricanozturk.originhub.actions.api.WorkflowRunStatsData;
import com.nuricanozturk.originhub.actions.entities.RunnerStatus;
import com.nuricanozturk.originhub.actions.entities.WorkflowRunStatus;
import com.nuricanozturk.originhub.actions.repositories.RunnerRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowRunRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NullMarked
@Transactional(readOnly = true)
public class ActionsAdminService implements ActionsAdminPort {

  private final RunnerRepository runnerRepository;
  private final WorkflowRunRepository workflowRunRepository;

  @Override
  public List<RunnerAdminData> listAllRunners() {
    return this.runnerRepository.findAll().stream()
        .map(
            r ->
                new RunnerAdminData(
                    r.getId(),
                    r.getRepoId(),
                    r.getName(),
                    r.getLabels(),
                    r.getStatus().name(),
                    r.getOs(),
                    r.getArch(),
                    r.getVersion(),
                    r.getLastHeartbeat(),
                    r.getCreatedAt()))
        .toList();
  }

  @Override
  public WorkflowRunStatsData getRunStats() {
    final var allRuns = this.workflowRunRepository.findAll();
    final long total = allRuns.size();
    final long success =
        allRuns.stream().filter(r -> r.getStatus() == WorkflowRunStatus.SUCCESS).count();
    final long failure =
        allRuns.stream().filter(r -> r.getStatus() == WorkflowRunStatus.FAILURE).count();
    final long cancelled =
        allRuns.stream().filter(r -> r.getStatus() == WorkflowRunStatus.CANCELLED).count();
    final long inProgress =
        allRuns.stream().filter(r -> r.getStatus() == WorkflowRunStatus.IN_PROGRESS).count();

    final var allRunners = this.runnerRepository.findAll();
    final long totalRunners = allRunners.size();
    final long onlineRunners =
        allRunners.stream().filter(r -> r.getStatus() == RunnerStatus.ONLINE).count();
    final long busyRunners =
        allRunners.stream().filter(r -> r.getStatus() == RunnerStatus.BUSY).count();

    return new WorkflowRunStatsData(
        total, success, failure, cancelled, inProgress, totalRunners, onlineRunners, busyRunners);
  }
}
