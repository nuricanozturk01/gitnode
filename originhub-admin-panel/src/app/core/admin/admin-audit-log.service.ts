import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AUDIT_PAGE_SIZE, type PagedResponse } from '../organization/organization.models';
import type { AuditLogEntry, AuditLogFilters, AuditLogQuery } from './admin.models';

@Injectable({ providedIn: 'root' })
export class AdminAuditLogService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/admin/audit-logs`;

  async list(query: AuditLogQuery = {}): Promise<PagedResponse<AuditLogEntry>> {
    let params = new HttpParams()
      .set('page', String(query.page ?? 0))
      .set('size', String(query.size ?? AUDIT_PAGE_SIZE));

    params = this.setOptional(params, 'q', query.q);
    params = this.setOptional(params, 'actor', query.actor);
    params = this.setOptional(params, 'action', query.action);
    params = this.setOptional(params, 'entityType', query.entityType);
    params = this.setOptional(params, 'entityId', query.entityId);
    params = this.setOptional(params, 'from', query.from);
    params = this.setOptional(params, 'to', query.to);

    return firstValueFrom(this.http.get<PagedResponse<AuditLogEntry>>(this.base, { params }));
  }

  async filters(): Promise<AuditLogFilters> {
    return firstValueFrom(this.http.get<AuditLogFilters>(`${this.base}/filters`));
  }

  private setOptional(params: HttpParams, key: string, value?: string): HttpParams {
    const trimmed = value?.trim();
    return trimmed ? params.set(key, trimmed) : params;
  }
}
