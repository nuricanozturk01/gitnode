import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { OrganizationSummary, PagedResponse } from '../organization/organization.models';

@Injectable({ providedIn: 'root' })
export class PlatformAdminService {
  private readonly http = inject(HttpClient);

  private readonly api = `${environment.apiUrl}/api/admin/organizations`;
  private checkPromise: Promise<boolean> | null = null;

  readonly verified = signal<boolean | null>(null);

  reset(): void {
    this.checkPromise = null;
    this.verified.set(null);
  }

  async verifyAccess(): Promise<boolean> {
    if (this.verified() === true) return true;
    if (this.verified() === false) return false;

    if (!this.checkPromise) {
      this.checkPromise = this.performCheck();
    }
    return this.checkPromise;
  }

  private async performCheck(): Promise<boolean> {
    try {
      await firstValueFrom(this.http.get<OrganizationSummary[] | PagedResponse<OrganizationSummary>>(this.api));
      this.verified.set(true);
      return true;
    } catch {
      this.verified.set(false);
      return false;
    }
  }
}
