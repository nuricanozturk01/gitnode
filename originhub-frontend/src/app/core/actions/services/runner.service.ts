import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { RegistrationToken, RunnerInfo } from '../../../domain/actions/models/runner.model';

@Injectable({ providedIn: 'root' })
export class RunnerService {
  private readonly http = inject(HttpClient);

  private base(owner: string, repo: string): string {
    return `${environment.apiUrl}/api/repos/${owner}/${repo}/actions/runners`;
  }

  list(owner: string, repo: string): Promise<RunnerInfo[]> {
    return firstValueFrom(this.http.get<RunnerInfo[]>(this.base(owner, repo)));
  }

  createRegistrationToken(owner: string, repo: string): Promise<RegistrationToken> {
    return firstValueFrom(this.http.post<RegistrationToken>(`${this.base(owner, repo)}/registration-token`, null));
  }

  delete(owner: string, repo: string, runnerId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(owner, repo)}/${runnerId}`));
  }
}
