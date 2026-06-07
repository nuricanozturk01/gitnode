import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AdminRunnerSummary {
  id: string;
  repoId: string | null;
  name: string;
  labels: string[];
  status: 'ONLINE' | 'OFFLINE' | 'BUSY';
  os: string | null;
  arch: string | null;
  version: string | null;
  lastHeartbeat: string | null;
  createdAt: string;
}

export interface AdminActionsStats {
  totalRuns: number;
  successRuns: number;
  failureRuns: number;
  cancelledRuns: number;
  inProgressRuns: number;
  totalRunners: number;
  onlineRunners: number;
  busyRunners: number;
}

@Injectable({ providedIn: 'root' })
export class AdminActionsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/admin/actions`;

  getRunners(): Observable<AdminRunnerSummary[]> {
    return this.http.get<AdminRunnerSummary[]>(`${this.base}/runners`);
  }

  getStats(): Observable<AdminActionsStats> {
    return this.http.get<AdminActionsStats>(`${this.base}/stats`);
  }
}
