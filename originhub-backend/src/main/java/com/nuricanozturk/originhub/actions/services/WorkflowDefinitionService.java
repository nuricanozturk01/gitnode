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

import com.nuricanozturk.originhub.actions.ActionsEnumNames;
import com.nuricanozturk.originhub.actions.dtos.response.DispatchInputResponse;
import com.nuricanozturk.originhub.actions.dtos.response.WorkflowDetailResponse;
import com.nuricanozturk.originhub.actions.dtos.response.WorkflowSummaryResponse;
import com.nuricanozturk.originhub.actions.entities.WorkflowDefinition;
import com.nuricanozturk.originhub.actions.entities.WorkflowRun;
import com.nuricanozturk.originhub.actions.entities.WorkflowRunStatus;
import com.nuricanozturk.originhub.actions.model.WorkflowModel;
import com.nuricanozturk.originhub.actions.repositories.WorkflowDefinitionRepository;
import com.nuricanozturk.originhub.actions.repositories.WorkflowRunRepository;
import com.nuricanozturk.originhub.actions.services.WorkflowParserService.ParsedWorkflow;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NullMarked
public class WorkflowDefinitionService {

  private final WorkflowParserService parserService;
  private final WorkflowDefinitionRepository definitionRepository;
  private final WorkflowRunRepository runRepository;
  private final RepoRepository repoRepository;
  private final JobDispatcher jobDispatcher;

  public List<WorkflowSummaryResponse> listForRepo(final UUID repoId) {

    final var repo =
        this.repoRepository
            .findByIdWithOwner(repoId)
            .orElseThrow(() -> new ItemNotFoundException("Repository not found: " + repoId));

    final var ownerUsername = repo.getOwner().getUsername();
    final var repoName = repo.getName();
    final var branchRef = "refs/heads/" + repo.getDefaultBranch();

    final var parsed = this.parserService.parseWorkflows(ownerUsername, repoName, branchRef);
    final Map<String, WorkflowDefinition> dbByPath =
        this.definitionRepository.findAllByRepoIdOrderByFilePathAsc(repoId).stream()
            .collect(Collectors.toMap(WorkflowDefinition::getFilePath, Function.identity()));

    final var defIds = dbByPath.values().stream().map(WorkflowDefinition::getId).toList();
    final Map<UUID, WorkflowRun> latestRunByDefId =
        defIds.isEmpty()
            ? Map.of()
            : this.runRepository.findLatestRunPerDef(repoId, defIds).stream()
                .collect(Collectors.toMap(WorkflowRun::getWorkflowDefId, Function.identity()));

    return parsed.stream()
        .map(pw -> this.toSummaryResponse(pw, dbByPath.get(pw.filePath()), latestRunByDefId))
        .toList();
  }

  private static String resolveWorkflowName(final ParsedWorkflow pw) {
    return pw.model().name() != null && !pw.model().name().isBlank()
        ? pw.model().name()
        : fileNameWithoutExt(pw.filePath());
  }

  private static boolean isDispatchable(final ParsedWorkflow pw) {
    return pw.model().on() != null && pw.model().on().workflowDispatch() != null;
  }

  private WorkflowSummaryResponse toSummaryResponse(
      final ParsedWorkflow pw,
      final @Nullable WorkflowDefinition def,
      final Map<UUID, WorkflowRun> latestRunByDefId) {

    final var name = resolveWorkflowName(pw);
    final boolean dispatchable = isDispatchable(pw);
    final boolean enabled = def == null || def.isEnabled();
    final String lastRunStatus =
        def == null ? null : resolveLastRunStatusFromRun(latestRunByDefId.get(def.getId()));

    return new WorkflowSummaryResponse(
        def != null ? def.getId() : null,
        name,
        pw.filePath(),
        enabled,
        dispatchable,
        lastRunStatus,
        def != null ? def.getCreatedAt() : null,
        def != null ? def.getUpdatedAt() : null);
  }

  public WorkflowDetailResponse getDetail(final UUID repoId, final String filePath) {

    final var repo =
        this.repoRepository
            .findByIdWithOwner(repoId)
            .orElseThrow(() -> new ItemNotFoundException("Repository not found: " + repoId));

    final var ownerUsername = repo.getOwner().getUsername();
    final var repoName = repo.getName();
    final var branchRef = "refs/heads/" + repo.getDefaultBranch();

    final var parsed =
        this.parserService.parseWorkflows(ownerUsername, repoName, branchRef).stream()
            .filter(p -> p.filePath().equals(filePath))
            .findFirst()
            .orElseThrow(
                () -> new ItemNotFoundException("Workflow not found at path: " + filePath));

    final var def =
        this.definitionRepository.findByRepoIdAndFilePath(repoId, filePath).orElse(null);
    final var name = resolveWorkflowName(parsed);
    final boolean dispatchable = isDispatchable(parsed);
    final boolean enabled = def == null || def.isEnabled();
    final Optional<WorkflowRun> latestRun =
        def == null
            ? Optional.empty()
            : this.runRepository.findFirstByRepoIdAndWorkflowDefIdOrderByCreatedAtDesc(
                repoId, def.getId());
    final String lastRunStatus = resolveLastRunStatusFromRun(latestRun.orElse(null));

    return new WorkflowDetailResponse(
        def != null ? def.getId() : null,
        name,
        parsed.filePath(),
        parsed.rawContent(),
        repo.getDefaultBranch(),
        enabled,
        dispatchable,
        lastRunStatus,
        resolveDispatchInputs(parsed.model()));
  }

  private static @Nullable List<DispatchInputResponse> resolveDispatchInputs(
      final WorkflowModel model) {

    if (model.on() == null || model.on().workflowDispatch() == null) {
      return null;
    }

    final var inputs = model.on().workflowDispatch().inputs();
    if (inputs == null || inputs.isEmpty()) {
      return List.of();
    }

    return inputs.entrySet().stream()
        .map(
            e ->
                new DispatchInputResponse(
                    e.getKey(),
                    e.getValue().description(),
                    e.getValue().type(),
                    e.getValue().defaultValue(),
                    e.getValue().options(),
                    e.getValue().required()))
        .toList();
  }

  @Transactional
  public void setEnabled(final UUID repoId, final String filePath, final boolean enabled) {

    final var repo =
        this.repoRepository
            .findByIdWithOwner(repoId)
            .orElseThrow(() -> new ItemNotFoundException("Repository not found: " + repoId));

    final var branchRef = "refs/heads/" + repo.getDefaultBranch();
    final var parsed =
        this.parserService
            .parseWorkflows(repo.getOwner().getUsername(), repo.getName(), branchRef)
            .stream()
            .filter(p -> p.filePath().equals(filePath))
            .findFirst()
            .orElseThrow(
                () -> new ItemNotFoundException("Workflow not found at path: " + filePath));

    final var hash = DigestUtils.sha256Hex(parsed.rawContent());
    final var def =
        this.definitionRepository
            .findByRepoIdAndFilePath(repoId, filePath)
            .orElseGet(
                () -> {
                  final var created = new WorkflowDefinition();
                  created.setRepoId(repoId);
                  created.setFilePath(parsed.filePath());
                  created.setContent(parsed.rawContent());
                  created.setContentHash(hash);
                  created.setName(resolveWorkflowName(parsed));
                  return created;
                });

    def.setEnabled(enabled);
    this.definitionRepository.save(def);
    this.jobDispatcher.invalidateYamlCache(def.getId());
  }

  private static @Nullable String resolveLastRunStatusFromRun(final @Nullable WorkflowRun run) {

    if (run == null) {
      return null;
    }
    if (run.getStatus() == WorkflowRunStatus.IN_PROGRESS
        || run.getStatus() == WorkflowRunStatus.QUEUED) {
      return ActionsEnumNames.lowerCase(run.getStatus());
    }
    return run.getConclusion() != null
        ? run.getConclusion()
        : ActionsEnumNames.lowerCase(run.getStatus());
  }

  private static String fileNameWithoutExt(final String filePath) {

    final var fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
    return fileName.replaceAll("\\.(yml|yaml)$", "");
  }
}
