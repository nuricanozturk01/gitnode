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
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { RepoForm } from '../../../domain/repository/models/repo-form.model';
import type { RepoInfo } from '../../../domain/repository/models/repo-info.model';
import type { RepoPage } from '../../../domain/repository/models/repo-page.model';

@Injectable({ providedIn: 'root' })
export class RepoService {
  private readonly http = inject(HttpClient);

  private readonly api = `${environment.apiUrl}/api/repo`;

  create(form: RepoForm): Promise<RepoInfo> {
    const body = this.toCreateBody(form);
    return firstValueFrom(this.http.post<RepoInfo>(this.api, body)).then((r) => this.normalizeRepo(r));
  }

  getRepo(owner: string, repo: string): Promise<RepoInfo> {
    return firstValueFrom(this.http.get<RepoInfo>(`${this.api}/${owner}/${repo}`)).then((r) => this.normalizeRepo(r));
  }

  listUserRepos(owner: string, page = 0, size?: number): Promise<RepoPage> {
    const params: Record<string, string> = { page: String(page) };
    if (size != null) {
      params['size'] = String(size);
    }
    return firstValueFrom(this.http.get<RepoPage>(`${this.api}/${owner}`, { params })).then((page) => ({
      ...page,
      content: page.content.map((r) => this.normalizeRepo(r as RepoInfo & { private?: boolean })),
    }));
  }

  /**
   * No backend route exists yet; avoids calling a non-existent URL. Use {@link listUserRepos} for the signed-in user's
   * repositories.
   */
  listCollaboratorRepos(): Promise<RepoInfo[]> {
    return Promise.resolve([]);
  }

  update(owner: string, repo: string, form: RepoForm): Promise<RepoInfo> {
    const body: Record<string, unknown> = {
      name: form.name,
      description: form.description ?? null,
      topics: form.topics ?? [],
    };
    if (form.isPrivate !== undefined) {
      body['isPrivate'] = form.isPrivate;
    }
    if (form.deleteHeadBranchOnPrMerge !== undefined) {
      body['deleteHeadBranchOnPrMerge'] = form.deleteHeadBranchOnPrMerge;
    }
    if (form.deleteHeadBranchOnPrClose !== undefined) {
      body['deleteHeadBranchOnPrClose'] = form.deleteHeadBranchOnPrClose;
    }
    if (form.aiPrReviewEnabled !== undefined) {
      body['aiPrReviewEnabled'] = form.aiPrReviewEnabled;
    }
    return firstValueFrom(this.http.patch<RepoInfo>(`${this.api}/${owner}/${repo}`, body)).then((r) =>
      this.normalizeRepo(r),
    );
  }

  delete(owner: string, repo: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.api}/${owner}/${repo}`));
  }

  fork(owner: string, repo: string): Promise<RepoInfo> {
    return firstValueFrom(this.http.post<RepoInfo>(`${this.api}/${owner}/${repo}/fork`, {})).then((r) =>
      this.normalizeRepo(r),
    );
  }

  /** Normalize visibility; prefer explicit `isPrivate`, then Jackson's `private` bean name. */
  private normalizeRepo(raw: RepoInfo & { private?: boolean }): RepoInfo {
    let isPrivate: boolean | undefined;
    if (typeof raw.isPrivate === 'boolean') {
      isPrivate = raw.isPrivate;
    } else if (typeof raw.private === 'boolean') {
      isPrivate = raw.private;
    }
    return {
      ...raw,
      ...(isPrivate === undefined ? {} : { isPrivate }),
      aiPrReviewEnabled: raw.aiPrReviewEnabled === true,
      deleteHeadBranchOnPrMerge: raw.deleteHeadBranchOnPrMerge === true,
      deleteHeadBranchOnPrClose: raw.deleteHeadBranchOnPrClose === true,
    };
  }

  private toCreateBody(form: RepoForm): Record<string, unknown> {
    const body: Record<string, unknown> = {
      name: form.name,
      description: form.description ?? null,
      isPrivate: form.isPrivate ?? true,
    };
    if (form.topics && form.topics.length > 0) {
      body['topics'] = form.topics;
    }
    return body;
  }
}
