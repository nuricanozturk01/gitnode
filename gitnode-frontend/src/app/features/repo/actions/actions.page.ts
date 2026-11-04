import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { WorkflowService } from '../../../core/actions/services/workflow.service';
import { WorkflowRunService, type WorkflowRunFilter } from '../../../core/actions/services/workflow-run.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import { ToastService } from '../../../core/toast/toast.service';
import type { WorkflowSummary } from '../../../domain/actions/models/workflow-summary.model';
import type { WorkflowDetail } from '../../../domain/actions/models/workflow-detail.model';
import type { WorkflowRun } from '../../../domain/actions/models/workflow-run.model';
import {
  commitTitle,
  formatDuration,
  runTitle,
  isRunActive,
  resolveWorkflowDisplayStatus,
  shortRef,
  workflowStatusIconClass,
  workflowStatusIconName,
  workflowStatusIconSpinning,
} from '../../../shared/utils/workflow-status.utils';
import {
  WorkflowDispatchModalComponent,
  type DispatchConfirmedEvent,
} from './components/workflow-dispatch-modal/workflow-dispatch-modal.component';

const PAGE_SIZE = 20;
const WORKFLOW_PAGE_SIZE = 20;

export const RUN_EVENT_OPTIONS = ['push', 'pull_request', 'workflow_dispatch', 'schedule'] as const;
export const RUN_STATUS_OPTIONS = ['queued', 'in_progress', 'success', 'failure', 'cancelled'] as const;

/** Adds the workflows that are not in the list yet, keeping the name order of the sidebar. */
function mergeWorkflows(current: WorkflowSummary[], incoming: WorkflowSummary[]): WorkflowSummary[] {
  const known = new Set(current.map((w) => w.filePath));
  const merged = [...current, ...incoming.filter((w) => !known.has(w.filePath))];
  return merged.sort(
    (a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()) || a.filePath.localeCompare(b.filePath),
  );
}

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-actions',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, RelativeTimePipe, WorkflowDispatchModalComponent],
  templateUrl: './actions.page.html',
})
export class ActionsPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly workflowService = inject(WorkflowService);
  private readonly runService = inject(WorkflowRunService);
  private readonly toast = inject(ToastService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly destroyRef = inject(DestroyRef);
  readonly repoContext = inject(RepoContextService);

  readonly workflows = signal<WorkflowSummary[]>([]);
  /** How many workflows the repository has in total (the sidebar loads them page by page). */
  readonly workflowTotal = signal(0);
  private readonly workflowPage = signal(0);
  readonly loadingMoreWorkflows = signal(false);
  readonly runs = signal<WorkflowRun[]>([]);
  readonly loading = signal(true);
  readonly runsLoading = signal(false);
  readonly runPage = signal(0);
  readonly totalRunPages = signal(0);
  readonly totalRuns = signal(0);
  readonly dispatching = signal<string | null>(null);
  readonly cancelling = signal<string | null>(null);
  readonly deleting = signal<string | null>(null);
  readonly pendingDispatchDetail = signal<WorkflowDetail | null>(null);
  readonly filter = signal<WorkflowRunFilter>({});
  /** Ticks every second while a run is active so durations count up between refreshes. */
  readonly now = signal(Date.now());

  readonly eventOptions = RUN_EVENT_OPTIONS;
  readonly statusOptions = RUN_STATUS_OPTIONS;

  isRunActive = isRunActive;
  commitTitle = commitTitle;
  shortRef = shortRef;
  formatDuration = formatDuration;
  statusIconClass = workflowStatusIconClass;
  statusIconName = workflowStatusIconName;
  statusIconSpinning = workflowStatusIconSpinning;

  private pollTimer: ReturnType<typeof setInterval> | null = null;
  private clockTimer: ReturnType<typeof setInterval> | null = null;

  private readonly routeParams = parentParamMapSignal(this.route);
  readonly owner = computed(() => this.routeParams().get('owner') ?? '');
  readonly repoName = computed(() => this.routeParams().get('repo') ?? '');

  readonly selectedWorkflow = computed(() => {
    const path = this.filter().workflow;
    return path ? (this.workflows().find((w) => w.filePath === path) ?? null) : null;
  });

  readonly remainingWorkflows = computed(() => Math.max(0, this.workflowTotal() - this.workflows().length));

  readonly hasFilters = computed(() => {
    const f = this.filter();
    return !!(f.event || f.status || f.branch || f.actor || f.query);
  });

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.filter.set({
        workflow: params.get('workflow') ?? undefined,
        event: params.get('event') ?? undefined,
        status: params.get('status') ?? undefined,
        branch: params.get('branch') ?? undefined,
        actor: params.get('actor') ?? undefined,
        query: params.get('q') ?? undefined,
      });
      this.runPage.set(Number(params.get('page') ?? '0') || 0);
      void this.loadRuns();
      void this.ensureSelectedWorkflowLoaded();
    });

    this.route.parent?.paramMap.pipe(takeUntilDestroyed()).subscribe(() => void this.loadWorkflows());
    this.destroyRef.onDestroy(() => this.stopPolling());
  }

  workflowLink(workflow: WorkflowSummary | null): Record<string, string | null> {
    return { workflow: workflow ? workflow.filePath : null };
  }

  /** Writes the filter into the URL; the query-param subscription reloads the list. */
  applyFilter(patch: Partial<WorkflowRunFilter>, page = 0): void {
    const next = { ...this.filter(), ...patch };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        workflow: next.workflow || null,
        event: next.event || null,
        status: next.status || null,
        branch: next.branch || null,
        actor: next.actor || null,
        q: next.query || null,
        page: page > 0 ? page : null,
      },
      replaceUrl: true,
    });
  }

  clearFilters(): void {
    this.applyFilter({ event: undefined, status: undefined, branch: undefined, actor: undefined, query: undefined });
  }

  onTextFilter(key: 'branch' | 'actor' | 'query', event: Event): void {
    event.preventDefault();
    const form = event.target as HTMLFormElement;
    const value = (form.elements.namedItem(key) as HTMLInputElement | null)?.value ?? '';
    this.applyFilter({ [key]: value.trim() || undefined });
  }

  onSelectFilter(key: 'event' | 'status', event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.applyFilter({ [key]: value || undefined });
  }

  async loadWorkflows(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) return;
    try {
      const page = await this.workflowService.list(owner, repo, 0, WORKFLOW_PAGE_SIZE);
      this.workflows.set(page.content ?? []);
      this.workflowTotal.set(page.totalElements ?? 0);
      this.workflowPage.set(0);
      await this.ensureSelectedWorkflowLoaded();
    } catch {
      this.workflows.set([]);
      this.workflowTotal.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  /** Appends the next page of workflows to the sidebar. */
  async loadMoreWorkflows(): Promise<void> {
    if (this.loadingMoreWorkflows() || this.remainingWorkflows() === 0) return;
    this.loadingMoreWorkflows.set(true);
    try {
      const next = this.workflowPage() + 1;
      const page = await this.workflowService.list(this.owner(), this.repoName(), next, WORKFLOW_PAGE_SIZE);
      this.workflows.update((current) => mergeWorkflows(current, page.content ?? []));
      this.workflowTotal.set(page.totalElements ?? 0);
      this.workflowPage.set(next);
    } catch {
      this.toast.error('Could not load more workflows');
    } finally {
      this.loadingMoreWorkflows.set(false);
    }
  }

  /** A deep link may point at a workflow on a page that is not loaded yet; fetch just that one. */
  private async ensureSelectedWorkflowLoaded(): Promise<void> {
    const path = this.filter().workflow;
    if (!path || this.workflows().some((w) => w.filePath === path)) return;
    try {
      const page = await this.workflowService.list(this.owner(), this.repoName(), 0, 1, path);
      const found = (page.content ?? []).find((w) => w.filePath === path);
      if (found) this.workflows.update((current) => mergeWorkflows(current, [found]));
    } catch {
      // the header falls back to "All workflows"
    }
  }

  async loadRuns(silent = false): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) return;
    if (!silent) this.runsLoading.set(true);
    try {
      const page = await this.runService.listRuns(owner, repo, this.runPage(), PAGE_SIZE, this.filter());
      this.runs.set(page.content);
      this.totalRunPages.set(page.totalPages);
      this.totalRuns.set(page.totalElements);
      this.syncPolling(page.content);
    } catch {
      this.runs.set([]);
      this.totalRunPages.set(0);
      this.totalRuns.set(0);
    } finally {
      this.runsLoading.set(false);
      this.loading.set(false);
    }
  }

  async toggleEnabled(workflow: WorkflowSummary): Promise<void> {
    try {
      if (workflow.enabled) {
        await this.workflowService.disable(this.owner(), this.repoName(), workflow.filePath);
      } else {
        await this.workflowService.enable(this.owner(), this.repoName(), workflow.filePath);
      }
      await this.loadWorkflows();
      this.toast.success(workflow.enabled ? 'Workflow disabled' : 'Workflow enabled');
    } catch {
      this.toast.error('Could not update workflow');
    }
  }

  async dispatchWorkflow(workflow: WorkflowSummary): Promise<void> {
    this.dispatching.set(workflow.filePath);
    try {
      const detail = await this.workflowService.getDetail(this.owner(), this.repoName(), workflow.filePath);
      this.pendingDispatchDetail.set(detail);
    } catch {
      this.toast.error('Could not load workflow details');
      this.dispatching.set(null);
    }
  }

  closeDispatchModal(): void {
    this.pendingDispatchDetail.set(null);
    this.dispatching.set(null);
  }

  async onDispatchConfirmed(event: DispatchConfirmedEvent): Promise<void> {
    const detail = this.pendingDispatchDetail();
    this.pendingDispatchDetail.set(null);
    if (!detail) return;
    try {
      await this.workflowService.dispatch(
        this.owner(),
        this.repoName(),
        detail.filePath,
        event.ref,
        Object.keys(event.inputs).length > 0 ? event.inputs : undefined,
      );
      this.toast.success('Workflow dispatched');
      this.applyFilter({ workflow: detail.filePath });
      await this.loadRuns();
    } catch {
      this.toast.error('Could not dispatch workflow');
    } finally {
      this.dispatching.set(null);
    }
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalRunPages()) return;
    this.applyFilter({}, page);
  }

  async cancelRun(run: WorkflowRun, event: Event): Promise<void> {
    event.preventDefault();
    event.stopPropagation();
    if (!isRunActive(run.status)) return;
    this.cancelling.set(run.id);
    try {
      await this.runService.cancelRun(this.owner(), this.repoName(), run.id);
      this.toast.success('Run cancelled');
      await this.loadRuns(true);
    } catch {
      this.toast.error('Could not cancel run');
    } finally {
      this.cancelling.set(null);
    }
  }

  async deleteRun(run: WorkflowRun, event: Event): Promise<void> {
    event.preventDefault();
    event.stopPropagation();
    const confirmed = await this.confirmModal.confirm(
      `Delete workflow run #${run.runNumber}?`,
      'The run and its logs will be permanently deleted. This cannot be undone.',
      { confirmLabel: 'Delete workflow run', variant: 'danger' },
    );
    if (!confirmed) return;
    this.deleting.set(run.id);
    try {
      await this.runService.deleteRun(this.owner(), this.repoName(), run.id);
      this.toast.success('Run deleted');
      await this.loadRuns(true);
    } catch {
      this.toast.error('Could not delete run');
    } finally {
      this.deleting.set(null);
    }
  }

  runDisplayStatus(run: WorkflowRun): string {
    return resolveWorkflowDisplayStatus(run.status, run.conclusion);
  }

  runTitle(run: WorkflowRun): string {
    return runTitle(run);
  }

  /** "Commit abc1234 pushed by user", "Manually run by user", … like GitHub's run subtitle. */
  runSubtitle(run: WorkflowRun): string {
    const actor = run.actorName ? ` by ${run.actorName}` : '';
    const sha = run.triggerSha ? run.triggerSha.slice(0, 7) : '';
    switch (run.triggerEvent) {
      case 'push':
        return `${sha ? `Commit ${sha} pushed` : 'Pushed'}${actor}`;
      case 'pull_request':
        return `Pull request${sha ? ` ${sha}` : ''} updated${actor}`;
      case 'workflow_dispatch':
        return `Manually run${actor}`;
      case 'schedule':
        return 'Scheduled';
      default:
        return `${run.triggerEvent}${actor}`;
    }
  }

  runDuration(run: WorkflowRun): string {
    return run.startedAt ? formatDuration(run.startedAt, run.completedAt, this.now()) : '';
  }

  private syncPolling(runs: WorkflowRun[]): void {
    if (runs.some((r) => isRunActive(r.status))) {
      this.pollTimer ??= setInterval(() => void this.loadRuns(true), 3000);
      this.clockTimer ??= setInterval(() => this.now.set(Date.now()), 1000);
    } else {
      this.stopPolling();
    }
  }

  private stopPolling(): void {
    if (this.clockTimer) {
      clearInterval(this.clockTimer);
      this.clockTimer = null;
    }
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }
}
