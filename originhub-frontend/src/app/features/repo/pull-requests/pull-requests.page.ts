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

import { Component, ChangeDetectionStrategy, inject, signal, computed, OnInit, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { PullRequestService } from '../../../core/pull-request/services/pull-request.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import type { PullRequestInfo } from '../../../domain/pull-request/models/pull-request-info.model';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
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

  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  private readonly repoRouteParams = parentParamMapSignal(this.route);
  readonly owner = computed(() => this.repoRouteParams().get('owner') ?? '');
  readonly repoName = computed(() => this.repoRouteParams().get('repo') ?? '');

  readonly hasPrev = computed(() => this.page() > 0);
  readonly hasNext = computed(() => this.page() < this.totalPages() - 1);

  ngOnInit(): void {
    this.route.parent!.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.page.set(0);
      void this.loadPage();
    });
  }

  setTab(t: 'open' | 'closed' | 'merged'): void {
    this.tab.set(t);
    this.page.set(0);
    void this.loadPage();
  }

  async prevPage(): Promise<void> {
    if (!this.hasPrev()) return;
    this.page.update((p) => p - 1);
    await this.loadPage();
  }

  async nextPage(): Promise<void> {
    if (!this.hasNext()) return;
    this.page.update((p) => p + 1);
    await this.loadPage();
  }

  private async loadPage(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) {
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    const statusMap: Record<string, 'OPEN' | 'MERGED' | 'CLOSED'> = {
      open: 'OPEN',
      closed: 'CLOSED',
      merged: 'MERGED',
    };
    try {
      const result = await this.prService.getPullRequests(owner, repo, statusMap[this.tab()], this.page());
      this.pulls.set(result.content);
      this.totalPages.set(result.totalPages);
      this.totalElements.set(result.totalElements);
    } catch {
      this.pulls.set([]);
    } finally {
      this.loading.set(false);
    }
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
