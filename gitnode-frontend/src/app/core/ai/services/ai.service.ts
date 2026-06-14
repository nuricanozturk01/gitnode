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
import type {
  UserAiSettings,
  UpdateAiSettingsRequest,
  TestAiConnectionRequest,
  TestAiConnectionResponse,
  CommitSuggestionResponse,
  PrDescriptionResponse,
  AiCodeReview,
  CodebaseAnalysis,
  PageResponse,
} from '../../../domain/ai/ai-settings.model';

@Injectable({ providedIn: 'root' })
export class AiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api`;

  getSettings(): Promise<UserAiSettings> {
    return firstValueFrom(this.http.get<UserAiSettings>(`${this.base}/user/ai/settings`));
  }

  updateSettings(request: UpdateAiSettingsRequest): Promise<UserAiSettings> {
    return firstValueFrom(this.http.put<UserAiSettings>(`${this.base}/user/ai/settings`, request));
  }

  deleteApiKey(): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base}/user/ai/settings/api-key`));
  }

  testConnection(request: TestAiConnectionRequest = {}): Promise<TestAiConnectionResponse> {
    return firstValueFrom(this.http.post<TestAiConnectionResponse>(`${this.base}/user/ai/settings/test`, request));
  }

  suggestCommitMessage(diff: string): Promise<CommitSuggestionResponse> {
    return firstValueFrom(this.http.post<CommitSuggestionResponse>(`${this.base}/ai/suggest/commit-message`, { diff }));
  }

  suggestPrDescription(
    owner: string,
    repo: string,
    sourceBranch: string,
    targetBranch: string,
  ): Promise<PrDescriptionResponse> {
    return firstValueFrom(
      this.http.post<PrDescriptionResponse>(`${this.base}/ai/suggest/pr-description`, {
        owner,
        repo,
        sourceBranch,
        targetBranch,
      }),
    );
  }

  getCodeReview(owner: string, repo: string, prNumber: number, page = 0, size = 20): Promise<AiCodeReview> {
    const params = new HttpParams().set('page', page).set('size', size);
    return firstValueFrom(
      this.http.get<AiCodeReview>(`${this.base}/ai/repos/${owner}/${repo}/pulls/${prNumber}/review`, { params }),
    );
  }

  retryCodeReview(owner: string, repo: string, prNumber: number): Promise<AiCodeReview> {
    return firstValueFrom(
      this.http.post<AiCodeReview>(`${this.base}/ai/repos/${owner}/${repo}/pulls/${prNumber}/review/retry`, null),
    );
  }

  triggerCodebaseAnalysis(owner: string, repo: string, branch?: string): Promise<CodebaseAnalysis> {
    const params = branch ? new HttpParams().set('branch', branch) : new HttpParams();
    return firstValueFrom(
      this.http.post<CodebaseAnalysis>(`${this.base}/ai/repos/${owner}/${repo}/analysis`, null, { params }),
    );
  }

  getLatestAnalysis(owner: string, repo: string): Promise<CodebaseAnalysis | null> {
    return firstValueFrom(this.http.get<CodebaseAnalysis>(`${this.base}/ai/repos/${owner}/${repo}/analysis`)).catch(
      () => null,
    );
  }

  listAnalyses(owner: string, repo: string, page = 0, size = 10): Promise<PageResponse<CodebaseAnalysis>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return firstValueFrom(
      this.http.get<PageResponse<CodebaseAnalysis>>(`${this.base}/ai/repos/${owner}/${repo}/analysis/history`, {
        params,
      }),
    );
  }
}
