import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ADMIN_PAGE_SIZE, type PagedResponse } from '../organization/organization.models';
import type { AdminRepoSummary } from './admin.models';

@Injectable({ providedIn: 'root' })
export class AdminRepoService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/admin/repos`;

  async list(
    query: {
      page?: number;
      size?: number;
      q?: string;
      owner?: string;
    } = {},
  ): Promise<PagedResponse<AdminRepoSummary>> {
    let params = new HttpParams()
      .set('page', String(query.page ?? 0))
      .set('size', String(query.size ?? ADMIN_PAGE_SIZE));

    const q = query.q?.trim();
    const owner = query.owner?.trim();
    if (q) params = params.set('q', q);
    if (owner) params = params.set('owner', owner);

    return firstValueFrom(this.http.get<PagedResponse<AdminRepoSummary>>(this.base, { params }));
  }
}
