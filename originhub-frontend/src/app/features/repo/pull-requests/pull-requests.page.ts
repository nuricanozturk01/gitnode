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

import { Component, inject, signal, computed, OnInit, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { PullRequestService } from '../../../core/pull-request/services/pull-request.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import type { PullRequestInfo } from '../../../domain/pull-request/models/pull-request-info.model';

@Component({
  selector: 'app-pull-requests',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, RelativeTimePipe],
  templateUrl: './pull-requests.page.html',
})
export class PullRequestsPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly prService = inject(PullRequestService);
  private readonly destroyRef = inject(DestroyRef);
  readonly repoContext = inject(RepoContextService);

  readonly pulls = signal<PullRequestInfo[]>([]);
  readonly loading = signal(true);
  readonly tab = signal<'open' | 'closed' | 'merged'>('open');

  private readonly repoRouteParams = parentParamMapSignal(this.route);
  readonly owner = computed(() => this.repoRouteParams().get('owner') ?? '');
  readonly repoName = computed(() => this.repoRouteParams().get('repo') ?? '');

  readonly openCount = computed(() => this.pulls().filter((p) => p.status === 'OPEN').length);
  readonly closedCount = computed(() => this.pulls().filter((p) => p.status === 'CLOSED').length);
  readonly mergedCount = computed(() => this.pulls().filter((p) => p.status === 'MERGED').length);

  readonly filteredPulls = computed(() => {
    const statusMap: Record<string, string> = { open: 'OPEN', closed: 'CLOSED', merged: 'MERGED' };
    return this.pulls().filter((pr) => pr.status === statusMap[this.tab()]);
  });

  ngOnInit(): void {
    this.route.parent!.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => void this.loadAll());
  }

  setTab(t: 'open' | 'closed' | 'merged'): void {
    this.tab.set(t);
  }

  private loadAll(): void {
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) {
      this.loading.set(false);
      return;
    }
    this.loading.set(true);

    Promise.all([
      this.prService.getPullRequests(owner, repo, 'OPEN'),
      this.prService.getPullRequests(owner, repo, 'CLOSED'),
      this.prService.getPullRequests(owner, repo, 'MERGED'),
    ])
      .then(([open, closed, merged]) => {
        this.pulls.set([...open, ...closed, ...merged]);
      })
      .catch(() => this.pulls.set([]))
      .finally(() => this.loading.set(false));
  }

  statusBadgeClass(status: string): string {
    switch (status) {
      case 'OPEN':
        return 'badge-pill--success';
      case 'MERGED':
        return 'badge-pill--primary';
      case 'CLOSED':
        return 'badge-pill--error';
      default:
        return 'badge-pill--neutral';
    }
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'OPEN':
        return 'Open';
      case 'MERGED':
        return 'Merged';
      case 'CLOSED':
        return 'Closed';
      default:
        return status;
    }
  }
}
