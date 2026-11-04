import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { WorkflowRun, WorkflowRunPage } from '../../../domain/actions/models/workflow-run.model';

export interface WorkflowRunFilter {
  workflow?: string;
  event?: string;
  triggerRef?: string;
  branch?: string;
  status?: string;
  actor?: string;
  query?: string;
}

@Injectable({ providedIn: 'root' })
export class WorkflowRunService {
  private readonly http = inject(HttpClient);

  private base(owner: string, repo: string): string {
    return `${environment.apiUrl}/api/repos/${owner}/${repo}/actions`;
  }

  listRuns(owner: string, repo: string, page = 0, size = 20, filter: WorkflowRunFilter = {}): Promise<WorkflowRunPage> {
    let params = new HttpParams().set('page', String(page)).set('size', String(size));
    const entries: [string, string | undefined][] = [
      ['triggerEvent', filter.event],
      ['triggerRef', filter.triggerRef],
      ['workflow', filter.workflow],
      ['branch', filter.branch],
      ['status', filter.status],
      ['actor', filter.actor],
      ['q', filter.query],
    ];
    for (const [key, value] of entries) {
      if (value && value.trim()) params = params.set(key, value.trim());
    }
    return firstValueFrom(this.http.get<WorkflowRunPage>(`${this.base(owner, repo)}/runs`, { params }));
  }

  getRun(owner: string, repo: string, runId: string): Promise<WorkflowRun> {
    return firstValueFrom(this.http.get<WorkflowRun>(`${this.base(owner, repo)}/runs/${runId}`));
  }

  cancelRun(owner: string, repo: string, runId: string): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${this.base(owner, repo)}/runs/${runId}/cancel`, null));
  }

  deleteRun(owner: string, repo: string, runId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(owner, repo)}/runs/${runId}`));
  }

  rerun(
    owner: string,
    repo: string,
    runId: string,
    options: { failedOnly?: boolean; jobId?: string } = {},
  ): Promise<void> {
    return firstValueFrom(
      this.http.post<void>(`${this.base(owner, repo)}/runs/${runId}/rerun`, {
        failedOnly: options.failedOnly ?? false,
        jobId: options.jobId ?? null,
      }),
    );
  }
}
