import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type {
  IssuePage,
  IssueDetail,
  IssueForm,
  IssueUpdateForm,
  IssueCommentInfo,
  IssueCommentForm,
  IssueCommentUpdateForm,
  IssueCommentPage,
  IssueLinkedTaskInfo,
} from '../../../domain/repository/models/issue.model';

@Injectable({ providedIn: 'root' })
export class IssueService {
  private readonly http = inject(HttpClient);

  private base(owner: string, repo: string): string {
    return `${environment.apiUrl}/api/repos/${owner}/${repo}/issues`;
  }

  getAll(owner: string, repo: string, status: 'OPEN' | 'CLOSED' = 'OPEN', page = 0): Promise<IssuePage> {
    const params = new HttpParams().set('status', status).set('page', String(page));
    return firstValueFrom(this.http.get<IssuePage>(this.base(owner, repo), { params }));
  }

  get(owner: string, repo: string, number: number): Promise<IssueDetail> {
    return firstValueFrom(this.http.get<IssueDetail>(`${this.base(owner, repo)}/${number}`));
  }

  create(owner: string, repo: string, form: IssueForm): Promise<IssueDetail> {
    return firstValueFrom(this.http.post<IssueDetail>(this.base(owner, repo), form));
  }

  update(owner: string, repo: string, number: number, form: IssueUpdateForm): Promise<IssueDetail> {
    return firstValueFrom(this.http.patch<IssueDetail>(`${this.base(owner, repo)}/${number}`, form));
  }

  delete(owner: string, repo: string, number: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(owner, repo)}/${number}`));
  }

  getComments(owner: string, repo: string, number: number, page = 0): Promise<IssueCommentPage> {
    const params = new HttpParams().set('page', String(page));
    return firstValueFrom(this.http.get<IssueCommentPage>(`${this.base(owner, repo)}/${number}/comments`, { params }));
  }

  addComment(owner: string, repo: string, number: number, form: IssueCommentForm): Promise<IssueCommentInfo> {
    return firstValueFrom(this.http.post<IssueCommentInfo>(`${this.base(owner, repo)}/${number}/comments`, form));
  }

  updateComment(
    owner: string,
    repo: string,
    number: number,
    commentId: string,
    form: IssueCommentUpdateForm,
  ): Promise<IssueCommentInfo> {
    return firstValueFrom(
      this.http.patch<IssueCommentInfo>(`${this.base(owner, repo)}/${number}/comments/${commentId}`, form),
    );
  }

  deleteComment(owner: string, repo: string, number: number, commentId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(owner, repo)}/${number}/comments/${commentId}`));
  }

  getLinkedTasks(owner: string, repo: string, number: number): Promise<IssueLinkedTaskInfo[]> {
    return firstValueFrom(this.http.get<IssueLinkedTaskInfo[]>(`${this.base(owner, repo)}/${number}/linked-tasks`));
  }
}
