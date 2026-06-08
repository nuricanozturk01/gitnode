import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { RegistrationToken, RunnerInfo } from '../../../domain/actions/models/runner.model';

@Injectable({ providedIn: 'root' })
export class RunnerService {
  private readonly http = inject(HttpClient);

  private readonly base = `${environment.apiUrl}/api/actions/runners`;

  list(): Promise<RunnerInfo[]> {
    return firstValueFrom(this.http.get<RunnerInfo[]>(this.base));
  }

  createRegistrationToken(): Promise<RegistrationToken> {
    return firstValueFrom(this.http.post<RegistrationToken>(`${this.base}/registration-token`, null));
  }

  delete(runnerId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base}/${runnerId}`));
  }
}
