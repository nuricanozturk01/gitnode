import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { FileComment, FileCommentPage } from '../../../domain/file-comment/file-comment.model';

@Injectable({ providedIn: 'root' })
export class FileCommentService {
  private readonly http = inject(HttpClient);

  private base(owner: string, repo: string): string {
    return `${environment.apiUrl}/api/repos/${owner}/${repo}/file-comments`;
  }

  list(owner: string, repo: string, branch: string, path: string): Promise<FileCommentPage> {
    const params = new HttpParams().set('branch', branch).set('path', path);
    return firstValueFrom(this.http.get<FileCommentPage>(this.base(owner, repo), { params }));
  }

  create(
    owner: string,
    repo: string,
    form: { branch: string; path: string; line?: number | null; body: string; parentId?: string },
  ): Promise<FileComment> {
    return firstValueFrom(this.http.post<FileComment>(this.base(owner, repo), form));
  }

  update(owner: string, repo: string, id: string, body: string): Promise<FileComment> {
    return firstValueFrom(this.http.patch<FileComment>(`${this.base(owner, repo)}/${id}`, { body }));
  }

  delete(owner: string, repo: string, id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(owner, repo)}/${id}`));
  }
}
