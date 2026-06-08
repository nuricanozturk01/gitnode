import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ADMIN_PAGE_SIZE, type PagedResponse } from '../organization/organization.models';
import type { WebhookDlqEntry, WebhookDlqSummary } from './admin.models';

@Injectable({ providedIn: 'root' })
export class AdminWebhookDlqService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/admin/webhooks/dlq`;

  async list(query: { page?: number; size?: number } = {}): Promise<PagedResponse<WebhookDlqEntry>> {
    const params = new HttpParams()
      .set('page', String(query.page ?? 0))
      .set('size', String(query.size ?? ADMIN_PAGE_SIZE));

    return firstValueFrom(this.http.get<PagedResponse<WebhookDlqEntry>>(this.base, { params }));
  }

  async summary(): Promise<WebhookDlqSummary> {
    return firstValueFrom(this.http.get<WebhookDlqSummary>(`${this.base}/summary`));
  }

  async retry(id: string): Promise<void> {
    await firstValueFrom(this.http.post<void>(`${this.base}/${encodeURIComponent(id)}/retry`, null));
  }

  async dismiss(id: string): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`${this.base}/${encodeURIComponent(id)}`));
  }
}
