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
  SnippetDetail,
  SnippetForm,
  SnippetInfo,
  SnippetPage,
  SnippetUpdateForm,
  SnippetCommentInfo,
  SnippetCommentPage,
  SnippetRevisionPage,
  SnippetRevisionDetail,
} from '../../../domain/snippet/models/snippet.model';

@Injectable({ providedIn: 'root' })
export class SnippetService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/snippets`;

  listPublic(page = 0, size = 20, q?: string): Promise<SnippetPage> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    return firstValueFrom(this.http.get<SnippetPage>(this.base, { params }));
  }

  listMine(): Promise<SnippetInfo[]> {
    return firstValueFrom(this.http.get<SnippetInfo[]>(`${this.base}/me`));
  }

  get(id: string): Promise<SnippetDetail> {
    return firstValueFrom(this.http.get<SnippetDetail>(`${this.base}/${id}`));
  }

  create(form: SnippetForm): Promise<SnippetDetail> {
    return firstValueFrom(this.http.post<SnippetDetail>(this.base, form));
  }

  update(id: string, form: SnippetUpdateForm): Promise<SnippetDetail> {
    return firstValueFrom(this.http.patch<SnippetDetail>(`${this.base}/${id}`, form));
  }

  delete(id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base}/${id}`));
  }

  fork(id: string): Promise<SnippetDetail> {
    return firstValueFrom(this.http.post<SnippetDetail>(`${this.base}/${id}/fork`, {}));
  }

  listRevisions(id: string, page = 0, size = 10): Promise<SnippetRevisionPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return firstValueFrom(this.http.get<SnippetRevisionPage>(`${this.base}/${id}/revisions`, { params }));
  }

  getRevision(id: string, revisionId: string): Promise<SnippetRevisionDetail> {
    return firstValueFrom(this.http.get<SnippetRevisionDetail>(`${this.base}/${id}/revisions/${revisionId}`));
  }

  listComments(id: string, page = 0, size = 10): Promise<SnippetCommentPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return firstValueFrom(this.http.get<SnippetCommentPage>(`${this.base}/${id}/comments`, { params }));
  }

  addComment(id: string, body: string): Promise<SnippetCommentInfo> {
    return firstValueFrom(this.http.post<SnippetCommentInfo>(`${this.base}/${id}/comments`, { body }));
  }

  deleteComment(id: string, commentId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base}/${id}/comments/${commentId}`));
  }

  listByOwner(username: string, page = 0, size = 20): Promise<SnippetPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return firstValueFrom(this.http.get<SnippetPage>(`${this.base}/by-owner/${username}`, { params }));
  }

  linkRepo(snippetId: string, repoId: string): Promise<SnippetDetail> {
    return firstValueFrom(this.http.put<SnippetDetail>(`${this.base}/${snippetId}/repo/${repoId}`, {}));
  }

  unlinkRepo(snippetId: string): Promise<SnippetDetail> {
    return firstValueFrom(this.http.delete<SnippetDetail>(`${this.base}/${snippetId}/repo`));
  }

  listByRepo(owner: string, repoName: string, page = 0, size = 20): Promise<SnippetPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return firstValueFrom(this.http.get<SnippetPage>(`${this.base}/repo/${owner}/${repoName}`, { params }));
  }

  rawFileUrl(id: string, fileId: string): string {
    return `${this.base}/${id}/files/${fileId}/raw`;
  }
}
