import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { paramMapSignal, parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { WorkflowRunService } from '../../../core/actions/services/workflow-run.service';
import { RunEventStreamService } from '../../../core/actions/services/run-event-stream.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { ToastService } from '../../../core/toast/toast.service';
import type { WorkflowRun } from '../../../domain/actions/models/workflow-run.model';
import type { WorkflowJob } from '../../../domain/actions/models/workflow-job.model';
import {
  isRunActive,
  resolveWorkflowDisplayStatus,
  workflowStatusBadgeClass,
  workflowStatusIconClass,
  workflowStatusIconName,
  workflowStatusIconSpinning,
  workflowStatusLabel,
} from '../../../shared/utils/workflow-status.utils';
import { JobGraphComponent } from './components/job-graph/job-graph.component';
import { JobPanelComponent } from './components/job-panel/job-panel.component';
import { LogViewerComponent } from './components/log-viewer/log-viewer.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-run-detail',
  standalone: true,
  imports: [
    RouterLink,
    LucideAngularModule,
    RelativeTimePipe,
    JobGraphComponent,
    JobPanelComponent,
    LogViewerComponent,
  ],
  templateUrl: './run-detail.page.html',
})
export class RunDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly runService = inject(WorkflowRunService);
  private readonly eventStreamService = inject(RunEventStreamService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly toast = inject(ToastService);
  readonly repoContext = inject(RepoContextService);

  readonly run = signal<WorkflowRun | null>(null);
  readonly loading = signal(true);
  readonly cancelling = signal(false);
  readonly selectedJobId = signal<string | null>(null);
  readonly selectedStepId = signal<string | null>(null);
  readonly expandedJobIds = signal<Set<string>>(new Set());

  // owner/repo come from the PARENT route (/:owner/:repo)
  private readonly parentParams = parentParamMapSignal(this.route);
  // runId comes from THIS route (actions/runs/:runId)
  private readonly currentParams = paramMapSignal(this.route);

  readonly owner = computed(() => this.parentParams().get('owner') ?? '');
  readonly repoName = computed(() => this.parentParams().get('repo') ?? '');
  readonly runId = computed(() => this.currentParams().get('runId') ?? '');

  readonly selectedJob = computed(() => {
    const id = this.selectedJobId();
    return this.run()?.jobs.find((j) => j.id === id) ?? null;
  });

  readonly selectedStep = computed(() => {
    const stepId = this.selectedStepId();
    const job = this.selectedJob();
    if (!stepId || !job) return null;
    return job.steps.find((s) => s.id === stepId) ?? null;
  });

  readonly isLive = computed(() => {
    const run = this.run();
    return run != null && isRunActive(run.status);
  });

  readonly isStepLive = computed(() => {
    const step = this.selectedStep();
    if (!step) return false;
    if (!this.isLive()) return false;
    return !step.status || step.status === 'running';
  });

  statusBadgeClass = workflowStatusBadgeClass;
  statusLabel = workflowStatusLabel;
  statusIconClass = workflowStatusIconClass;
  statusIconName = workflowStatusIconName;
  statusIconSpinning = workflowStatusIconSpinning;

  private sseAbortController: AbortController | null = null;
  private pollTimer: ReturnType<typeof setInterval> | null = null;
  private refreshDebounceTimer: ReturnType<typeof setTimeout> | null = null;
  private refreshAbortController: AbortController | null = null;

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe(() => void this.loadRun());
    this.destroyRef.onDestroy(() => {
      this.stopStreaming();
      this.stopPolling();
      this.cancelRefreshDebounce();
    });
  }

  displayStatus(run: WorkflowRun): string {
    return resolveWorkflowDisplayStatus(run.status, run.conclusion);
  }

  async cancelRun(): Promise<void> {
    const run = this.run();
    if (!run || !isRunActive(run.status)) return;
    this.cancelling.set(true);
    try {
      await this.runService.cancelRun(this.owner(), this.repoName(), run.id);
      this.toast.success('Run cancelled');
      await this.refreshRun();
    } catch {
      this.toast.error('Could not cancel run');
    } finally {
      this.cancelling.set(false);
    }
  }

  selectJob(jobId: string): void {
    this.expandedJobIds.update((ids) => {
      const next = new Set(ids);
      if (next.has(jobId)) {
        next.delete(jobId);
      } else {
        next.add(jobId);
      }
      return next;
    });
    this.selectedJobId.set(jobId);
    const job = this.run()?.jobs.find((j) => j.id === jobId);
    const firstStep = job?.steps[0];
    this.selectedStepId.set(firstStep?.id ?? null);
  }

  selectStep(stepId: string): void {
    this.selectedStepId.set(stepId);
  }

  stepName(step: { name: string | null; uses: string | null; stepNumber: number }): string {
    return step.name ?? step.uses ?? `Step ${step.stepNumber}`;
  }

  private async loadRun(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const runId = this.runId();
    if (!owner || !repo || !runId) return;

    this.loading.set(true);
    try {
      const run = await this.runService.getRun(owner, repo, runId);
      this.run.set(run);
      this.ensureSelection(run.jobs);
      this.stopStreaming();
      this.syncStreaming(run);
    } catch {
      this.run.set(null);
    } finally {
      this.loading.set(false);
    }
  }

  private ensureSelection(jobs: WorkflowJob[]): void {
    if (jobs.length === 0) {
      this.selectedJobId.set(null);
      this.selectedStepId.set(null);
      return;
    }

    const currentJob = this.selectedJobId();
    if (!currentJob || !jobs.some((j) => j.id === currentJob)) {
      const firstJob = jobs[0];
      this.selectedJobId.set(firstJob.id);
      this.selectedStepId.set(firstJob.steps[0]?.id ?? null);
      this.expandedJobIds.update((ids) => {
        const next = new Set(ids);
        next.add(firstJob.id);
        return next;
      });
    }
  }

  private syncStreaming(run: WorkflowRun): void {
    if (isRunActive(run.status)) {
      this.startStreaming();
    } else {
      this.stopStreaming();
      this.stopPolling();
    }
  }

  private startStreaming(): void {
    if (this.sseAbortController) return;
    const owner = this.owner();
    const repo = this.repoName();
    const runId = this.runId();
    if (!owner || !repo || !runId) return;

    this.sseAbortController = new AbortController();
    const ctrl = this.sseAbortController;

    const fallbackToPolling = () => {
      if (!ctrl.signal.aborted) {
        this.stopStreaming();
        this.startPolling();
      }
    };

    void this.eventStreamService
      .streamRunEvents(
        owner,
        repo,
        runId,
        () => this.scheduleRefresh(),
        () => this.stopStreaming(),
        ctrl.signal,
      )
      .then(fallbackToPolling)
      .catch(fallbackToPolling);
  }

  private stopStreaming(): void {
    this.sseAbortController?.abort();
    this.sseAbortController = null;
  }

  private startPolling(): void {
    if (this.pollTimer) return;
    this.pollTimer = setInterval(() => void this.refreshRun(), 3000);
  }

  private stopPolling(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }

  async refreshRun(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const runId = this.runId();
    if (!owner || !repo || !runId) return;

    this.refreshAbortController?.abort();
    this.refreshAbortController = new AbortController();
    const ctrl = this.refreshAbortController;

    try {
      const run = await this.runService.getRun(owner, repo, runId);
      if (ctrl.signal.aborted) return;
      this.run.set(run);
      this.ensureSelectionIfNeeded(run.jobs);
      if (!isRunActive(run.status)) {
        this.stopStreaming();
        this.stopPolling();
      }
    } catch {
      // keep last known state
    }
  }

  private scheduleRefresh(): void {
    if (this.refreshDebounceTimer) return;
    this.refreshDebounceTimer = setTimeout(() => {
      this.refreshDebounceTimer = null;
      void this.refreshRun();
    }, 200);
  }

  private cancelRefreshDebounce(): void {
    if (this.refreshDebounceTimer) {
      clearTimeout(this.refreshDebounceTimer);
      this.refreshDebounceTimer = null;
    }
    this.refreshAbortController?.abort();
    this.refreshAbortController = null;
  }

  private ensureSelectionIfNeeded(jobs: WorkflowJob[]): void {
    const currentStepId = this.selectedStepId();
    if (!currentStepId) {
      this.ensureSelection(jobs);
      return;
    }
    const stepExists = jobs.some((j) => j.steps.some((s) => s.id === currentStepId));
    if (!stepExists) {
      this.ensureSelection(jobs);
    }
  }
}
