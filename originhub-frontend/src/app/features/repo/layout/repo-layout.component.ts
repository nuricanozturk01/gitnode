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
import { RouterLink, RouterLinkActive, RouterOutlet, ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { LucideAngularModule } from 'lucide-angular';
import { paramMapSignal } from '../../../core/repo/utils/route-param-signals';
import { RepoService } from '../../../core/repo/services/repo.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { PullRequestService } from '../../../core/pull-request/services/pull-request.service';
import { IssueService } from '../../../core/issue/services/issue.service';
import { ReleaseService } from '../../../core/release/services/release.service';
import { TokenService } from '../../../core/auth/services/token.service';

@Component({
  selector: 'app-repo-layout',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, LucideAngularModule],
  templateUrl: './repo-layout.component.html',
  styleUrl: './repo-layout.component.css',
})
export class RepoLayoutComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly repoService = inject(RepoService);
  readonly repoContext = inject(RepoContextService);
  private readonly prService = inject(PullRequestService);
  private readonly issueService = inject(IssueService);
  private readonly releaseService = inject(ReleaseService);
  private readonly tokenService = inject(TokenService);

  readonly loading = signal(true);
  /** Shared with settings and child routes — updates when visibility or metadata changes. */
  readonly repo = this.repoContext.repo;

  private readonly routeParams = paramMapSignal(this.route);
  readonly owner = computed(() => this.routeParams().get('owner') ?? '');
  readonly repoName = computed(() => this.routeParams().get('repo') ?? '');
  readonly prCount = signal(0);
  readonly issueCount = signal(0);
  readonly releaseCount = signal(0);

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe(() => void this.loadRepo());
  }

  private async loadRepo(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) {
      this.loading.set(false);
      this.repoContext.repo.set(null);
      return;
    }
    this.loading.set(true);
    try {
      const [repoData, prList, issueList, releaseList] = await Promise.all([
        this.repoService.getRepo(owner, repo),
        this.prService.getPullRequests(owner, repo, 'OPEN').catch(() => []),
        this.issueService
          .getAll(owner, repo, 'OPEN')
          .catch(() => ({ content: [], number: 0, size: 0, totalElements: 0, totalPages: 0 })),
        this.releaseService.getAll(owner, repo).catch(() => []),
      ]);
      this.repoContext.repo.set(repoData);
      this.prCount.set(prList.length);
      this.issueCount.set(issueList.totalElements);
      this.releaseCount.set(releaseList.length);
    } catch (err) {
      if (err instanceof HttpErrorResponse && (err.status === 401 || err.status === 403)) {
        if (!this.tokenService.getAccessToken()) {
          this.router.navigate(['/login']);
          return;
        }
      }
      this.repoContext.repo.set(null);
    } finally {
      this.loading.set(false);
    }
  }
}
