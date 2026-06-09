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

import { Component, ChangeDetectionStrategy, DestroyRef, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { LucideAngularModule } from 'lucide-angular';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { ProjectService } from '../../../core/project/services/project.service';
import type { ProjectInfo } from '../../../domain/project/models/project-info.model';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-repo-projects',
  standalone: true,
  imports: [RouterLink, LucideAngularModule],
  templateUrl: './repo-projects.page.html',
})
export class RepoProjectsPage {
  private readonly destroyRef = inject(DestroyRef);
  readonly repoContext = inject(RepoContextService);
  private readonly projectService = inject(ProjectService);

  readonly projects = signal<ProjectInfo[]>([]);
  readonly loading = signal(true);
  readonly currentPage = signal(0);
  readonly totalPages = signal(0);

  private currentOwner = '';
  private currentRepoId = '';

  constructor() {
    toObservable(this.repoContext.repo)
      .pipe(
        filter((r) => r !== null),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((r) => {
        this.currentOwner = r!.owner?.username ?? '';
        this.currentRepoId = r!.id;
        void this.load(0);
      });
  }

  async goToPage(page: number): Promise<void> {
    if (page < 0 || page >= this.totalPages()) return;
    await this.load(page);
  }

  private async load(page: number): Promise<void> {
    this.loading.set(true);
    try {
      const data = await this.projectService.getLinkedProjects(this.currentOwner, this.currentRepoId, page);
      this.projects.set(data.content);
      this.currentPage.set(data.number);
      this.totalPages.set(data.totalPages);
    } catch {
      this.projects.set([]);
    } finally {
      this.loading.set(false);
    }
  }
}
