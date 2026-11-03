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
import { Location } from '@angular/common';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { IssueService } from '../../../core/issue/services/issue.service';
import type { IssueLabel, IssueMilestone } from '../../../domain/repository/models/issue.model';
import { PullRequestService } from '../../../core/pull-request/services/pull-request.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import type { PrListFilter, PullRequestInfo } from '../../../domain/pull-request/models/pull-request-info.model';
import { parseUrlTab, replaceUrlFragment } from '../../../shared/utils/url-tab.utils';

const PR_LIST_TABS = ['open', 'closed', 'merged'] as const;
type PrListTab = (typeof PR_LIST_TABS)[number];
const DEFAULT_PR_LIST_TAB: PrListTab = 'open';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-pull-requests',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, RelativeTimePipe],
  templateUrl: './pull-requests.page.html',
})
export class PullRequestsPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly location = inject(Location);
  private readonly prService = inject(PullRequestService);
  private readonly issues = inject(IssueService);
  private readonly destroyRef = inject(DestroyRef);
  readonly repoContext = inject(RepoContextService);

  readonly pulls = signal<PullRequestInfo[]>([]);
  readonly allLabels = signal<IssueLabel[]>([]);
  readonly allMilestones = signal<IssueMilestone[]>([]);
  readonly labelFilter = signal('');
  readonly milestoneFilter = signal('');
  readonly assigneeFilter = signal('');
  readonly hasFilter = computed(() => !!(this.labelFilter() || this.milestoneFilter() || this.assigneeFilter().trim()));
  readonly loading = signal(true);
  readonly loadFailed = signal(false);
  private loadVersion = 0;
  readonly tab = signal<PrListTab>(DEFAULT_PR_LIST_TAB);

  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  private readonly repoRouteParams = parentParamMapSignal(this.route);
  readonly owner = computed(() => this.repoRouteParams().get('owner') ?? '');
  readonly repoName = computed(() => this.repoRouteParams().get('repo') ?? '');

  readonly hasPrev = computed(() => this.page() > 0);
  readonly hasNext = computed(() => this.page() < this.totalPages() - 1);

  ngOnInit(): void {
    this.route.fragment.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((fragment) => {
      const next = parseUrlTab(fragment, PR_LIST_TABS, DEFAULT_PR_LIST_TAB);
      if (next === this.tab()) return;
      this.tab.set(next);
      this.page.set(0);
      void this.loadPage();
    });

    this.route.parent!.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.page.set(0);
      this.labelFilter.set('');
      this.milestoneFilter.set('');
      this.assigneeFilter.set('');
      this.allLabels.set([]);
      this.allMilestones.set([]);
      void this.loadFilterOptions(this.owner(), this.repoName());
      void this.loadPage();
    });
  }

  setTab(t: PrListTab): void {
    this.tab.set(t);
    this.page.set(0);
    replaceUrlFragment(this.location, t === DEFAULT_PR_LIST_TAB ? null : t);
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

  setFilter(kind: 'label' | 'milestone' | 'assignee', value: string): void {
    if (kind === 'label') this.labelFilter.set(value);
    else if (kind === 'milestone') this.milestoneFilter.set(value);
    else this.assigneeFilter.set(value);
    this.page.set(0);
    void this.loadPage();
  }

  clearFilters(): void {
    this.labelFilter.set('');
    this.milestoneFilter.set('');
    this.assigneeFilter.set('');
    this.page.set(0);
    void this.loadPage();
  }

  labelTextColor(hex: string): string {
    const r = parseInt(hex.slice(0, 2), 16);
    const g = parseInt(hex.slice(2, 4), 16);
    const b = parseInt(hex.slice(4, 6), 16);
    return (r * 299 + g * 587 + b * 114) / 1000 > 150 ? '#1f2328' : '#ffffff';
  }

  private async loadFilterOptions(owner: string, repo: string): Promise<void> {
    try {
      const [labels, milestones] = await Promise.all([
        this.issues.listLabels(owner, repo),
        this.issues.listMilestones(owner, repo),
      ]);
      if (owner !== this.owner() || repo !== this.repoName()) return;
      this.allLabels.set(labels);
      this.allMilestones.set(milestones);
    } catch {
      // Filters are optional; the list itself still works without them.
    }
  }

  retryLoad(): void {
    void this.loadPage();
  }

  private async loadPage(): Promise<void> {
    const version = ++this.loadVersion;
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) {
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.loadFailed.set(false);
    const statusMap: Record<string, 'OPEN' | 'MERGED' | 'CLOSED'> = {
      open: 'OPEN',
      closed: 'CLOSED',
      merged: 'MERGED',
    };
    try {
      const result = await this.prService.getPullRequests(
        owner,
        repo,
        statusMap[this.tab()],
        this.page(),
        25,
        this.currentFilter(),
      );
      if (version !== this.loadVersion) return;
      this.pulls.set(result.content);
      this.totalPages.set(result.totalPages);
      this.totalElements.set(result.totalElements);
    } catch {
      if (version !== this.loadVersion) return;
      this.pulls.set([]);
      this.loadFailed.set(true);
    } finally {
      if (version === this.loadVersion) this.loading.set(false);
    }
  }

  private currentFilter(): PrListFilter {
    return {
      labelId: this.labelFilter(),
      milestoneId: this.milestoneFilter(),
      assignee: this.assigneeFilter(),
    };
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
