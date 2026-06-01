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

import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { ReleaseService } from '../../../core/release/services/release.service';
import { TagService } from '../../../core/tag/services/tag.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import { ToastService } from '../../../core/toast/toast.service';
import type { ReleaseInfo } from '../../../domain/release/models/release-info.model';
import type { TagInfo } from '../../../domain/repository/models/tag-info.model';

const PAGE_SIZE = 10;

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-releases',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, RelativeTimePipe],
  templateUrl: './releases.page.html',
})
export class ReleasesPage {
  private readonly route = inject(ActivatedRoute);
  private readonly releaseService = inject(ReleaseService);
  private readonly tagService = inject(TagService);
  readonly repoContext = inject(RepoContextService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly toast = inject(ToastService);

  readonly releases = signal<ReleaseInfo[]>([]);
  readonly tags = signal<TagInfo[]>([]);
  readonly loading = signal(true);
  readonly releasePage = signal(0);
  readonly tagPage = signal(0);

  private readonly routeParams = parentParamMapSignal(this.route);
  readonly owner = computed(() => this.routeParams().get('owner') ?? '');
  readonly repoName = computed(() => this.routeParams().get('repo') ?? '');

  readonly tagsWithoutRelease = computed(() => this.tags().filter((t) => t.release === null));

  readonly totalReleasePages = computed(() => Math.ceil(this.releases().length / PAGE_SIZE));
  readonly pagedReleases = computed(() => {
    const p = this.releasePage();
    return this.releases().slice(p * PAGE_SIZE, (p + 1) * PAGE_SIZE);
  });

  readonly totalTagPages = computed(() => Math.ceil(this.tagsWithoutRelease().length / PAGE_SIZE));
  readonly pagedTags = computed(() => {
    const p = this.tagPage();
    return this.tagsWithoutRelease().slice(p * PAGE_SIZE, (p + 1) * PAGE_SIZE);
  });

  constructor() {
    this.route.parent?.paramMap.pipe(takeUntilDestroyed()).subscribe(() => void this.loadData());
  }

  private async loadData(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) return;
    const routeKey = `${owner}/${repo}`;
    const cachedReleases = this.repoContext.repoRouteKey() === routeKey ? this.repoContext.releases() : null;

    this.loading.set(true);
    try {
      const tags = await this.tagService.getAll(owner, repo).catch(() => []);
      let releases = cachedReleases;
      if (!releases) {
        releases = await this.releaseService.getAll(owner, repo);
        const currentRepo = this.repoContext.repo();
        if (currentRepo && this.repoContext.repoRouteKey() === routeKey) {
          this.repoContext.setRepoBundle(routeKey, currentRepo, {
            openPrs: this.repoContext.openPrCount(),
            openIssues: this.repoContext.openIssueCount(),
            releases,
          });
        }
      }
      this.releases.set(releases);
      this.tags.set(tags);
      this.releasePage.set(0);
      this.tagPage.set(0);
    } catch {
      this.releases.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  prevReleasePage(): void {
    this.releasePage.update((p) => Math.max(0, p - 1));
  }

  nextReleasePage(): void {
    this.releasePage.update((p) => Math.min(this.totalReleasePages() - 1, p + 1));
  }

  prevTagPage(): void {
    this.tagPage.update((p) => Math.max(0, p - 1));
  }

  nextTagPage(): void {
    this.tagPage.update((p) => Math.min(this.totalTagPages() - 1, p + 1));
  }

  async deleteRelease(release: ReleaseInfo): Promise<void> {
    const confirmed = await this.confirmModal.confirm(
      'Delete release',
      `Delete release "${release.name ?? release.tagName}"? The tag will remain.`,
      { variant: 'danger' },
    );
    if (!confirmed) return;
    try {
      await this.releaseService.delete(this.owner(), this.repoName(), release.id);
      this.releases.update((list) => {
        const next = list.filter((r) => r.id !== release.id);
        this.syncReleasesCache(next);
        return next;
      });
      this.toast.success('Release deleted');
    } catch {
      this.toast.error('Could not delete release');
    }
  }

  private syncReleasesCache(releases: ReleaseInfo[]): void {
    const owner = this.owner();
    const repo = this.repoName();
    const routeKey = `${owner}/${repo}`;
    const currentRepo = this.repoContext.repo();
    if (!currentRepo || this.repoContext.repoRouteKey() !== routeKey) return;
    this.repoContext.setRepoBundle(routeKey, currentRepo, {
      openPrs: this.repoContext.openPrCount(),
      openIssues: this.repoContext.openIssueCount(),
      releases,
    });
  }

  async deleteTag(tagName: string): Promise<void> {
    const confirmed = await this.confirmModal.confirm('Delete tag', `Delete tag "${tagName}"? This cannot be undone.`, {
      variant: 'danger',
    });
    if (!confirmed) return;
    try {
      await this.tagService.delete(this.owner(), this.repoName(), tagName);
      this.tags.update((list) => list.filter((t) => t.name !== tagName));
      this.toast.success('Tag deleted');
    } catch {
      this.toast.error('Could not delete tag');
    }
  }
}
