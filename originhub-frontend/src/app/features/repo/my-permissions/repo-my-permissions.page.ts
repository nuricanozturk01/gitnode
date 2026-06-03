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

import { DatePipe } from '@angular/common';
import { Component, ChangeDetectionStrategy, computed, effect, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import { ActivatedRoute } from '@angular/router';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { CollaboratorService } from '../../../core/collaborator/collaborator.service';
import {
  ALL_PERMISSIONS,
  COLLABORATOR_PERMISSION_GROUPS,
  type CollaboratorInfo,
  type CollaboratorPermission,
} from '../../../domain/collaborator/collaborator.model';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-repo-my-permissions',
  standalone: true,
  imports: [DatePipe, LucideAngularModule, RouterLink],
  templateUrl: './repo-my-permissions.page.html',
  styleUrl: './repo-my-permissions.page.css',
})
export class RepoMyPermissionsPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly repoContext = inject(RepoContextService);
  private readonly collaboratorService = inject(CollaboratorService);

  readonly permissionGroups = COLLABORATOR_PERMISSION_GROUPS;
  readonly permissionMeta = ALL_PERMISSIONS;

  readonly membership = signal<CollaboratorInfo | null>(null);
  readonly loadingMembership = signal(true);

  private readonly repoRouteParams = parentParamMapSignal(this.route);
  readonly owner = computed(() => this.repoRouteParams().get('owner') ?? '');
  readonly repoName = computed(() => this.repoRouteParams().get('repo') ?? '');

  readonly grantedCount = computed(
    () => this.permissionMeta.filter((p) => this.hasEffectivePermission(p.value)).length,
  );

  readonly totalCount = computed(() => this.permissionMeta.length);

  constructor() {
    effect(() => {
      const owner = this.owner();
      const repo = this.repoName();
      if (!owner || !repo || !this.repoContext.repo()) return;
      if (!this.repoContext.isCollaborator()) {
        void this.router.navigate(['/', owner, repo]);
      }
    });

    effect(() => {
      const owner = this.owner();
      const repo = this.repoName();
      if (!owner || !repo || !this.repoContext.isCollaborator()) return;
      void this.loadMembership(owner, repo);
    });
  }

  private async loadMembership(owner: string, repo: string): Promise<void> {
    this.loadingMembership.set(true);
    try {
      const info = await this.collaboratorService.getMyInvitation(owner, repo);
      this.membership.set(info);
    } catch {
      this.membership.set(null);
    } finally {
      this.loadingMembership.set(false);
    }
  }

  hasEffectivePermission(perm: CollaboratorPermission): boolean {
    return this.repoContext.hasPermission(perm);
  }

  permissionLabel(perm: CollaboratorPermission): string {
    return this.permissionMeta.find((p) => p.value === perm)?.label ?? perm;
  }

  permissionDescription(perm: CollaboratorPermission): string {
    return this.permissionMeta.find((p) => p.value === perm)?.description ?? '';
  }

  groupGrantedCount(permissions: CollaboratorPermission[]): number {
    return permissions.filter((p) => this.hasEffectivePermission(p)).length;
  }
}
