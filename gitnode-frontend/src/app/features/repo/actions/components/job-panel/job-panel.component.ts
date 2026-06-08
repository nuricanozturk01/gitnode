import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import type { WorkflowJob } from '../../../../../domain/actions/models/workflow-job.model';
import {
  resolveWorkflowDisplayStatus,
  workflowStatusBadgeClass,
  workflowStatusIconClass,
  workflowStatusIconName,
  workflowStatusIconSpinning,
  workflowStatusLabel,
} from '../../../../../shared/utils/workflow-status.utils';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-job-panel',
  standalone: true,
  imports: [LucideAngularModule],
  templateUrl: './job-panel.component.html',
})
export class JobPanelComponent {
  readonly jobs = input.required<WorkflowJob[]>();
  readonly selectedJobId = input<string | null>(null);
  readonly expandedJobIds = input<Set<string>>(new Set());

  readonly jobSelected = output<string>();
  readonly stepSelected = output<string>();

  statusBadgeClass = workflowStatusBadgeClass;
  statusLabel = workflowStatusLabel;
  statusIconClass = workflowStatusIconClass;
  statusIconName = workflowStatusIconName;
  statusIconSpinning = workflowStatusIconSpinning;
  resolveDisplayStatus = resolveWorkflowDisplayStatus;

  displayStatus(job: WorkflowJob): string {
    return resolveWorkflowDisplayStatus(job.status, job.conclusion);
  }

  selectJob(jobId: string): void {
    this.jobSelected.emit(jobId);
  }

  selectStep(stepId: string): void {
    this.stepSelected.emit(stepId);
  }
}
