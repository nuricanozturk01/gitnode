import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { WorkflowSummary } from '../../../domain/actions/models/workflow-summary.model';
import type { WorkflowDetail } from '../../../domain/actions/models/workflow-detail.model';

@Injectable({ providedIn: 'root' })
export class WorkflowService {
  private readonly http = inject(HttpClient);

  private base(owner: string, repo: string): string {
    return `${environment.apiUrl}/api/repos/${owner}/${repo}/actions`;
  }

  list(owner: string, repo: string): Promise<WorkflowSummary[]> {
    return firstValueFrom(this.http.get<WorkflowSummary[]>(`${this.base(owner, repo)}/workflows`));
  }

  getDetail(owner: string, repo: string, filePath: string): Promise<WorkflowDetail> {
    const params = new HttpParams().set('filePath', filePath);
    return firstValueFrom(this.http.get<WorkflowDetail>(`${this.base(owner, repo)}/workflows/detail`, { params }));
  }

  enable(owner: string, repo: string, filePath: string): Promise<void> {
    const params = new HttpParams().set('filePath', filePath);
    return firstValueFrom(this.http.put<void>(`${this.base(owner, repo)}/workflows/enable`, null, { params }));
  }

  disable(owner: string, repo: string, filePath: string): Promise<void> {
    const params = new HttpParams().set('filePath', filePath);
    return firstValueFrom(this.http.put<void>(`${this.base(owner, repo)}/workflows/disable`, null, { params }));
  }

  dispatch(
    owner: string,
    repo: string,
    workflowFilePath: string,
    ref: string,
    inputs?: Record<string, string>,
  ): Promise<void> {
    return firstValueFrom(
      this.http.post<void>(`${this.base(owner, repo)}/workflows/dispatches`, {
        filePath: workflowFilePath,
        ref,
        inputs: inputs ?? null,
      }),
    );
  }
}
