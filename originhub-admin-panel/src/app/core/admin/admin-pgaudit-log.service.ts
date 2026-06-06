import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AUDIT_PAGE_SIZE } from '../organization/organization.models';
import type { PgAuditLogQuery, PgAuditLogSearchResponse, PgAuditLogStatus } from './admin.models';

@Injectable({ providedIn: 'root' })
export class AdminPgAuditLogService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/admin/pgaudit-logs`;

  async status(): Promise<PgAuditLogStatus> {
    return firstValueFrom(this.http.get<PgAuditLogStatus>(`${this.base}/status`));
  }

  async list(query: PgAuditLogQuery = {}): Promise<PgAuditLogSearchResponse> {
    let params = new HttpParams()
      .set('page', String(query.page ?? 0))
      .set('size', String(query.size ?? AUDIT_PAGE_SIZE));

    params = this.setOptional(params, 'q', query.q);
    params = this.setOptional(params, 'user', query.user);
    params = this.setOptional(params, 'category', query.category);
    params = this.setOptional(params, 'from', query.from);
    params = this.setOptional(params, 'to', query.to);

    return firstValueFrom(this.http.get<PgAuditLogSearchResponse>(this.base, { params }));
  }

  private setOptional(params: HttpParams, key: string, value?: string): HttpParams {
    const trimmed = value?.trim();
    return trimmed ? params.set(key, trimmed) : params;
  }
}
