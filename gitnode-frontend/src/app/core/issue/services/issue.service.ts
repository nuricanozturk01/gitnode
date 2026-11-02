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
  IssueLabel,
  IssueMilestone,
} from '../../../domain/repository/models/issue.model';

@Injectable({ providedIn: 'root' })
export class IssueService {
  private readonly http = inject(HttpClient);

  private base(owner: string, repo: string): string {
    return `${environment.apiUrl}/api/repos/${owner}/${repo}/issues`;
  }

  getAll(owner: string, repo: string, status: 'OPEN' | 'CLOSED' = 'OPEN', page = 0, labelId = '', milestoneId = ''): Promise<IssuePage> {
    let params = new HttpParams().set('status', status).set('page', String(page));
    if (labelId) params = params.set('labelId', labelId);
    if (milestoneId) params = params.set('milestoneId', milestoneId);
    return firstValueFrom(this.http.get<IssuePage>(this.base(owner, repo), { params }));
  }

  listMilestones(owner: string, repo: string): Promise<IssueMilestone[]> {
    return firstValueFrom(this.http.get<IssueMilestone[]>(`${environment.apiUrl}/api/repos/${owner}/${repo}/milestones`));
  }

  saveMilestone(owner: string, repo: string, form: Omit<IssueMilestone, 'id'>, id?: string): Promise<IssueMilestone> {
    const base = `${environment.apiUrl}/api/repos/${owner}/${repo}/milestones`;
    return firstValueFrom(id ? this.http.put<IssueMilestone>(`${base}/${id}`, form) : this.http.post<IssueMilestone>(base, form));
  }

  deleteMilestone(owner: string, repo: string, id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${environment.apiUrl}/api/repos/${owner}/${repo}/milestones/${id}`));
  }

  listLabels(owner: string, repo: string): Promise<IssueLabel[]> {
    return firstValueFrom(this.http.get<IssueLabel[]>(`${environment.apiUrl}/api/repos/${owner}/${repo}/labels`));
  }

  saveLabel(owner: string, repo: string, form: { name: string; color: string; description: string }, id?: string): Promise<IssueLabel> {
    const base = `${environment.apiUrl}/api/repos/${owner}/${repo}/labels`;
    return firstValueFrom(id ? this.http.put<IssueLabel>(`${base}/${id}`, form) : this.http.post<IssueLabel>(base, form));
  }

  deleteLabel(owner: string, repo: string, id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${environment.apiUrl}/api/repos/${owner}/${repo}/labels/${id}`));
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
