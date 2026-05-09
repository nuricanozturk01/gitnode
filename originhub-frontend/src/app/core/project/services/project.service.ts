import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { ProjectInfo, ProjectForm, ProjectUpdateForm } from '../../../domain/project/models/project-info.model';
import type { ProjectRepoInfo } from '../../../domain/project/models/project-repo-info.model';

@Injectable({ providedIn: 'root' })
export class ProjectService {
  private readonly http = inject(HttpClient);

  private base(owner: string): string {
    return `${environment.apiUrl}/api/projects/${owner}`;
  }

  getAll(owner: string): Promise<ProjectInfo[]> {
    return firstValueFrom(this.http.get<ProjectInfo[]>(this.base(owner)));
  }

  get(owner: string, projectCode: string): Promise<ProjectInfo> {
    return firstValueFrom(this.http.get<ProjectInfo>(`${this.base(owner)}/${projectCode}`));
  }

  create(owner: string, form: ProjectForm): Promise<ProjectInfo> {
    return firstValueFrom(this.http.post<ProjectInfo>(this.base(owner), form));
  }

  update(owner: string, projectCode: string, form: ProjectUpdateForm): Promise<ProjectInfo> {
    return firstValueFrom(this.http.patch<ProjectInfo>(`${this.base(owner)}/${projectCode}`, form));
  }

  delete(owner: string, projectCode: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(owner)}/${projectCode}`));
  }

  linkRepo(owner: string, projectCode: string, repoId: string): Promise<void> {
    return firstValueFrom(this.http.post<void>(`${this.base(owner)}/${projectCode}/repos/${repoId}`, {}));
  }

  unlinkRepo(owner: string, projectCode: string, repoId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(owner)}/${projectCode}/repos/${repoId}`));
  }

  getLinkedRepos(owner: string, projectCode: string): Promise<ProjectRepoInfo[]> {
    return firstValueFrom(this.http.get<ProjectRepoInfo[]>(`${this.base(owner)}/${projectCode}/repos`));
  }
}
