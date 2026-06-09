import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { AdminPlatformSettings, AdminFeatureTogglesRequest, PlatformAdminsResponse } from './admin.models';

@Injectable({ providedIn: 'root' })
export class AdminSettingsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/admin/settings`;

  async getSettings(): Promise<AdminPlatformSettings> {
    return firstValueFrom(this.http.get<AdminPlatformSettings>(this.base));
  }

  async updateStatsCacheTtl(statsCacheTtlSeconds: number): Promise<AdminPlatformSettings> {
    return firstValueFrom(
      this.http.put<AdminPlatformSettings>(`${this.base}/stats-cache`, {
        statsCacheTtlSeconds,
      }),
    );
  }

  async updateFeatureToggles(toggles: AdminFeatureTogglesRequest): Promise<AdminPlatformSettings> {
    return firstValueFrom(this.http.put<AdminPlatformSettings>(`${this.base}/feature-toggles`, toggles));
  }

  async getPlatformAdmins(): Promise<PlatformAdminsResponse> {
    return firstValueFrom(this.http.get<PlatformAdminsResponse>(`${this.base}/platform-admins`));
  }
}
