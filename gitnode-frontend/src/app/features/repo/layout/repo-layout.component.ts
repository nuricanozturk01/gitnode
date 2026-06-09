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
import { RouterLink, RouterLinkActive, RouterOutlet, ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { distinctUntilChanged, filter, map } from 'rxjs';
import { LucideAngularModule } from 'lucide-angular';
import { paramMapSignal } from '../../../core/repo/utils/route-param-signals';
import { RepoService } from '../../../core/repo/services/repo.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { PullRequestService } from '../../../core/pull-request/services/pull-request.service';
import { IssueService } from '../../../core/issue/services/issue.service';
import { ReleaseService } from '../../../core/release/services/release.service';
import { TokenService } from '../../../core/auth/services/token.service';
import { ToastService } from '../../../core/toast/toast.service';
import { CollaboratorService } from '../../../core/collaborator/collaborator.service';
import type { CollaboratorInfo } from '../../../domain/collaborator/collaborator.model';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-repo-layout',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, LucideAngularModule],
  templateUrl: './repo-layout.component.html',
  styleUrl: './repo-layout.component.css',
})
export class RepoLayoutComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly repoService = inject(RepoService);
  readonly repoContext = inject(RepoContextService);
  private readonly prService = inject(PullRequestService);
  private readonly issueService = inject(IssueService);
  private readonly releaseService = inject(ReleaseService);
  private readonly tokenService = inject(TokenService);
  private readonly toastService = inject(ToastService);
  private readonly collaboratorService = inject(CollaboratorService);

  readonly loading = signal(true);
  readonly isForking = signal(false);
  readonly accessDenied = signal(false);
  readonly notFound = signal(false);
  /** Shared with settings and child routes — updates when visibility or metadata changes. */
  readonly repo = this.repoContext.repo;

  private readonly routeParams = paramMapSignal(this.route);
  readonly owner = computed(() => this.routeParams().get('owner') ?? '');
  readonly repoName = computed(() => this.routeParams().get('repo') ?? '');
  readonly prCount = signal(0);
  readonly issueCount = signal(0);
  readonly releaseCount = signal(0);

  readonly isLoggedIn = computed(() => this.repoContext.isLoggedIn());
  readonly canFork = computed(
    () => this.isLoggedIn() && !this.repoContext.canEdit() && !(this.repo()?.isPrivate ?? true),
  );
  readonly pendingInvitation = signal<CollaboratorInfo | null>(null);
  readonly respondingToInvitation = signal(false);

  constructor() {
    this.route.paramMap
      .pipe(
        map((p) => `${p.get('owner') ?? ''}/${p.get('repo') ?? ''}`),
        filter((key) => key !== '/'),
        distinctUntilChanged(),
        takeUntilDestroyed(),
      )
      .subscribe(() => void this.loadRepo());
  }

  private async loadRepo(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    this.accessDenied.set(false);
    this.notFound.set(false);
    if (!owner || !repo) {
      this.loading.set(false);
      this.repoContext.clearRepo();
      return;
    }
    const routeKey = `${owner}/${repo}`;
    this.loading.set(true);
    try {
      const repoData = await this.repoService.getRepo(owner, repo);
      this.repoContext.setRepoBundle(routeKey, repoData, {
        openPrs: 0,
        openIssues: 0,
        releases: [],
      });
      this.prCount.set(0);
      this.issueCount.set(0);
      this.releaseCount.set(0);
    } catch (err) {
      this.repoContext.clearRepo();
      if (err instanceof HttpErrorResponse) {
        if (err.status === 401 && !this.tokenService.getAccessToken()) {
          this.router.navigate(['/login']);
          return;
        }
        if (err.status === 401 || err.status === 403) {
          this.accessDenied.set(true);
        } else if (err.status === 404) {
          this.notFound.set(true);
        } else {
          this.notFound.set(true);
        }
      } else {
        this.notFound.set(true);
      }
    } finally {
      this.loading.set(false);
    }
    void this.loadTabCounts(owner, repo, routeKey);
    void this.checkPendingInvitation(owner, repo);
  }

  async forkRepo(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo || this.isForking()) return;
    this.isForking.set(true);
    try {
      const forked = await this.repoService.fork(owner, repo);
      this.toastService.success('Repository forked');
      void this.router.navigate(['/', forked.owner?.username ?? this.tokenService.getUsername(), forked.name]);
    } catch (err) {
      if (err instanceof HttpErrorResponse && err.status === 409) {
        this.toastService.error('You have already forked this repository');
      } else {
        this.toastService.error('Failed to fork repository');
      }
    } finally {
      this.isForking.set(false);
    }
  }

  private async checkPendingInvitation(owner: string, repo: string): Promise<void> {
    this.pendingInvitation.set(null);
    this.repoContext.collaboratorPermissions.set([]);
    if (!this.isLoggedIn() || this.repoContext.canEdit()) return;
    try {
      const inv = await this.collaboratorService.getMyInvitation(owner, repo);
      if (inv.status === 'PENDING') {
        this.pendingInvitation.set(inv);
      } else if (inv.status === 'ACCEPTED') {
        this.repoContext.collaboratorPermissions.set(inv.permissions);
      }
    } catch {
      // No invitation — expected for most visitors
    }
  }

  async acceptInvitation(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo || this.respondingToInvitation()) return;
    this.respondingToInvitation.set(true);
    try {
      await this.collaboratorService.acceptInvitation(owner, repo);
      this.pendingInvitation.set(null);
      this.toastService.success('You are now a collaborator on this repository');
      void this.loadRepo();
    } catch {
      this.toastService.error('Failed to accept invitation');
    } finally {
      this.respondingToInvitation.set(false);
    }
  }

  async declineInvitation(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo || this.respondingToInvitation()) return;
    this.respondingToInvitation.set(true);
    try {
      await this.collaboratorService.declineInvitation(owner, repo);
      this.pendingInvitation.set(null);
      this.toastService.success('Invitation declined');
    } catch {
      this.toastService.error('Failed to decline invitation');
    } finally {
      this.respondingToInvitation.set(false);
    }
  }

  private async loadTabCounts(owner: string, repo: string, routeKey: string): Promise<void> {
    try {
      const [prList, issueList, releaseList] = await Promise.all([
        this.prService
          .getPullRequests(owner, repo, 'OPEN')
          .catch(() => ({ content: [], number: 0, size: 0, totalElements: 0, totalPages: 0 })),
        this.issueService
          .getAll(owner, repo, 'OPEN')
          .catch(() => ({ content: [], number: 0, size: 0, totalElements: 0, totalPages: 0 })),
        this.releaseService.getAll(owner, repo).catch(() => []),
      ]);
      if (this.repoContext.repoRouteKey() !== routeKey) return;
      const currentRepo = this.repoContext.repo();
      if (!currentRepo) return;

      this.repoContext.setRepoBundle(routeKey, currentRepo, {
        openPrs: prList.totalElements,
        openIssues: issueList.totalElements,
        releases: releaseList,
      });
      this.prCount.set(prList.totalElements);
      this.issueCount.set(issueList.totalElements);
      this.releaseCount.set(releaseList.length);
    } catch {
      // Tab badges are optional; repo shell stays usable.
    }
  }
}
