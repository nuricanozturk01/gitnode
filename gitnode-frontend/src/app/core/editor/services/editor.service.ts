import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { BlobResponse } from '../../../domain/repository/models/blob-response.model';
import type { TreeResponse } from '../../../domain/repository/models/tree-response.model';

export interface EditorChange {
  path: string;
  content?: string;
  delete: boolean;
}

@Injectable({ providedIn: 'root' })
export class EditorService {
  private readonly http = inject(HttpClient);

  private base(owner: string, repo: string): string {
    return `${environment.apiUrl}/api/repos/${owner}/${repo}`;
  }

  private encode(path: string): string {
    return path.split('/').map(encodeURIComponent).join('/');
  }

  tree(owner: string, repo: string, branch: string, path: string): Promise<TreeResponse> {
    const suffix = path ? `/${this.encode(path)}` : '';
    return firstValueFrom(
      this.http.get<TreeResponse>(`${this.base(owner, repo)}/tree/${encodeURIComponent(branch)}${suffix}`),
    );
  }

  blob(owner: string, repo: string, branch: string, path: string): Promise<BlobResponse> {
    return firstValueFrom(
      this.http.get<BlobResponse>(`${this.base(owner, repo)}/blob/${encodeURIComponent(branch)}/${this.encode(path)}`),
    );
  }

  async headSha(owner: string, repo: string, branch: string): Promise<string> {
    const branches = await firstValueFrom(
      this.http.get<{ name: string; lastCommitSha: string }[]>(`${this.base(owner, repo)}/branches`),
    );
    const found = branches.find((b) => b.name === branch);
    if (!found) throw new Error('branch-not-found');
    return found.lastCommitSha;
  }

  commit(
    owner: string,
    repo: string,
    form: { branch: string; expectedHeadSha: string; message: string; description?: string; changes: EditorChange[] },
  ): Promise<{ commitSha: string }> {
    return firstValueFrom(this.http.post<{ commitSha: string }>(`${this.base(owner, repo)}/changes`, form));
  }
}
