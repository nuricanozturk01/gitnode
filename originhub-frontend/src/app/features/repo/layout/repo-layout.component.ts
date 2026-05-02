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

import { Component, inject, signal, computed } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, RouterLinkActive, RouterOutlet, ActivatedRoute } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { RepoService } from '../../../core/repo/services/repo.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { PullRequestService } from '../../../core/pull-request/services/pull-request.service';
import type { RepoInfo } from '../../../domain/repository/models/repo-info.model';

@Component({
  selector: 'app-repo-layout',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, LucideAngularModule],
  templateUrl: './repo-layout.component.html',
  styleUrl: './repo-layout.component.css',
})
export class RepoLayoutComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly repoService = inject(RepoService);
  readonly repoContext = inject(RepoContextService);
  private readonly prService = inject(PullRequestService);

  readonly repo = signal<RepoInfo | null>(null);
  readonly loading = signal(true);

  readonly owner = computed(() => this.route.snapshot.paramMap.get('owner') ?? '');
  readonly repoName = computed(() => this.route.snapshot.paramMap.get('repo') ?? '');
  readonly prCount = signal(0);

  constructor() {
    this.route.params.pipe(takeUntilDestroyed()).subscribe(() => this.loadRepo());
  }

  private async loadRepo(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) return;
    this.loading.set(true);
    try {
      const [repoData, prList] = await Promise.all([
        this.repoService.getRepo(owner, repo),
        this.prService.getPullRequests(owner, repo, 'OPEN').catch(() => []),
      ]);
      this.repo.set(repoData);
      this.repoContext.repo.set(repoData);
      this.prCount.set(prList.length);
    } catch {
      this.repo.set(null);
      this.repoContext.repo.set(null);
    } finally {
      this.loading.set(false);
    }
  }
}
