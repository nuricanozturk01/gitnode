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
import type { LanguageStats } from '../../../domain/language/models/language-stats.model';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly http = inject(HttpClient);

  getLanguages(owner: string, repo: string, branch: string): Promise<LanguageStats[]> {
    return firstValueFrom(
      this.http.get<LanguageStats[]>(`${environment.apiUrl}/api/repos/${owner}/${repo}/languages`, {
        params: { branch },
      }),
    ).catch(() => []);
  }
}
