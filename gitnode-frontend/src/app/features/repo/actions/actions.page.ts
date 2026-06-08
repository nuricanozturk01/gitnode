import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Location } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { WorkflowService } from '../../../core/actions/services/workflow.service';
import { WorkflowRunService } from '../../../core/actions/services/workflow-run.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { ToastService } from '../../../core/toast/toast.service';
import type { WorkflowSummary } from '../../../domain/actions/models/workflow-summary.model';
import type { WorkflowDetail } from '../../../domain/actions/models/workflow-detail.model';
import type { WorkflowRun } from '../../../domain/actions/models/workflow-run.model';
import {
  isRunActive,
  resolveWorkflowDisplayStatus,
  workflowStatusBadgeClass,
  workflowStatusIconClass,
  workflowStatusIconName,
  workflowStatusIconSpinning,
  workflowStatusLabel,
} from '../../../shared/utils/workflow-status.utils';
import { parseUrlTab, replaceUrlFragment } from '../../../shared/utils/url-tab.utils';
import {
  WorkflowDispatchModalComponent,
  type DispatchConfirmedEvent,
} from './components/workflow-dispatch-modal/workflow-dispatch-modal.component';

const ACTIONS_TABS = ['workflows', 'runs'] as const;
type ActionsTab = (typeof ACTIONS_TABS)[number];
const DEFAULT_ACTIONS_TAB: ActionsTab = 'workflows';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-actions',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, RelativeTimePipe, WorkflowDispatchModalComponent],
  templateUrl: './actions.page.html',
})
export class ActionsPage {
  private readonly route = inject(ActivatedRoute);
  private readonly location = inject(Location);
  private readonly workflowService = inject(WorkflowService);
  private readonly runService = inject(WorkflowRunService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  readonly repoContext = inject(RepoContextService);

  readonly tab = signal<ActionsTab>(DEFAULT_ACTIONS_TAB);
  readonly workflows = signal<WorkflowSummary[]>([]);
  readonly runs = signal<WorkflowRun[]>([]);
  readonly loading = signal(true);
  readonly runPage = signal(0);
  readonly totalRunPages = signal(0);
  readonly totalRuns = signal(0);
  readonly dispatching = signal<string | null>(null);
  readonly cancelling = signal<string | null>(null);
  readonly pendingDispatchDetail = signal<WorkflowDetail | null>(null);

  isRunActive = isRunActive;

  private pollTimer: ReturnType<typeof setInterval> | null = null;

  private readonly routeParams = parentParamMapSignal(this.route);
  readonly owner = computed(() => this.routeParams().get('owner') ?? '');
  readonly repoName = computed(() => this.routeParams().get('repo') ?? '');

  statusBadgeClass = workflowStatusBadgeClass;
  statusLabel = workflowStatusLabel;
  statusIconClass = workflowStatusIconClass;
  statusIconName = workflowStatusIconName;
  statusIconSpinning = workflowStatusIconSpinning;

  constructor() {
    this.route.fragment.pipe(takeUntilDestroyed()).subscribe((fragment) => {
      this.tab.set(parseUrlTab(fragment, ACTIONS_TABS, DEFAULT_ACTIONS_TAB));
      this.syncPollingForCurrentTab();
    });

    this.route.parent?.paramMap.pipe(takeUntilDestroyed()).subscribe(() => void this.loadData());
    this.destroyRef.onDestroy(() => this.stopPolling());
  }

  setTab(next: ActionsTab): void {
    this.tab.set(next);
    replaceUrlFragment(this.location, next === DEFAULT_ACTIONS_TAB ? null : next);
    this.syncPollingForCurrentTab();
  }

  private syncPollingForCurrentTab(): void {
    if (this.tab() === 'runs') {
      this.syncPolling(this.runs());
    } else {
      this.stopPolling();
    }
  }

  async loadData(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) return;

    this.loading.set(true);
    try {
      const [workflows, runPage] = await Promise.all([
        this.workflowService.list(owner, repo),
        this.runService.listRuns(owner, repo, this.runPage()),
      ]);
      this.workflows.set(workflows);
      this.runs.set(runPage.content);
      this.totalRunPages.set(runPage.totalPages);
      this.totalRuns.set(runPage.totalElements);
      this.syncPolling(runPage.content);
    } catch {
      this.workflows.set([]);
      this.runs.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  async toggleEnabled(workflow: WorkflowSummary): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    try {
      if (workflow.enabled) {
        await this.workflowService.disable(owner, repo, workflow.filePath);
      } else {
        await this.workflowService.enable(owner, repo, workflow.filePath);
      }
      await this.loadData();
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
      this.tab.set('runs');
      this.runPage.set(0);
      await this.loadData();
    } catch {
      this.toast.error('Could not dispatch workflow');
    } finally {
      this.dispatching.set(null);
    }
  }

  async prevRunPage(): Promise<void> {
    if (this.runPage() <= 0) return;
    this.runPage.update((p) => p - 1);
    await this.loadRunsOnly();
  }

  async nextRunPage(): Promise<void> {
    if (this.runPage() >= this.totalRunPages() - 1) return;
    this.runPage.update((p) => p + 1);
    await this.loadRunsOnly();
  }

  private async loadRunsOnly(): Promise<void> {
    try {
      const runPage = await this.runService.listRuns(this.owner(), this.repoName(), this.runPage());
      this.runs.set(runPage.content);
      this.totalRunPages.set(runPage.totalPages);
      this.totalRuns.set(runPage.totalElements);
      this.syncPolling(runPage.content);
    } catch {
      this.runs.set([]);
    }
  }

  private syncPolling(runs: WorkflowRun[]): void {
    const hasActive = runs.some((r) => isRunActive(r.status));
    if (hasActive) {
      this.startPolling();
    } else {
      this.stopPolling();
    }
  }

  private startPolling(): void {
    if (this.pollTimer) return;
    this.pollTimer = setInterval(() => void this.loadRunsOnly(), 2000);
  }

  private stopPolling(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }

  runDisplayStatus(run: WorkflowRun): string {
    return resolveWorkflowDisplayStatus(run.status, run.conclusion);
  }

  async cancelRun(run: WorkflowRun, event: Event): Promise<void> {
    event.preventDefault();
    event.stopPropagation();
    if (!isRunActive(run.status)) return;
    this.cancelling.set(run.id);
    try {
      await this.runService.cancelRun(this.owner(), this.repoName(), run.id);
      this.toast.success('Run cancelled');
      await this.loadRunsOnly();
    } catch {
      this.toast.error('Could not cancel run');
    } finally {
      this.cancelling.set(null);
    }
  }
}
