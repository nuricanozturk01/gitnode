import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import type { WorkflowJob } from '../../../../../domain/actions/models/workflow-job.model';
import { resolveWorkflowDisplayStatus, workflowStatusTone } from '../../../../../shared/utils/workflow-status.utils';

interface JobLayer {
  jobs: WorkflowJob[];
}

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-job-graph',
  standalone: true,
  imports: [LucideAngularModule],
  templateUrl: './job-graph.component.html',
  styleUrl: './job-graph.component.css',
})
export class JobGraphComponent {
  readonly jobs = input.required<WorkflowJob[]>();
  readonly selectedJobId = input<string | null>(null);

  readonly jobSelected = output<string>();

  readonly layers = computed(() => this.buildLayers(this.jobs()));

  nodeClass(job: WorkflowJob): string {
    const tone = workflowStatusTone(resolveWorkflowDisplayStatus(job.status, job.conclusion));
    return `job-graph__node job-graph__node--${tone}`;
  }

  selectJob(jobId: string): void {
    this.jobSelected.emit(jobId);
  }

  private buildLayers(jobs: WorkflowJob[]): JobLayer[] {
    if (jobs.length === 0) return [];

    const byName = new Map(jobs.map((j) => [j.name, j]));
    const assigned = new Set<string>();
    const layers: JobLayer[] = [];

    while (assigned.size < jobs.length) {
      const layer: WorkflowJob[] = [];

      for (const job of jobs) {
        if (assigned.has(job.id)) continue;

        const needs = job.needs ?? [];
        const depsReady = needs.every((need) => {
          const dep = byName.get(need);
          return dep != null && assigned.has(dep.id);
        });

        if (depsReady) {
          layer.push(job);
        }
      }

      if (layer.length === 0) {
        for (const job of jobs) {
          if (!assigned.has(job.id)) {
            layer.push(job);
          }
        }
      }

      for (const job of layer) {
        assigned.add(job.id);
      }
      layers.push({ jobs: layer });
    }

    return layers;
  }
}
