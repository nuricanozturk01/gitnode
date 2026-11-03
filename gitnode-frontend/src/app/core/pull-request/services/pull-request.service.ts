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
import type {
  PrConflictInfo,
  PrConflictResolveForm,
  PrMergeRequirements,
  PrMetadata,
} from '../../../domain/pull-request/models/pr-conflict.model';
import type { PullRequestDetail } from '../../../domain/pull-request/models/pull-request-detail.model';
import type { PrListFilter, PrPage } from '../../../domain/pull-request/models/pull-request-info.model';
import type { PrCommentInfo, PrCommentPage } from '../../../domain/pull-request/models/pr-comment-info.model';

export interface PrReviewer {
  username: string;
  displayName: string | null;
  avatarUrl: string | null;
  canMerge: boolean;
}

export interface PrReview {
  id: string;
  username: string;
  state: 'APPROVED' | 'CHANGES_REQUESTED' | 'COMMENTED';
  body: string | null;
  commitSha: string;
  targetSha: string | null;
  createdAt: string | null;
}

export interface PrReviews {
  headSha: string | null;
  targetSha: string | null;
  reviews: PrReview[];
}

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
    page = 0,
    size = 25,
    filter: PrListFilter = {},
  ): Promise<PrPage> {
    let params = new HttpParams().set('status', status).set('page', page).set('size', size);
    if (filter.labelId) params = params.set('labelId', filter.labelId);
    if (filter.milestoneId) params = params.set('milestoneId', filter.milestoneId);
    if (filter.assignee?.trim()) params = params.set('assignee', filter.assignee.trim());
    return firstValueFrom(this.http.get<PrPage>(this.base(owner, repo), { params }));
  }

  getPullRequest(owner: string, repo: string, number: number): Promise<PullRequestDetail> {
    return firstValueFrom(this.http.get<PullRequestDetail>(`${this.base(owner, repo)}/${number}`));
  }

  getReviewers(owner: string, repo: string, number: number): Promise<PrReviewer[]> {
    return firstValueFrom(this.http.get<PrReviewer[]>(`${this.base(owner, repo)}/${number}/reviewers`));
  }

  setReviewers(owner: string, repo: string, number: number, usernames: string[]): Promise<PrReviewer[]> {
    return firstValueFrom(this.http.put<PrReviewer[]>(`${this.base(owner, repo)}/${number}/reviewers`, { usernames }));
  }

  getReviews(owner: string, repo: string, number: number): Promise<PrReviews> {
    return firstValueFrom(this.http.get<PrReviews>(`${this.base(owner, repo)}/${number}/reviews`));
  }

  submitReview(
    owner: string,
    repo: string,
    number: number,
    form: { state: PrReview['state']; body: string; expectedSha: string; expectedTargetSha: string },
  ): Promise<PrReview> {
    return firstValueFrom(this.http.post<PrReview>(`${this.base(owner, repo)}/${number}/reviews`, form));
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

  setCommentResolved(
    owner: string,
    repo: string,
    number: number,
    commentId: string,
    resolved: boolean,
  ): Promise<PrCommentInfo> {
    return firstValueFrom(
      this.http.put<PrCommentInfo>(`${this.base(owner, repo)}/${number}/comments/${commentId}/resolved`, { resolved }),
    );
  }

  getMetadata(owner: string, repo: string, number: number): Promise<PrMetadata> {
    return firstValueFrom(this.http.get<PrMetadata>(`${this.base(owner, repo)}/${number}/metadata`));
  }

  saveMetadata(
    owner: string,
    repo: string,
    number: number,
    form: { labelIds: string[]; assignees: string[]; milestoneId: string | null },
  ): Promise<PrMetadata> {
    return firstValueFrom(this.http.put<PrMetadata>(`${this.base(owner, repo)}/${number}/metadata`, form));
  }

  getMergeRequirements(owner: string, repo: string, number: number): Promise<PrMergeRequirements> {
    return firstValueFrom(this.http.get<PrMergeRequirements>(`${this.base(owner, repo)}/${number}/merge-requirements`));
  }

  getConflicts(owner: string, repo: string, number: number): Promise<PrConflictInfo> {
    return firstValueFrom(this.http.get<PrConflictInfo>(`${this.base(owner, repo)}/${number}/conflicts`));
  }

  resolveConflicts(
    owner: string,
    repo: string,
    number: number,
    form: PrConflictResolveForm,
  ): Promise<{ commitSha: string }> {
    return firstValueFrom(
      this.http.post<{ commitSha: string }>(`${this.base(owner, repo)}/${number}/conflicts/resolve`, form),
    );
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

  getComments(owner: string, repo: string, number: number, page = 0, size = 50): Promise<PrCommentPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return firstValueFrom(this.http.get<PrCommentPage>(`${this.base(owner, repo)}/${number}/comments`, { params }));
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
      parentId?: string;
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

  reopenPullRequest(owner: string, repo: string, number: number): Promise<PullRequestDetail> {
    return firstValueFrom(this.http.post<PullRequestDetail>(`${this.base(owner, repo)}/${number}/reopen`, null));
  }

  updatePullRequest(
    owner: string,
    repo: string,
    number: number,
    form: { title?: string; description?: string; isDraft?: boolean },
  ): Promise<PullRequestDetail> {
    return firstValueFrom(this.http.patch<PullRequestDetail>(`${this.base(owner, repo)}/${number}`, form));
  }
}
