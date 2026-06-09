import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { SecretInfo } from '../../../domain/actions/models/secret.model';

@Injectable({ providedIn: 'root' })
export class SecretService {
  private readonly http = inject(HttpClient);

  private base(owner: string, repo: string): string {
    return `${environment.apiUrl}/api/repos/${owner}/${repo}/actions/secrets`;
  }

  list(owner: string, repo: string): Promise<SecretInfo[]> {
    return firstValueFrom(this.http.get<SecretInfo[]>(this.base(owner, repo)));
  }

  createOrUpdate(owner: string, repo: string, name: string, value: string): Promise<void> {
    return firstValueFrom(this.http.put<void>(`${this.base(owner, repo)}/${encodeURIComponent(name)}`, { value }));
  }

  delete(owner: string, repo: string, name: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(owner, repo)}/${encodeURIComponent(name)}`));
  }
}
