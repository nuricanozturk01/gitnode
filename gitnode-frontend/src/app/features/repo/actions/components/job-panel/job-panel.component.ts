import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import type { WorkflowJob } from '../../../../../domain/actions/models/workflow-job.model';
import {
  formatDuration,
  resolveWorkflowDisplayStatus,
  workflowStatusIconClass,
  workflowStatusIconName,
  workflowStatusIconSpinning,
} from '../../../../../shared/utils/workflow-status.utils';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-job-panel',
  host: { class: 'block' },
  standalone: true,
  imports: [LucideAngularModule],
  templateUrl: './job-panel.component.html',
})
export class JobPanelComponent {
  readonly jobs = input.required<WorkflowJob[]>();
  readonly selectedJobId = input<string | null>(null);
  /** Current time, ticking while the run is active, so running jobs count up. */
  readonly now = input<number>(Date.now());

  readonly jobSelected = output<string>();

  statusIconClass = workflowStatusIconClass;
  statusIconName = workflowStatusIconName;
  statusIconSpinning = workflowStatusIconSpinning;

  displayStatus(job: WorkflowJob): string {
    return resolveWorkflowDisplayStatus(job.status, job.conclusion);
  }

  duration(job: WorkflowJob): string {
    return job.startedAt ? formatDuration(job.startedAt, job.completedAt, this.now()) : '';
  }

  selectJob(jobId: string): void {
    this.jobSelected.emit(jobId);
  }
}
