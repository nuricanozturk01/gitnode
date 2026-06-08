import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { StatsOverviewResponse, StatsPeriod, StatsReposResponse, StatsUploadsResponse } from './admin.models';

@Injectable({ providedIn: 'root' })
export class AdminStatsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/admin/stats`;

  async getOverview(refresh = false): Promise<StatsOverviewResponse> {
    return firstValueFrom(
      this.http.get<StatsOverviewResponse>(`${this.base}/overview`, {
        params: refresh ? { refresh: 'true' } : {},
      }),
    );
  }

  async getRepos(period: StatsPeriod, refresh = false): Promise<StatsReposResponse> {
    return firstValueFrom(
      this.http.get<StatsReposResponse>(`${this.base}/repos`, {
        params: refresh ? { period, refresh: 'true' } : { period },
      }),
    );
  }

  async getUploads(period: StatsPeriod, refresh = false): Promise<StatsUploadsResponse> {
    return firstValueFrom(
      this.http.get<StatsUploadsResponse>(`${this.base}/uploads`, {
        params: refresh ? { period, refresh: 'true' } : { period },
      }),
    );
  }
}
