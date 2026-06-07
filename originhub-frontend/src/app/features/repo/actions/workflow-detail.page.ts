import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { LucideAngularModule } from 'lucide-angular';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { WorkflowService } from '../../../core/actions/services/workflow.service';
import { WorkflowRunService } from '../../../core/actions/services/workflow-run.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { ToastService } from '../../../core/toast/toast.service';
import type { WorkflowDetail } from '../../../domain/actions/models/workflow-detail.model';
import type { WorkflowRun } from '../../../domain/actions/models/workflow-run.model';
import {
  resolveWorkflowDisplayStatus,
  workflowStatusBadgeClass,
  workflowStatusIconClass,
  workflowStatusIconName,
  workflowStatusIconSpinning,
  workflowStatusLabel,
} from '../../../shared/utils/workflow-status.utils';
import { SourceCodeViewerComponent } from '../../../shared/components/source-code-viewer/source-code-viewer.component';
import {
  WorkflowDispatchModalComponent,
  type DispatchConfirmedEvent,
} from './components/workflow-dispatch-modal/workflow-dispatch-modal.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-workflow-detail',
  standalone: true,
  imports: [
    RouterLink,
    LucideAngularModule,
    RelativeTimePipe,
    SourceCodeViewerComponent,
    WorkflowDispatchModalComponent,
  ],
  templateUrl: './workflow-detail.page.html',
})
export class WorkflowDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly workflowService = inject(WorkflowService);
  private readonly runService = inject(WorkflowRunService);
  private readonly toast = inject(ToastService);
  readonly repoContext = inject(RepoContextService);

  readonly workflow = signal<WorkflowDetail | null>(null);
  readonly runs = signal<WorkflowRun[]>([]);
  readonly loading = signal(true);
  readonly dispatching = signal(false);
  readonly showDispatchModal = signal(false);

  private readonly routeParams = parentParamMapSignal(this.route);
  readonly owner = computed(() => this.routeParams().get('owner') ?? '');
  readonly repoName = computed(() => this.routeParams().get('repo') ?? '');

  private readonly filePathParam = toSignal(this.route.queryParamMap.pipe(map((p) => p.get('path') ?? '')), {
    initialValue: this.route.snapshot.queryParamMap.get('path') ?? '',
  });

  readonly fileName = computed(() => {
    const path = this.workflow()?.filePath ?? this.filePathParam();
    return path.split('/').pop() ?? path;
  });

  readonly blobLink = computed(() => {
    const wf = this.workflow();
    if (!wf) return null;
    const pathSegments = wf.filePath.split('/').filter(Boolean);
    return ['/', this.owner(), this.repoName(), 'blob', wf.defaultBranch, ...pathSegments];
  });

  statusBadgeClass = workflowStatusBadgeClass;
  statusLabel = workflowStatusLabel;
  statusIconClass = workflowStatusIconClass;
  statusIconName = workflowStatusIconName;
  statusIconSpinning = workflowStatusIconSpinning;

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe(() => void this.loadPage());
  }

  runDisplayStatus(run: WorkflowRun): string {
    return resolveWorkflowDisplayStatus(run.status, run.conclusion);
  }

  dispatchWorkflow(): void {
    if (!this.workflow()) return;
    this.showDispatchModal.set(true);
  }

  closeDispatchModal(): void {
    this.showDispatchModal.set(false);
  }

  async onDispatchConfirmed(event: DispatchConfirmedEvent): Promise<void> {
    this.showDispatchModal.set(false);
    const wf = this.workflow();
    if (!wf) return;
    this.dispatching.set(true);
    try {
      await this.workflowService.dispatch(
        this.owner(),
        this.repoName(),
        wf.filePath,
        event.ref,
        Object.keys(event.inputs).length > 0 ? event.inputs : undefined,
      );
      this.toast.success('Workflow dispatched');
      await this.loadRuns(wf.name);
    } catch {
      this.toast.error('Could not dispatch workflow');
    } finally {
      this.dispatching.set(false);
    }
  }

  private async loadPage(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const filePath = this.filePathParam();
    if (!owner || !repo || !filePath) {
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    try {
      const detail = await this.workflowService.getDetail(owner, repo, filePath);
      this.workflow.set(detail);
      await this.loadRuns(detail.name);
    } catch {
      this.workflow.set(null);
      this.runs.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  private async loadRuns(workflowName: string): Promise<void> {
    try {
      const page = await this.runService.listRuns(this.owner(), this.repoName(), 0, 50);
      this.runs.set(page.content.filter((r) => r.workflowName === workflowName));
    } catch {
      this.runs.set([]);
    }
  }
}
