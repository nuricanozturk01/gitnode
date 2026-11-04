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

package dev.gitnode.os.actions.services;

import dev.gitnode.os.actions.ActionsEnumNames;
import dev.gitnode.os.actions.dtos.response.AnnotationResponse;
import dev.gitnode.os.actions.dtos.response.JobSummaryResponse;
import dev.gitnode.os.actions.dtos.response.WorkflowJobResponse;
import dev.gitnode.os.actions.dtos.response.WorkflowRunResponse;
import dev.gitnode.os.actions.dtos.response.WorkflowStepResponse;
import dev.gitnode.os.actions.entities.WorkflowJob;
import dev.gitnode.os.actions.entities.WorkflowRun;
import dev.gitnode.os.actions.entities.WorkflowRunStatus;
import dev.gitnode.os.actions.entities.WorkflowStep;
import dev.gitnode.os.actions.repositories.WorkflowDefinitionRepository;
import dev.gitnode.os.actions.repositories.WorkflowJobRepository;
import dev.gitnode.os.actions.repositories.WorkflowLogRepository;
import dev.gitnode.os.actions.repositories.WorkflowRunRepository;
import dev.gitnode.os.actions.repositories.WorkflowStepRepository;
import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of workflow runs: what the controllers show, so they never touch repositories. */
@Service
@RequiredArgsConstructor
@NullMarked
@Transactional(readOnly = true)
public class WorkflowRunQueryService {

  private final WorkflowRunRepository runRepository;
  private final WorkflowJobRepository jobRepository;
  private final WorkflowStepRepository stepRepository;
  private final WorkflowLogRepository logRepository;
  private final WorkflowDefinitionRepository definitionRepository;
  private final TenantQueryPort tenantQuery;

  /** Optional filters of the run list; every null field means "any". */
  public record RunFilter(
      @Nullable String workflowPath,
      @Nullable String triggerEvent,
      @Nullable String triggerRef,
      @Nullable String branch,
      @Nullable String status,
      @Nullable String actor,
      @Nullable String query) {}

  public Page<WorkflowRunResponse> listRuns(
      final UUID repoId, final RunFilter filter, final Pageable pageable) {

    final var paging =
        PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            Sort.by(Sort.Direction.DESC, "createdAt"));

    final Specification<WorkflowRun> spec;
    try {
      spec = this.buildSpec(repoId, filter);
    } catch (final NoMatchException e) {
      return new PageImpl<>(List.of(), paging, 0);
    }

    final var page = this.runRepository.findAll(spec, paging);
    final var actors = this.actorsOf(page.getContent());
    final var paths = this.pathsOf(page.getContent());
    return page.map(run -> this.toResponse(run, List.of(), actors, paths));
  }

  /** A filter named something that does not exist, so no run can match it. */
  private static final class NoMatchException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    NoMatchException() {
      super(null, null, false, false);
    }
  }

  private Specification<WorkflowRun> buildSpec(final UUID repoId, final RunFilter filter) {

    final UUID defId =
        hasText(filter.workflowPath()) ? this.requireDefinitionId(repoId, filter) : null;
    final UUID actorId = hasText(filter.actor()) ? this.requireActorId(filter) : null;
    final var status = hasText(filter.status()) ? requireStatus(filter) : null;

    return (root, query, cb) -> {
      final var predicates = new ArrayList<Predicate>();
      predicates.add(cb.equal(root.get("repoId"), repoId));
      addIf(predicates, defId != null, () -> cb.equal(root.get("workflowDefId"), defId));
      addIf(predicates, actorId != null, () -> cb.equal(root.get("triggerActor"), actorId));
      addIf(predicates, status != null, () -> cb.equal(root.get("status"), status));
      addIf(
          predicates,
          hasText(filter.triggerEvent()),
          () -> cb.equal(root.get("triggerEvent"), filter.triggerEvent().strip()));
      addIf(
          predicates,
          hasText(filter.triggerRef()),
          () -> cb.equal(root.get("triggerRef"), filter.triggerRef().strip()));
      addIf(
          predicates,
          hasText(filter.branch()),
          () ->
              root.get("triggerRef")
                  .in(filter.branch().strip(), "refs/heads/" + filter.branch().strip()));
      addIf(
          predicates,
          hasText(filter.query()),
          () -> {
            final var like =
                "%" + escapeLike(filter.query().strip().toLowerCase(Locale.ROOT)) + "%";
            return cb.or(
                cb.like(cb.lower(root.get("workflowName")), like, '\\'),
                cb.like(
                    cb.lower(cb.coalesce(root.<String>get("headCommitMessage"), "")), like, '\\'));
          });
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private static void addIf(
      final List<Predicate> predicates,
      final boolean condition,
      final java.util.function.Supplier<Predicate> predicate) {
    if (condition) {
      predicates.add(predicate.get());
    }
  }

  private UUID requireDefinitionId(final UUID repoId, final RunFilter filter) {
    return this.definitionRepository
        .findByRepoIdAndFilePath(repoId, filter.workflowPath().strip())
        .map(def -> def.getId())
        .orElseThrow(NoMatchException::new);
  }

  private UUID requireActorId(final RunFilter filter) {
    return this.tenantQuery
        .findByUsernameIgnoreCase(filter.actor().strip())
        .map(TenantRef::id)
        .orElseThrow(NoMatchException::new);
  }

  private static WorkflowRunStatus requireStatus(final RunFilter filter) {
    final var parsed = parseStatus(filter.status());
    if (parsed == null) {
      throw new NoMatchException();
    }
    return parsed;
  }

  private static boolean hasText(final @Nullable String value) {
    return value != null && !value.isBlank();
  }

  /** Accepts the stored names such as in_progress; anything else matches nothing. */
  private static @Nullable WorkflowRunStatus parseStatus(final String raw) {
    try {
      return WorkflowRunStatus.valueOf(raw.strip().toUpperCase(Locale.ROOT).replace('-', '_'));
    } catch (final IllegalArgumentException e) {
      return null;
    }
  }

  private Map<UUID, TenantRef> actorsOf(final List<WorkflowRun> runs) {
    final var ids = new HashSet<UUID>();
    runs.stream().map(WorkflowRun::getTriggerActor).filter(Objects::nonNull).forEach(ids::add);
    return ids.isEmpty() ? Map.of() : this.tenantQuery.findAllByIds(ids);
  }

  private Map<UUID, String> pathsOf(final List<WorkflowRun> runs) {
    final var ids = new HashSet<UUID>();
    runs.stream().map(WorkflowRun::getWorkflowDefId).filter(Objects::nonNull).forEach(ids::add);
    final var paths = new HashMap<UUID, String>();
    if (!ids.isEmpty()) {
      this.definitionRepository
          .findAllById(ids)
          .forEach(def -> paths.put(def.getId(), def.getFilePath()));
    }
    return paths;
  }

  private static String escapeLike(final String raw) {
    return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  public WorkflowRunResponse getRun(final UUID runId) {

    final var run = this.requireRun(runId);
    final var jobs =
        this.jobRepository.findAllByRunIdOrderByCreatedAtAsc(runId).stream()
            .map(job -> this.toJobResponse(job, true))
            .toList();

    return this.toResponse(run, jobs, this.actorsOf(List.of(run)), this.pathsOf(List.of(run)));
  }

  /** Upper bound of annotations returned for one run, like GitHub's cap. */
  public static final int MAX_ANNOTATIONS = 50;

  /**
   * The ::error / ::warning / ::notice annotations of a run, first MAX_ANNOTATIONS in log order.
   */
  public List<AnnotationResponse> annotations(final UUID runId) {

    this.requireRun(runId);
    return this.logRepository.findAnnotations(runId, PageRequest.of(0, MAX_ANNOTATIONS)).stream()
        .map(
            row ->
                new AnnotationResponse(
                    annotationLevel(row.content()),
                    stripAnnotationPrefix(row.content()),
                    row.jobId(),
                    row.jobName(),
                    row.stepId(),
                    row.stepNumber(),
                    row.lineNumber()))
        .toList();
  }

  /** The job summaries of a run: the markdown each job's steps wrote, one entry per job. */
  public List<JobSummaryResponse> summaries(final UUID runId) {

    this.requireRun(runId);
    final var byJob = new java.util.LinkedHashMap<UUID, StringBuilder>();
    final var names = new java.util.HashMap<UUID, String>();
    for (final var row : this.logRepository.findSummaryLines(runId)) {
      names.putIfAbsent(row.jobId(), row.jobName());
      byJob
          .computeIfAbsent(row.jobId(), id -> new StringBuilder())
          .append(row.content())
          .append('\n');
    }
    return byJob.entrySet().stream()
        .map(
            e -> new JobSummaryResponse(e.getKey(), names.get(e.getKey()), e.getValue().toString()))
        .toList();
  }

  private static String annotationLevel(final String content) {
    if (content.startsWith("##[warning]")) {
      return "warning";
    }
    return content.startsWith("##[notice]") ? "notice" : "error";
  }

  private static String stripAnnotationPrefix(final String content) {
    return content.substring(content.indexOf(']') + 1);
  }

  public WorkflowRunStatus runStatus(final UUID runId) {

    return this.requireRun(runId).getStatus();
  }

  /** Log lines already stored for a step, oldest first. */
  public List<WorkflowExecutionService.LogLine> logLines(final UUID stepId) {

    return this.logRepository.findAllByStepIdOrderByLineNumberAsc(stepId).stream()
        .map(
            log ->
                new WorkflowExecutionService.LogLine(
                    log.getLineNumber(), log.getContent(), log.getLevel(), log.getCreatedAt()))
        .toList();
  }

  public boolean isStepCompleted(final UUID stepId) {

    return this.stepRepository
        .findById(stepId)
        .map(step -> "completed".equals(step.getStatus()))
        .orElse(false);
  }

  private WorkflowRun requireRun(final UUID runId) {

    return this.runRepository
        .findById(runId)
        .orElseThrow(() -> new ItemNotFoundException("Workflow run not found: " + runId));
  }

  private WorkflowRunResponse toResponse(
      final WorkflowRun run,
      final List<WorkflowJobResponse> jobs,
      final Map<UUID, TenantRef> actors,
      final Map<UUID, String> paths) {

    final var actor = run.getTriggerActor() == null ? null : actors.get(run.getTriggerActor());

    return new WorkflowRunResponse(
        run.getId(),
        run.getRunNumber(),
        run.getWorkflowName(),
        ActionsEnumNames.lowerCase(run.getStatus()),
        run.getConclusion(),
        run.getTriggerEvent(),
        run.getTriggerRef(),
        run.getTriggerSha(),
        run.getStartedAt(),
        run.getCompletedAt(),
        run.getCreatedAt(),
        run.getHeadCommitMessage(),
        actor == null ? null : actor.username(),
        actor == null ? null : actor.avatarUrl(),
        run.getRunAttempt(),
        run.getWorkflowDefId() == null ? null : paths.get(run.getWorkflowDefId()),
        jobs);
  }

  private WorkflowJobResponse toJobResponse(final WorkflowJob job, final boolean includeSteps) {

    final var steps =
        includeSteps
            ? this.stepRepository.findAllByJobIdOrderByStepNumberAsc(job.getId()).stream()
                .map(this::toStepResponse)
                .toList()
            : List.<WorkflowStepResponse>of();

    return new WorkflowJobResponse(
        job.getId(),
        job.getName(),
        ActionsEnumNames.lowerCase(job.getStatus()),
        job.getConclusion(),
        job.getRunnerLabels(),
        job.getNeeds(),
        job.getMatrixValues(),
        job.getStartedAt(),
        job.getCompletedAt(),
        job.getCreatedAt(),
        steps);
  }

  private WorkflowStepResponse toStepResponse(final WorkflowStep step) {

    return new WorkflowStepResponse(
        step.getId(),
        step.getStepNumber(),
        step.getName(),
        step.getUses(),
        step.getStatus(),
        step.getConclusion(),
        step.getStartedAt(),
        step.getCompletedAt());
  }
}
