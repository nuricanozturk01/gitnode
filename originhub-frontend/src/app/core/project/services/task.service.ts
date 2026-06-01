import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { BranchInfo } from '../../../domain/repository/models/branch-info.model';
import type {
  TaskPage,
  TaskDetail,
  TaskForm,
  TaskUpdateForm,
  SubtaskInfo,
  SubtaskForm,
  SubtaskUpdateForm,
  CreateBranchFromTaskForm,
} from '../../../domain/project/models/task.model';

@Injectable({ providedIn: 'root' })
export class TaskService {
  private readonly http = inject(HttpClient);

  private base(owner: string, projectCode: string): string {
    return `${environment.apiUrl}/api/projects/${owner}/${projectCode}/tasks`;
  }

  getAll(owner: string, projectCode: string, page = 0, size = 500): Promise<TaskPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return firstValueFrom(this.http.get<TaskPage>(this.base(owner, projectCode), { params }));
  }

  get(owner: string, projectCode: string, taskCode: string): Promise<TaskDetail> {
    return firstValueFrom(this.http.get<TaskDetail>(`${this.base(owner, projectCode)}/${taskCode}`));
  }

  create(owner: string, projectCode: string, form: TaskForm): Promise<TaskDetail> {
    return firstValueFrom(this.http.post<TaskDetail>(this.base(owner, projectCode), form));
  }

  update(owner: string, projectCode: string, taskCode: string, form: TaskUpdateForm): Promise<TaskDetail> {
    return firstValueFrom(this.http.patch<TaskDetail>(`${this.base(owner, projectCode)}/${taskCode}`, form));
  }

  delete(owner: string, projectCode: string, taskCode: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(owner, projectCode)}/${taskCode}`));
  }

  createBranch(
    owner: string,
    projectCode: string,
    taskCode: string,
    form: CreateBranchFromTaskForm,
  ): Promise<BranchInfo> {
    return firstValueFrom(this.http.post<BranchInfo>(`${this.base(owner, projectCode)}/${taskCode}/branch`, form));
  }

  createBranchForSubtask(
    owner: string,
    projectCode: string,
    taskCode: string,
    subtaskId: string,
    form: CreateBranchFromTaskForm,
  ): Promise<BranchInfo> {
    return firstValueFrom(
      this.http.post<BranchInfo>(`${this.base(owner, projectCode)}/${taskCode}/subtasks/${subtaskId}/branch`, form),
    );
  }

  createSubtask(owner: string, projectCode: string, taskCode: string, form: SubtaskForm): Promise<SubtaskInfo> {
    return firstValueFrom(this.http.post<SubtaskInfo>(`${this.base(owner, projectCode)}/${taskCode}/subtasks`, form));
  }

  updateSubtask(
    owner: string,
    projectCode: string,
    taskCode: string,
    subtaskId: string,
    form: SubtaskUpdateForm,
  ): Promise<SubtaskInfo> {
    return firstValueFrom(
      this.http.patch<SubtaskInfo>(`${this.base(owner, projectCode)}/${taskCode}/subtasks/${subtaskId}`, form),
    );
  }

  deleteSubtask(owner: string, projectCode: string, taskCode: string, subtaskId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(owner, projectCode)}/${taskCode}/subtasks/${subtaskId}`));
  }
}
