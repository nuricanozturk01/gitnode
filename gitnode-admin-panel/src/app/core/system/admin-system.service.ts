import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { ActuatorMetricResponse, ActuatorMetricsIndex, SystemHealthResponse } from '../admin/admin.models';

@Injectable({ providedIn: 'root' })
export class AdminSystemService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/actuator`;

  async getHealth(): Promise<SystemHealthResponse> {
    return firstValueFrom(this.http.get<SystemHealthResponse>(`${this.base}/health`));
  }

  async listMetrics(): Promise<ActuatorMetricsIndex> {
    return firstValueFrom(this.http.get<ActuatorMetricsIndex>(`${this.base}/metrics`));
  }

  async getMetric(name: string): Promise<ActuatorMetricResponse> {
    return firstValueFrom(this.http.get<ActuatorMetricResponse>(`${this.base}/metrics/${encodeURIComponent(name)}`));
  }
}
