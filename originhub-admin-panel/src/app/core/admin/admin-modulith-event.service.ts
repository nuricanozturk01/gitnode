import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AUDIT_PAGE_SIZE } from '../organization/organization.models';
import type {
  ModulithEventDetail,
  ModulithEventFilters,
  ModulithEventQuery,
  ModulithEventSearchResponse,
} from './admin.models';

@Injectable({ providedIn: 'root' })
export class AdminModulithEventService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/admin/modulith-events`;

  async status(): Promise<{ available: boolean; message: string }> {
    return firstValueFrom(this.http.get<{ available: boolean; message: string }>(`${this.base}/status`));
  }

  async list(query: ModulithEventQuery = {}): Promise<ModulithEventSearchResponse> {
    let params = new HttpParams()
      .set('page', String(query.page ?? 0))
      .set('size', String(query.size ?? AUDIT_PAGE_SIZE))
      .set('lifecycle', query.lifecycle ?? 'ALL');

    params = this.setOptional(params, 'q', query.q);
    params = this.setOptional(params, 'eventType', query.eventType);
    params = this.setOptional(params, 'listenerId', query.listenerId);
    params = this.setOptional(params, 'status', query.status);
    params = this.setOptional(params, 'from', query.from);
    params = this.setOptional(params, 'to', query.to);

    return firstValueFrom(this.http.get<ModulithEventSearchResponse>(this.base, { params }));
  }

  async filters(): Promise<ModulithEventFilters> {
    return firstValueFrom(this.http.get<ModulithEventFilters>(`${this.base}/filters`));
  }

  async detail(id: string): Promise<ModulithEventDetail> {
    return firstValueFrom(this.http.get<ModulithEventDetail>(`${this.base}/${id}`));
  }

  private setOptional(params: HttpParams, key: string, value?: string): HttpParams {
    const trimmed = value?.trim();
    return trimmed ? params.set(key, trimmed) : params;
  }
}
