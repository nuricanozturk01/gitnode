import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { WorkflowSummary, WorkflowSummaryPage } from '../../../domain/actions/models/workflow-summary.model';
import type { WorkflowDetail } from '../../../domain/actions/models/workflow-detail.model';

/** Safety stop for listAll: 50 pages of 100 is far beyond any real repository. */
const MAX_PAGES = 50;

@Injectable({ providedIn: 'root' })
export class WorkflowService {
  private readonly http = inject(HttpClient);

  private base(owner: string, repo: string): string {
    return `${environment.apiUrl}/api/repos/${owner}/${repo}/actions`;
  }

  /** One page of the repository's workflows, ordered by name; `query` filters by name or path. */
  list(owner: string, repo: string, page = 0, size = 20, query?: string): Promise<WorkflowSummaryPage> {
    let params = new HttpParams().set('page', String(page)).set('size', String(size));
    if (query?.trim()) params = params.set('q', query.trim());
    return firstValueFrom(this.http.get<WorkflowSummaryPage>(`${this.base(owner, repo)}/workflows`, { params }));
  }

  /** Every workflow of the repository, fetched page by page (for pickers that need them all). */
  async listAll(owner: string, repo: string): Promise<WorkflowSummary[]> {
    const all: WorkflowSummary[] = [];
    for (let page = 0; page < MAX_PAGES; page++) {
      const result = await this.list(owner, repo, page, 100);
      all.push(...result.content);
      if (page + 1 >= result.totalPages) break;
    }
    return all;
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
