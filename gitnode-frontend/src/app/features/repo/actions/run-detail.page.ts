import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  SecurityContext,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { marked } from 'marked';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { paramMapSignal, parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { WorkflowRunService } from '../../../core/actions/services/workflow-run.service';
import { LogStreamService } from '../../../core/actions/services/log-stream.service';
import { RunEventStreamService } from '../../../core/actions/services/run-event-stream.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { ToastService } from '../../../core/toast/toast.service';
import type { WorkflowRun } from '../../../domain/actions/models/workflow-run.model';
import type { WorkflowJob } from '../../../domain/actions/models/workflow-job.model';
import type { WorkflowStep } from '../../../domain/actions/models/workflow-step.model';
import type { RunAnnotation } from '../../../domain/actions/models/run-extras.model';
import {
  commitTitle,
  formatDuration,
  runTitle,
  isRunActive,
  shortRef,
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

/** A job's summary, rendered: the markdown the job wrote, as sanitized HTML. */
interface RenderedSummary {
  jobId: string;
  jobName: string;
  html: string;
}

marked.use({ gfm: true, breaks: false });

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
  private readonly router = inject(Router);
  private readonly logService = inject(LogStreamService);
  private readonly sanitizer = inject(DomSanitizer);
  readonly repoContext = inject(RepoContextService);

  readonly run = signal<WorkflowRun | null>(null);
  readonly loading = signal(true);
  readonly cancelling = signal(false);
  /** Ticks every second while the run is active so durations count up between server updates. */
  readonly now = signal(Date.now());
  readonly annotations = signal<RunAnnotation[]>([]);
  readonly summaries = signal<RenderedSummary[]>([]);
  readonly showTimestamps = signal(false);
  readonly rerunning = signal(false);
  readonly downloading = signal(false);
  readonly selectedJobId = signal<string | null>(null);
  readonly logSearch = signal('');
  /** Steps the user opened or closed by hand; everything else follows the step's state. */
  readonly stepOverrides = signal<Record<string, boolean>>({});

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

  readonly isLive = computed(() => {
    const run = this.run();
    return run != null && isRunActive(run.status);
  });

  readonly canRerun = computed(() => {
    const run = this.run();
    return run != null && !isRunActive(run.status);
  });

  readonly hasFailedJobs = computed(
    () =>
      this.run()?.jobs.some((j) => ['failure', 'cancelled'].includes((j.conclusion ?? j.status).toLowerCase())) ??
      false,
  );

  statusBadgeClass = workflowStatusBadgeClass;
  statusLabel = workflowStatusLabel;
  statusIconClass = workflowStatusIconClass;
  statusIconName = workflowStatusIconName;
  statusIconSpinning = workflowStatusIconSpinning;
  commitTitle = commitTitle;
  runTitle = runTitle;
  shortRef = shortRef;
  formatDuration = formatDuration;

  private clockTimer: ReturnType<typeof setInterval> | null = null;
  /** run id + status the annotations and summaries were last loaded for. */
  private extrasFor = '';
  private sseAbortController: AbortController | null = null;
  private pollTimer: ReturnType<typeof setInterval> | null = null;
  private refreshDebounceTimer: ReturnType<typeof setTimeout> | null = null;
  private refreshAbortController: AbortController | null = null;

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe(() => void this.loadRun());
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.selectedJobId.set(params.get('job'));
      this.logSearch.set('');
    });
    this.destroyRef.onDestroy(() => {
      this.stopStreaming();
      this.stopPolling();
      this.stopClock();
      this.cancelRefreshDebounce();
    });
  }

  displayStatus(item: { status: string; conclusion: string | null }): string {
    return resolveWorkflowDisplayStatus(item.status, item.conclusion);
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

  /** Opens a job (or the summary when null) by writing it into the URL. */
  selectJob(jobId: string | null): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { job: jobId },
      queryParamsHandling: 'merge',
    });
  }

  stepName(step: { name: string | null; uses: string | null; stepNumber: number }): string {
    return step.name ?? step.uses ?? `Step ${step.stepNumber}`;
  }

  isStepLive(step: WorkflowStep): boolean {
    return this.isLive() && step.status === 'running';
  }

  isStepOpen(step: WorkflowStep): boolean {
    const override = this.stepOverrides()[step.id];
    if (override !== undefined) return override;
    return step.status === 'running' || step.conclusion === 'failure';
  }

  toggleStep(step: WorkflowStep): void {
    const open = !this.isStepOpen(step);
    this.stepOverrides.update((current) => ({ ...current, [step.id]: open }));
  }

  stepDuration(step: WorkflowStep): string {
    return step.startedAt ? formatDuration(step.startedAt, step.completedAt, this.now()) : '';
  }

  jobDuration(job: WorkflowJob): string {
    return job.startedAt ? formatDuration(job.startedAt, job.completedAt, this.now()) : '';
  }

  toggleTimestamps(): void {
    this.showTimestamps.update((on) => !on);
  }

  onLogSearch(event: Event): void {
    this.logSearch.set((event.target as HTMLInputElement).value);
  }

  runDuration(run: WorkflowRun): string {
    return run.startedAt ? formatDuration(run.startedAt, run.completedAt, this.now()) : '';
  }

  async rerun(options: { failedOnly?: boolean; jobId?: string }): Promise<void> {
    const run = this.run();
    if (!run || !this.canRerun()) return;
    this.rerunning.set(true);
    try {
      await this.runService.rerun(this.owner(), this.repoName(), run.id, options);
      this.toast.success(options.jobId ? 'Re-running job' : 'Re-running jobs');
      this.stepOverrides.set({});
      await this.refreshRun();
      this.syncStreaming(this.run() ?? run);
    } catch {
      this.toast.error('Could not re-run');
    } finally {
      this.rerunning.set(false);
    }
  }

  /** Saves the job's step logs as one text file (finished jobs only). */
  async downloadLogs(job: WorkflowJob): Promise<void> {
    this.downloading.set(true);
    try {
      const parts: string[] = [];
      for (const step of job.steps) {
        const lines = await this.logService.fetchStepLogs(step.id);
        parts.push(`##[group]${this.stepName(step)}`, ...lines.map((l) => l.content), '##[endgroup]');
      }
      const blob = new Blob([parts.join('\n') + '\n'], { type: 'text/plain' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${job.name.replace(/[^\w.-]+/g, '_')}.txt`;
      link.click();
      URL.revokeObjectURL(url);
    } catch {
      this.toast.error('Could not download logs');
    } finally {
      this.downloading.set(false);
    }
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
      this.stopStreaming();
      this.syncStreaming(run);
      void this.loadExtras(run);
    } catch {
      this.run.set(null);
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Annotations and job summaries come from the logs, so they are fetched when a run is opened and
   * once more when it changes status (in particular when it finishes), not on every live update.
   */
  private async loadExtras(run: WorkflowRun): Promise<void> {
    const marker = `${run.id}:${run.runAttempt}:${run.status}`;
    if (marker === this.extrasFor) return;
    this.extrasFor = marker;
    try {
      const [annotations, summaries] = await Promise.all([
        this.runService.getAnnotations(this.owner(), this.repoName(), run.id),
        this.runService.getSummary(this.owner(), this.repoName(), run.id),
      ]);
      if (this.run()?.id !== run.id) return;
      this.annotations.set(annotations);
      this.summaries.set(
        summaries.map((s) => ({
          jobId: s.jobId,
          jobName: s.jobName,
          html: this.sanitizer.sanitize(SecurityContext.HTML, marked.parse(s.markdown, { async: false })) ?? '',
        })),
      );
    } catch {
      // the summary page still works without them
      this.extrasFor = '';
    }
  }

  private syncClock(active: boolean): void {
    if (active) {
      this.clockTimer ??= setInterval(() => this.now.set(Date.now()), 1000);
    } else {
      this.stopClock();
      this.now.set(Date.now());
    }
  }

  private stopClock(): void {
    if (this.clockTimer) {
      clearInterval(this.clockTimer);
      this.clockTimer = null;
    }
  }

  private syncStreaming(run: WorkflowRun): void {
    this.syncClock(isRunActive(run.status));
    if (isRunActive(run.status)) {
      this.startStreaming();
      // Safety net: if a live event is missed, the page still catches up within a few seconds.
      this.startPolling();
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
      this.syncClock(isRunActive(run.status));
      void this.loadExtras(run);
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
}
