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

import { Injectable, inject, signal, computed } from '@angular/core';
import { TokenService } from '../../../core/auth/services/token.service';
import type { RepoInfo } from '../../../domain/repository/models/repo-info.model';

@Injectable({ providedIn: 'root' })
export class RepoContextService {
  private readonly tokenService = inject(TokenService);

  readonly repo = signal<RepoInfo | null>(null);
  readonly defaultBranch = computed(() => this.repo()?.defaultBranch ?? 'main');

  readonly isLoggedIn = computed(() => this.tokenService.isLoggedIn());

  readonly canEdit = computed(() => {
    const r = this.repo();
    const u = this.tokenService.getUsername();
    return !!(r && u && r.owner.username.toLowerCase() === u.toLowerCase());
  });
}
