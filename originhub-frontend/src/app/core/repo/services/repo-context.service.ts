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

import { Injectable, inject, signal, computed } from '@angular/core';
import { TokenService } from '../../../core/auth/services/token.service';
import type { RepoInfo } from '../../../domain/repository/models/repo-info.model';
import type { ReleaseInfo } from '../../../domain/release/models/release-info.model';
import type { CollaboratorPermission } from '../../../domain/collaborator/collaborator.model';

@Injectable({ providedIn: 'root' })
export class RepoContextService {
  private readonly tokenService = inject(TokenService);

  readonly repo = signal<RepoInfo | null>(null);
  /** `owner/repo` key for the cached bundle below. */
  readonly repoRouteKey = signal<string | null>(null);
  readonly releases = signal<ReleaseInfo[] | null>(null);
  readonly openPrCount = signal(0);
  readonly openIssueCount = signal(0);
  readonly defaultBranch = computed(() => this.repo()?.defaultBranch ?? 'main');

  /** Active collaborator permissions for the current user (empty if owner or not a collaborator). */
  readonly collaboratorPermissions = signal<CollaboratorPermission[]>([]);

  readonly isLoggedIn = computed(() => this.tokenService.isLoggedIn());

  readonly canEdit = computed(() => {
    const r = this.repo();
    const u = this.tokenService.getUsername();
    return !!(r && u && r.owner.username.toLowerCase() === u.toLowerCase());
  });

  /** Returns true if current user is owner OR has the given collaborator permission. */
  hasPermission(perm: CollaboratorPermission): boolean {
    if (this.canEdit()) return true;
    const perms = this.collaboratorPermissions();
    return perms.includes('ADMIN') || perms.includes(perm);
  }

  /** Returns true if user can view Settings tab (owner, SETTINGS_READ, SETTINGS_WRITE, or ADMIN). */
  readonly canViewSettings = computed(
    () => this.canEdit() || this.hasPermission('SETTINGS_READ') || this.hasPermission('SETTINGS_WRITE'),
  );

  /** Can change settings that require SETTINGS_WRITE (description, topics, PR options, webhooks). */
  readonly canWriteSettings = computed(() => this.canEdit() || this.hasPermission('SETTINGS_WRITE'));

  /** Owner or collaborator with ACTIONS_WRITE — can dispatch, cancel, delete runs, enable/disable workflows. */
  readonly canWriteActions = computed(() => this.canEdit() || this.hasPermission('ACTIONS_WRITE'));

  /** Accepted collaborator on this repo (not the owner). */
  readonly isCollaborator = computed(() => !this.canEdit() && this.collaboratorPermissions().length > 0);

  setRepoBundle(
    routeKey: string,
    repo: RepoInfo,
    counts: { openPrs: number; openIssues: number; releases: ReleaseInfo[] },
  ): void {
    this.repoRouteKey.set(routeKey);
    this.repo.set(repo);
    this.openPrCount.set(counts.openPrs);
    this.openIssueCount.set(counts.openIssues);
    this.releases.set(counts.releases);
  }

  clearRepo(): void {
    this.repoRouteKey.set(null);
    this.repo.set(null);
    this.releases.set(null);
    this.openPrCount.set(0);
    this.openIssueCount.set(0);
    this.collaboratorPermissions.set([]);
  }
}
