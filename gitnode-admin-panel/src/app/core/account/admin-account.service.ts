import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { AdminAccountProfile } from '../admin/admin.models';

@Injectable({ providedIn: 'root' })
export class AdminAccountService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/users`;

  async getProfile(): Promise<AdminAccountProfile> {
    return firstValueFrom(this.http.get<AdminAccountProfile>(`${this.base}/me`));
  }

  async updateDisplayName(displayName: string): Promise<AdminAccountProfile> {
    return firstValueFrom(this.http.patch<AdminAccountProfile>(`${this.base}/me/display-name`, { displayName }));
  }

  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    await firstValueFrom(
      this.http.patch<void>(`${this.base}/me/password`, {
        currentPassword,
        newPassword,
      }),
    );
  }
}
