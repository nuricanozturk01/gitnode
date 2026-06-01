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

import { Component, ChangeDetectionStrategy, effect, inject, signal, computed } from '@angular/core';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import {
  parentParamMapSignal,
  paramMapSignal,
  queryParamMapSignal,
} from '../../../core/repo/utils/route-param-signals';
import { LucideAngularModule } from 'lucide-angular';
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { CommitService } from '../../../core/commit/services/commit.service';
import { BranchService } from '../../../core/branch/services/branch.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import type { CommitInfo } from '../../../domain/commit/models/commit-info.model';
import type { BranchInfo } from '../../../domain/repository/models/branch-info.model';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-commits',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, RelativeTimePipe, AvatarComponent],
  templateUrl: './commits.page.html',
  styleUrl: './commits.page.css',
})
export class CommitsPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly commitService = inject(CommitService);
  private readonly branchService = inject(BranchService);
  private readonly repoContext = inject(RepoContextService);

  readonly commits = signal<CommitInfo[]>([]);
  readonly branches = signal<BranchInfo[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly totalPages = signal(0);
  readonly hasNext = signal(false);
  readonly hasPrevious = signal(false);

  private readonly parentPm = parentParamMapSignal(this.route);
  private readonly localPm = paramMapSignal(this.route);
  private readonly queryPm = queryParamMapSignal(this.route);

  readonly owner = computed(() => this.parentPm().get('owner') ?? '');
  readonly repoName = computed(() => this.parentPm().get('repo') ?? '');
  readonly branch = computed(() => this.localPm().get('branch') ?? this.repoContext.defaultBranch());
  readonly defaultBranch = this.repoContext.defaultBranch;
  readonly currentPage = computed(() => {
    const p = this.queryPm().get('page');
    return p ? parseInt(p, 10) : 0;
  });

  private readonly routeKey = computed(
    () => `${this.owner()}/${this.repoName()}/${this.branch()}:${this.currentPage()}`,
  );

  constructor() {
    effect(() => {
      this.routeKey();
      if (!this.owner() || !this.repoName()) {
        this.loading.set(false);
        return;
      }
      void this.loadData();
    });
  }

  async loadData(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const branch = this.branch();
    const page = this.currentPage();
    if (!owner || !repo) {
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    try {
      const [commitsRes, branchesData] = await Promise.all([
        this.commitService.getCommits(owner, repo, branch, page, 20),
        this.branchService.getAll(owner, repo),
      ]);
      this.commits.set(commitsRes.items);
      this.totalPages.set(commitsRes.totalPages);
      this.hasNext.set(commitsRes.hasNext);
      this.hasPrevious.set(commitsRes.hasPrevious);
      this.branches.set(branchesData);
    } catch {
      this.error.set('Failed to load commits');
      this.commits.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  goToPage(p: number): void {
    this.router.navigate(['/', this.owner(), this.repoName(), 'commits', this.branch()], {
      queryParams: { page: p },
    });
  }

  onBranchChange(branchName: string): void {
    this.router.navigate(['/', this.owner(), this.repoName(), 'commits', branchName], {
      queryParams: { page: 0 },
    });
  }
}
