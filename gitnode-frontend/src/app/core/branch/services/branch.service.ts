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
import type { BranchInfo } from '../../../domain/repository/models/branch-info.model';
import type { CreateBranchForm } from '../../../domain/repository/models/create-branch-form.model';
import type { SetDefaultBranchForm } from '../../../domain/repository/models/set-default-branch-form.model';

@Injectable({ providedIn: 'root' })
export class BranchService {
  private readonly http = inject(HttpClient);

  private base(owner: string, repo: string): string {
    return `${environment.apiUrl}/api/repos/${owner}/${repo}/branches`;
  }

  getAll(owner: string, repo: string): Promise<BranchInfo[]> {
    return firstValueFrom(this.http.get<BranchInfo[]>(this.base(owner, repo)));
  }

  createBranch(owner: string, repo: string, form: CreateBranchForm): Promise<BranchInfo> {
    return firstValueFrom(this.http.post<BranchInfo>(this.base(owner, repo), form));
  }

  deleteBranch(owner: string, repo: string, branch: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(owner, repo)}/${branch}`));
  }

  setDefaultBranch(owner: string, repo: string, form: SetDefaultBranchForm): Promise<void> {
    return firstValueFrom(this.http.patch<void>(`${this.base(owner, repo)}/default`, form));
  }
}
