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
import { RouterLink, ActivatedRoute } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { AvatarComponent } from '../../shared/components/avatar/avatar.component';
import { RelativeTimePipe } from '../../shared/pipes/relative-time.pipe';
import { RepoService } from '../../core/repo/services/repo.service';
import { UserService } from '../../core/user/services/user.service';
import { TokenService } from '../../core/auth/services/token.service';
import type { User } from '../../domain/auth/models/user.model';
import type { RepoInfo } from '../../domain/repository/models/repo-info.model';
import { paramMapSignal } from '../../core/repo/utils/route-param-signals';
import {
  collectTopicsLower,
  compareReposBySort,
  matchesRepoSearch,
  matchesTopicFilter,
  type RepoListSort,
} from '../../shared/utils/repo-list.utils';

@Component({
  selector: 'app-user-profile',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, RelativeTimePipe, AvatarComponent],
  templateUrl: './user-profile.page.html',
  styleUrl: './user-profile.page.css',
})
export class UserProfilePage {
  private readonly route = inject(ActivatedRoute);
  private readonly repoService = inject(RepoService);
  private readonly userService = inject(UserService);
  private readonly tokenService = inject(TokenService);

  readonly user = signal<User | null>(null);
  readonly repos = signal<RepoInfo[]>([]);
  readonly loading = signal(true);
  readonly repoQuery = signal('');
  readonly sortBy = signal<RepoListSort>('updated');
  readonly selectedTopics = signal<string[]>([]);
  readonly currentPage = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  private readonly profileParams = paramMapSignal(this.route);
  readonly username = computed(() => this.profileParams().get('username') ?? '');

  readonly isOwnProfile = computed(() => {
    const me = this.tokenService.getUsername();
    const u = this.username();
    if (!me || !u) return false;
    return me.toLowerCase() === u.toLowerCase();
  });

  readonly repoStats = computed(() => {
    const list = this.repos();
    return {
      total: this.totalElements(),
      public: list.filter((r) => !r.isPrivate).length,
      private: list.filter((r) => r.isPrivate).length,
      archived: list.filter((r) => r.isArchived).length,
    };
  });

  readonly allTopics = computed(() => collectTopicsLower(this.repos()));

  readonly hasActiveFilters = computed(() => this.repoQuery().trim().length > 0 || this.selectedTopics().length > 0);

  readonly filteredRepos = computed(() => {
    const q = this.repoQuery().trim().toLowerCase();
    const topics = this.selectedTopics();
    const sort = this.sortBy();
    const list = this.repos().filter((r) => matchesRepoSearch(r, q) && matchesTopicFilter(r, topics));
    return [...list].sort((a, b) => compareReposBySort(a, b, sort));
  });

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe(() => void this.loadData());
  }

  setSort(mode: RepoListSort): void {
    this.sortBy.set(mode);
  }

  onSearchInput(event: Event): void {
    const v = (event.target as HTMLInputElement).value;
    this.repoQuery.set(v);
  }

  toggleTopic(topicLower: string): void {
    const k = topicLower.toLowerCase();
    const cur = this.selectedTopics();
    if (cur.includes(k)) {
      this.selectedTopics.set(cur.filter((x) => x !== k));
    } else {
      this.selectedTopics.set([...cur, k]);
    }
  }

  isTopicActive(topicLower: string): boolean {
    return this.selectedTopics().includes(topicLower.toLowerCase());
  }

  clearFilters(): void {
    this.repoQuery.set('');
    this.selectedTopics.set([]);
  }

  clearTopicFilters(): void {
    this.selectedTopics.set([]);
  }

  async goToPage(page: number): Promise<void> {
    const username = this.username();
    if (!username) return;
    this.loading.set(true);
    try {
      const reposPage = await this.repoService.listUserRepos(username, page);
      this.currentPage.set(page);
      this.totalPages.set(reposPage.totalPages);
      this.totalElements.set(reposPage.totalElements);
      this.repos.set(reposPage.content);
    } catch {
      this.repos.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  private async loadData(): Promise<void> {
    const username = this.username();
    if (!username) return;
    this.loading.set(true);
    try {
      const [profile, reposPage] = await Promise.all([
        this.userService.getPublicProfile(username),
        this.repoService.listUserRepos(username, 0),
      ]);
      this.currentPage.set(0);
      this.totalPages.set(reposPage.totalPages);
      this.totalElements.set(reposPage.totalElements);
      this.repos.set(reposPage.content);
      this.user.set({
        id: '',
        username: profile.username,
        email: '',
        displayName: profile.displayName,
        avatarUrl: profile.avatarUrl,
        isAdmin: false,
        createdAt: '',
        updatedAt: '',
      });
    } catch {
      this.user.set(null);
      this.repos.set([]);
    } finally {
      this.loading.set(false);
    }
  }
}
