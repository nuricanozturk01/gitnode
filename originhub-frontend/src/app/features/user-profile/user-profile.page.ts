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
  readonly sortBy = signal<'updated' | 'name'>('updated');

  readonly username = computed(() => this.route.snapshot.paramMap.get('username') ?? '');

  readonly isOwnProfile = computed(() => {
    const me = this.tokenService.getUsername();
    const u = this.username();
    if (!me || !u) return false;
    return me.toLowerCase() === u.toLowerCase();
  });

  readonly repoStats = computed(() => {
    const list = this.repos();
    return {
      total: list.length,
      public: list.filter((r) => !r.isPrivate).length,
      private: list.filter((r) => r.isPrivate).length,
      archived: list.filter((r) => r.isArchived).length,
    };
  });

  readonly filteredRepos = computed(() => {
    const q = this.repoQuery().trim().toLowerCase();
    let list = this.repos();
    if (q) {
      list = list.filter(
        (r) =>
          r.name.toLowerCase().includes(q) ||
          (r.description ?? '').toLowerCase().includes(q) ||
          r.topics.some((t) => t.toLowerCase().includes(q)),
      );
    }
    const sorted = [...list].sort((a, b) => {
      if (this.sortBy() === 'name') {
        return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });
      }
      return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime();
    });
    return sorted;
  });

  constructor() {
    this.route.params.pipe(takeUntilDestroyed()).subscribe(() => this.loadData());
  }

  setSort(mode: 'updated' | 'name'): void {
    this.sortBy.set(mode);
  }

  onSearchInput(event: Event): void {
    const v = (event.target as HTMLInputElement).value;
    this.repoQuery.set(v);
  }

  private async loadData(): Promise<void> {
    const username = this.username();
    if (!username) return;
    this.loading.set(true);
    try {
      const [profile, reposData] = await Promise.all([
        this.userService.getPublicProfile(username),
        this.repoService.listUserRepos(username),
      ]);
      this.repos.set(reposData);
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
