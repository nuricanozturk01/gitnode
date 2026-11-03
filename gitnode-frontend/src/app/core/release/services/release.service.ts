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
import { HttpClient, HttpEventType, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { ReleaseAssetInfo, ReleaseInfo } from '../../../domain/release/models/release-info.model';
import type { CreateReleaseForm } from '../../../domain/release/models/create-release-form.model';
import type { UpdateReleaseForm } from '../../../domain/release/models/update-release-form.model';

@Injectable({ providedIn: 'root' })
export class ReleaseService {
  private readonly http = inject(HttpClient);

  private base(owner: string, repo: string): string {
    return `${environment.apiUrl}/api/repos/${owner}/${repo}/releases`;
  }

  getAll(owner: string, repo: string): Promise<ReleaseInfo[]> {
    return firstValueFrom(this.http.get<ReleaseInfo[]>(this.base(owner, repo)));
  }

  getLatest(owner: string, repo: string): Promise<ReleaseInfo | null> {
    return firstValueFrom(this.http.get<ReleaseInfo>(`${this.base(owner, repo)}/latest`)).catch(() => null);
  }

  getByTag(owner: string, repo: string, tagName: string): Promise<ReleaseInfo> {
    return firstValueFrom(this.http.get<ReleaseInfo>(`${this.base(owner, repo)}/tag/${tagName}`));
  }

  getById(owner: string, repo: string, id: string): Promise<ReleaseInfo> {
    return firstValueFrom(this.http.get<ReleaseInfo>(`${this.base(owner, repo)}/${id}`));
  }

  generateNotes(
    owner: string,
    repo: string,
    tagName: string,
    targetCommitish?: string,
  ): Promise<{ name: string; body: string; previousTag: string | null; commitCount: number; truncated: boolean }> {
    let params = new HttpParams().set('tagName', tagName);
    if (targetCommitish) params = params.set('targetCommitish', targetCommitish);
    return firstValueFrom(
      this.http.get<{
        name: string;
        body: string;
        previousTag: string | null;
        commitCount: number;
        truncated: boolean;
      }>(`${this.base(owner, repo)}/generate-notes`, { params }),
    );
  }

  create(owner: string, repo: string, form: CreateReleaseForm): Promise<ReleaseInfo> {
    return firstValueFrom(this.http.post<ReleaseInfo>(this.base(owner, repo), form));
  }

  update(owner: string, repo: string, id: string, form: UpdateReleaseForm): Promise<ReleaseInfo> {
    return firstValueFrom(this.http.patch<ReleaseInfo>(`${this.base(owner, repo)}/${id}`, form));
  }

  listAssets(owner: string, repo: string, releaseId: string): Promise<ReleaseAssetInfo[]> {
    return firstValueFrom(this.http.get<ReleaseAssetInfo[]>(`${this.base(owner, repo)}/${releaseId}/assets`));
  }

  /** Uploads one file; {@code onProgress} receives 0-100 while the bytes are sent. */
  uploadAsset(
    owner: string,
    repo: string,
    releaseId: string,
    file: File,
    onProgress?: (percent: number) => void,
  ): Promise<ReleaseAssetInfo> {
    const form = new FormData();
    form.append('file', file, file.name);
    return new Promise<ReleaseAssetInfo>((resolve, reject) => {
      this.http
        .post<ReleaseAssetInfo>(`${this.base(owner, repo)}/${releaseId}/assets`, form, {
          observe: 'events',
          reportProgress: true,
        })
        .subscribe({
          next: (event) => {
            if (event.type === HttpEventType.UploadProgress && event.total) {
              onProgress?.(Math.round((event.loaded / event.total) * 100));
            } else if (event.type === HttpEventType.Response && event.body) {
              resolve(event.body);
            }
          },
          error: reject,
        });
    });
  }

  downloadAsset(owner: string, repo: string, releaseId: string, assetId: string): Promise<Blob> {
    return firstValueFrom(
      this.http.get(`${this.base(owner, repo)}/${releaseId}/assets/${assetId}`, { responseType: 'blob' }),
    );
  }

  deleteAsset(owner: string, repo: string, releaseId: string, assetId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(owner, repo)}/${releaseId}/assets/${assetId}`));
  }

  markLatest(owner: string, repo: string, id: string): Promise<ReleaseInfo> {
    return firstValueFrom(this.http.post<ReleaseInfo>(`${this.base(owner, repo)}/${id}/latest`, null));
  }

  delete(owner: string, repo: string, id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(owner, repo)}/${id}`));
  }
}
