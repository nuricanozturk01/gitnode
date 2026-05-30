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
import type { FileDiff } from '../../../domain/commit/models/file-diff.model';
import type { PullRequestDetail } from '../../../domain/pull-request/models/pull-request-detail.model';
import type { PullRequestInfo } from '../../../domain/pull-request/models/pull-request-info.model';
import type { PrCommentInfo } from '../../../domain/pull-request/models/pr-comment-info.model';

@Injectable({ providedIn: 'root' })
export class PullRequestService {
  private readonly http = inject(HttpClient);

  private base(owner: string, repo: string): string {
    return `${environment.apiUrl}/api/repos/${owner}/${repo}/pulls`;
  }

  getPullRequests(
    owner: string,
    repo: string,
    status: 'OPEN' | 'MERGED' | 'CLOSED' = 'OPEN',
  ): Promise<PullRequestInfo[]> {
    const params = new HttpParams().set('status', status);
    return firstValueFrom(this.http.get<PullRequestInfo[]>(this.base(owner, repo), { params }));
  }

  getPullRequest(owner: string, repo: string, number: number): Promise<PullRequestDetail> {
    return firstValueFrom(this.http.get<PullRequestDetail>(`${this.base(owner, repo)}/${number}`));
  }

  createPullRequest(
    owner: string,
    repo: string,
    form: {
      title: string;
      description?: string;
      sourceBranch: string;
      targetBranch: string;
      isDraft?: boolean;
    },
  ): Promise<PullRequestDetail> {
    return firstValueFrom(this.http.post<PullRequestDetail>(this.base(owner, repo), form));
  }

  closePullRequest(owner: string, repo: string, number: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(owner, repo)}/${number}`));
  }

  mergePullRequest(
    owner: string,
    repo: string,
    number: number,
    form: { strategy?: 'MERGE_COMMIT' | 'SQUASH' | 'REBASE'; commitMessage?: string },
  ): Promise<PullRequestDetail> {
    return firstValueFrom(this.http.post<PullRequestDetail>(`${this.base(owner, repo)}/${number}/merge`, form));
  }

  getPrCommits(owner: string, repo: string, number: number): Promise<CommitInfo[]> {
    return firstValueFrom(this.http.get<CommitInfo[]>(`${this.base(owner, repo)}/${number}/commits`));
  }

  getPrDiff(owner: string, repo: string, number: number): Promise<FileDiff[]> {
    return firstValueFrom(this.http.get<FileDiff[]>(`${this.base(owner, repo)}/${number}/diff`));
  }

  getComments(owner: string, repo: string, number: number): Promise<PrCommentInfo[]> {
    return firstValueFrom(this.http.get<PrCommentInfo[]>(`${this.base(owner, repo)}/${number}/comments`));
  }

  addComment(
    owner: string,
    repo: string,
    number: number,
    form: {
      body: string;
      filePath?: string;
      commitSha?: string;
      lineNumber?: number;
      lineSide?: string;
    },
  ): Promise<PrCommentInfo> {
    return firstValueFrom(this.http.post<PrCommentInfo>(`${this.base(owner, repo)}/${number}/comments`, form));
  }

  updateComment(
    owner: string,
    repo: string,
    number: number,
    commentId: string,
    form: { body: string },
  ): Promise<PrCommentInfo> {
    return firstValueFrom(
      this.http.patch<PrCommentInfo>(`${this.base(owner, repo)}/${number}/comments/${commentId}`, form),
    );
  }

  deleteComment(owner: string, repo: string, number: number, commentId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(owner, repo)}/${number}/comments/${commentId}`));
  }
}
