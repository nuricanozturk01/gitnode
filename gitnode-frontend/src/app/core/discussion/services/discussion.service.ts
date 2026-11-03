import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type {
  DiscussionCategory,
  DiscussionComment,
  DiscussionDetail,
  DiscussionInfo,
  DiscussionListFilter,
  DiscussionPage,
} from '../../../domain/discussion/models/discussion.model';

@Injectable({ providedIn: 'root' })
export class DiscussionService {
  private readonly http = inject(HttpClient);

  private base(owner: string, repo: string): string {
    return `${environment.apiUrl}/api/repos/${owner}/${repo}/discussions`;
  }

  list(owner: string, repo: string, filter: DiscussionListFilter): Promise<DiscussionPage<DiscussionInfo>> {
    let params = new HttpParams()
      .set('sort', filter.sort)
      .set('page', String(filter.page))
      .set('unanswered', String(filter.unanswered));
    if (filter.category) params = params.set('category', filter.category);
    return firstValueFrom(this.http.get<DiscussionPage<DiscussionInfo>>(this.base(owner, repo), { params }));
  }

  create(
    owner: string,
    repo: string,
    form: { title: string; body: string; category: DiscussionCategory },
  ): Promise<DiscussionDetail> {
    return firstValueFrom(this.http.post<DiscussionDetail>(this.base(owner, repo), form));
  }

  get(owner: string, repo: string, number: number): Promise<DiscussionDetail> {
    return firstValueFrom(this.http.get<DiscussionDetail>(`${this.base(owner, repo)}/${number}`));
  }

  update(
    owner: string,
    repo: string,
    number: number,
    form: { title?: string; body?: string; category?: DiscussionCategory },
  ): Promise<DiscussionDetail> {
    return firstValueFrom(this.http.patch<DiscussionDetail>(`${this.base(owner, repo)}/${number}`, form));
  }

  delete(owner: string, repo: string, number: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(owner, repo)}/${number}`));
  }

  setLocked(owner: string, repo: string, number: number, locked: boolean): Promise<DiscussionDetail> {
    return firstValueFrom(this.http.put<DiscussionDetail>(`${this.base(owner, repo)}/${number}/lock`, { locked }));
  }

  setUpvote(
    owner: string,
    repo: string,
    number: number,
    upvoted: boolean,
  ): Promise<{ upvotes: number; upvoted: boolean }> {
    const url = `${this.base(owner, repo)}/${number}/upvote`;
    return firstValueFrom(
      upvoted
        ? this.http.put<{ upvotes: number; upvoted: boolean }>(url, {})
        : this.http.delete<{ upvotes: number; upvoted: boolean }>(url),
    );
  }

  setAnswer(owner: string, repo: string, number: number, commentId: string | null): Promise<DiscussionDetail> {
    return firstValueFrom(this.http.put<DiscussionDetail>(`${this.base(owner, repo)}/${number}/answer`, { commentId }));
  }

  comments(owner: string, repo: string, number: number, page: number): Promise<DiscussionPage<DiscussionComment>> {
    const params = new HttpParams().set('page', String(page));
    return firstValueFrom(
      this.http.get<DiscussionPage<DiscussionComment>>(`${this.base(owner, repo)}/${number}/comments`, { params }),
    );
  }

  replies(
    owner: string,
    repo: string,
    number: number,
    commentId: string,
    page: number,
  ): Promise<DiscussionPage<DiscussionComment>> {
    const params = new HttpParams().set('page', String(page));
    return firstValueFrom(
      this.http.get<DiscussionPage<DiscussionComment>>(
        `${this.base(owner, repo)}/${number}/comments/${commentId}/replies`,
        { params },
      ),
    );
  }

  addComment(
    owner: string,
    repo: string,
    number: number,
    body: string,
    parentId: string | null,
  ): Promise<DiscussionComment> {
    return firstValueFrom(
      this.http.post<DiscussionComment>(`${this.base(owner, repo)}/${number}/comments`, { body, parentId }),
    );
  }

  updateComment(
    owner: string,
    repo: string,
    number: number,
    commentId: string,
    body: string,
  ): Promise<DiscussionComment> {
    return firstValueFrom(
      this.http.patch<DiscussionComment>(`${this.base(owner, repo)}/${number}/comments/${commentId}`, { body }),
    );
  }

  deleteComment(owner: string, repo: string, number: number, commentId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(owner, repo)}/${number}/comments/${commentId}`));
  }
}
