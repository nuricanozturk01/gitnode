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
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { LucideAngularModule } from 'lucide-angular';
import { AvatarComponent } from '../../shared/components/avatar/avatar.component';
import { RelativeTimePipe } from '../../shared/pipes/relative-time.pipe';
import { MarkdownPipe } from '../../shared/pipes/markdown.pipe';
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

type ProfileTab = 'overview' | 'repositories';

@Component({
  selector: 'app-user-profile',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, RelativeTimePipe, AvatarComponent, MarkdownPipe],
  templateUrl: './user-profile.page.html',
  styleUrl: './user-profile.page.css',
})
export class UserProfilePage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly repoService = inject(RepoService);
  private readonly userService = inject(UserService);
  private readonly tokenService = inject(TokenService);

  readonly user = signal<User | null>(null);
  readonly repos = signal<RepoInfo[]>([]);
  readonly loading = signal(true);
  readonly profileBio = signal<string | null>(null);
  readonly profileWebsite = signal<string | null>(null);
  readonly profileLocation = signal<string | null>(null);
  readonly profileReadme = signal<string | null>(null);
  readonly activeTab = signal<ProfileTab>('overview');
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

  readonly repoStats = computed(() => ({
    total: this.totalElements(),
    public: this.repos().filter((r) => !r.isPrivate).length,
    private: this.repos().filter((r) => r.isPrivate).length,
    archived: this.repos().filter((r) => r.isArchived).length,
  }));

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
    this.route.fragment.pipe(takeUntilDestroyed()).subscribe((fragment) => {
      this.activeTab.set(this.fragmentToTab(fragment));
    });
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe(() => void this.loadData());
  }

  setTab(tab: ProfileTab): void {
    this.activeTab.set(tab);
    const path = this.location.path(false).split('#')[0];
    this.location.replaceState(path + '#' + tab);
  }

  setSort(mode: RepoListSort): void {
    this.sortBy.set(mode);
  }

  onSearchInput(event: Event): void {
    this.repoQuery.set((event.target as HTMLInputElement).value);
  }

  toggleTopic(topicLower: string): void {
    const k = topicLower.toLowerCase();
    const cur = this.selectedTopics();
    this.selectedTopics.set(cur.includes(k) ? cur.filter((x) => x !== k) : [...cur, k]);
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

  private fragmentToTab(fragment: string | null): ProfileTab {
    if (fragment === 'repositories') return 'repositories';
    return 'overview';
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
      this.profileBio.set(profile.bio ?? null);
      this.profileWebsite.set(profile.website ?? null);
      this.profileLocation.set(profile.location ?? null);
      this.profileReadme.set(profile.profileReadme ?? null);
      this.user.set({
        id: '',
        username: profile.username,
        email: '',
        displayName: profile.displayName,
        avatarUrl: profile.avatarUrl,
        bio: profile.bio ?? null,
        website: profile.website ?? null,
        location: profile.location ?? null,
        profileReadme: profile.profileReadme ?? null,
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
