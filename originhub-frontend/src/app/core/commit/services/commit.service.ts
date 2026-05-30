///
/// Copyright 2026 the original author or authors.
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///      https://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { CommitInfo } from '../../../domain/commit/models/commit-info.model';
import type { CommitDetail } from '../../../domain/commit/models/commit-detail.model';
import type { FileDiff } from '../../../domain/commit/models/file-diff.model';
import type { PagedResult } from '../../../domain/commit/models/paged-result.model';

@Injectable({ providedIn: 'root' })
export class CommitService {
  private readonly http = inject(HttpClient);

  private base(owner: string, repo: string): string {
    return `${environment.apiUrl}/api/repos/${owner}/${repo}/commits`;
  }

  getCommits(owner: string, repo: string, branch = 'main', page = 0, size = 20): Promise<PagedResult<CommitInfo>> {
    const params = new HttpParams().set('branch', branch).set('page', String(page)).set('size', String(size));
    return firstValueFrom(this.http.get<PagedResult<CommitInfo>>(this.base(owner, repo), { params }));
  }

  getCommit(owner: string, repo: string, sha: string): Promise<CommitDetail> {
    return firstValueFrom(this.http.get<CommitDetail>(`${this.base(owner, repo)}/${sha}`));
  }

  getCommitDiff(owner: string, repo: string, sha: string): Promise<FileDiff[]> {
    return firstValueFrom(this.http.get<FileDiff[]>(`${this.base(owner, repo)}/${sha}/diff`));
  }
}
