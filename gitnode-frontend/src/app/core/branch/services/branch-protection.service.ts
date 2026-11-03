import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface BranchProtection {
  id: string;
  branchName: string;
  requiredApprovals: number;
  requiredChecks: string[];
  requireConversationResolution: boolean;
}

@Injectable({ providedIn: 'root' })
export class BranchProtectionService {
  private readonly http = inject(HttpClient);
  private base(owner: string, repo: string): string {
    return `${environment.apiUrl}/api/repos/${owner}/${repo}/branch-protections`;
  }
  list(owner: string, repo: string): Promise<BranchProtection[]> {
    return firstValueFrom(this.http.get<BranchProtection[]>(this.base(owner, repo)));
  }
  save(
    owner: string,
    repo: string,
    branchName: string,
    requiredApprovals: number,
    requiredChecks: string[] = [],
    requireConversationResolution = false,
  ): Promise<BranchProtection> {
    return firstValueFrom(
      this.http.put<BranchProtection>(this.base(owner, repo), {
        branchName,
        requiredApprovals,
        requiredChecks,
        requireConversationResolution,
      }),
    );
  }
  delete(owner: string, repo: string, id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(owner, repo)}/${id}`));
  }
}
