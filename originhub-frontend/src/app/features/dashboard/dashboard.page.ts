///
/// Copyright 2026 the original author or authors.
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
///

import { Component, ChangeDetectionStrategy, inject, signal, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { RelativeTimePipe } from '../../shared/pipes/relative-time.pipe';
import { TokenService } from '../../core/auth/services/token.service';
import { UserService } from '../../core/user/services/user.service';
import { RepoService } from '../../core/repo/services/repo.service';
import type { RepoInfo } from '../../domain/repository/models/repo-info.model';
import {
  collectTopicsLower,
  compareReposBySort,
  matchesRepoSearch,
  matchesTopicFilter,
  type RepoListSort,
} from '../../shared/utils/repo-list.utils';

export interface DashboardRepo {
  repo: RepoInfo;
  isCollaborator: boolean;
}

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, RelativeTimePipe],
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.css',
})
export class DashboardPage {
  private readonly tokenService = inject(TokenService);
  private readonly userService = inject(UserService);
  private readonly repoService = inject(RepoService);

  readonly repos = signal<DashboardRepo[]>([]);
  readonly loading = signal(true);
  readonly repoQuery = signal('');
  readonly sortBy = signal<RepoListSort>('updated');
  readonly selectedTopics = signal<string[]>([]);
  readonly currentPage = signal(0);
  readonly totalPages = signal(0);
  readonly ownedTotalElements = signal(0);

  private allCollaboratorRepos: DashboardRepo[] = [];

  readonly stats = computed(() => {
    const owned = this.ownedTotalElements();
    const collaborator = this.allCollaboratorRepos.length;
    return {
      total: owned + collaborator,
      owned,
      collaborator,
    };
  });

  readonly allTopics = computed(() => collectTopicsLower(this.repos().map((i) => i.repo)));

  readonly filteredRepos = computed(() => {
    const q = this.repoQuery().trim();
    const topics = this.selectedTopics();
    const sort = this.sortBy();
    const qLower = q.toLowerCase();
    let items = this.repos().filter(({ repo }) => matchesRepoSearch(repo, qLower) && matchesTopicFilter(repo, topics));
    items = [...items].sort((a, b) => compareReposBySort(a.repo, b.repo, sort));
    return items;
  });

  readonly hasActiveFilters = computed(() => this.repoQuery().trim().length > 0 || this.selectedTopics().length > 0);

  constructor() {
    this.loadRepos();
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
    const username = this.tokenService.getUsername();
    if (!username) return;
    this.loading.set(true);
    try {
      const ownedPage = await this.repoService.listUserRepos(username, page);
      this.currentPage.set(page);
      this.totalPages.set(ownedPage.totalPages);
      this.ownedTotalElements.set(ownedPage.totalElements);
      const ownedIds = new Set(ownedPage.content.map((r) => r.id));
      const merged: DashboardRepo[] = [
        ...ownedPage.content.map((r) => ({ repo: r, isCollaborator: false })),
        ...this.allCollaboratorRepos.filter((c) => !ownedIds.has(c.repo.id)),
      ];
      this.patchReposAndLoading(merged, false);
    } catch {
      this.loading.set(false);
    }
  }

  private async loadRepos(): Promise<void> {
    if (!this.tokenService.getAccessToken()) {
      this.patchReposAndLoading([], false);
      return;
    }

    let username = this.tokenService.getUsername();
    if (!username) {
      try {
        const me = await this.userService.getMe();
        username = me.username;
        this.tokenService.persistUsernameIfMissing(username);
      } catch {
        this.patchReposAndLoading([], false);
        return;
      }
    }

    try {
      const [ownedPage, collaborator] = await Promise.all([
        this.repoService.listUserRepos(username, 0),
        this.repoService.listCollaboratorRepos().catch(() => []),
      ]);
      this.currentPage.set(0);
      this.totalPages.set(ownedPage.totalPages);
      this.ownedTotalElements.set(ownedPage.totalElements);
      const ownedIds = new Set(ownedPage.content.map((r) => r.id));
      this.allCollaboratorRepos = collaborator
        .filter((r) => !ownedIds.has(r.id))
        .map((r) => ({ repo: r, isCollaborator: true }));
      const merged: DashboardRepo[] = [
        ...ownedPage.content.map((r) => ({ repo: r, isCollaborator: false })),
        ...this.allCollaboratorRepos,
      ];
      this.patchReposAndLoading(merged, false);
    } catch {
      this.patchReposAndLoading([], false);
    }
  }

  private patchReposAndLoading(nextRepos: DashboardRepo[], loading: boolean): void {
    this.repos.set(nextRepos);
    this.loading.set(loading);
  }
}
