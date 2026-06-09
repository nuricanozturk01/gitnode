import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ADMIN_PAGE_SIZE, type PagedResponse, type UserPageQuery } from '../organization/organization.models';
import type { AdminUser, AdminUserDetail, SetUserEnabledRequest } from './admin.models';

@Injectable({ providedIn: 'root' })
export class AdminUserService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/admin/users`;

  async list(query: UserPageQuery = {}): Promise<PagedResponse<AdminUser>> {
    let params = new HttpParams()
      .set('page', String(query.page ?? 0))
      .set('size', String(query.size ?? ADMIN_PAGE_SIZE));

    const q = query.q?.trim();
    if (q) {
      params = params.set('q', q);
    }

    return firstValueFrom(this.http.get<PagedResponse<AdminUser>>(this.base, { params }));
  }

  async get(id: string): Promise<AdminUserDetail> {
    return firstValueFrom(this.http.get<AdminUserDetail>(`${this.base}/${encodeURIComponent(id)}`));
  }

  async setEnabled(id: string, enabled: boolean): Promise<AdminUser> {
    const body: SetUserEnabledRequest = { enabled };
    return firstValueFrom(this.http.put<AdminUser>(`${this.base}/${encodeURIComponent(id)}/enabled`, body));
  }
}
