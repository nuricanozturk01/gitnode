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

import { Component, ChangeDetectionStrategy, DestroyRef, inject, signal, computed } from '@angular/core';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LucideAngularModule } from 'lucide-angular';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { SnippetService } from '../../../core/snippet/services/snippet.service';
import { parentParamMapSignal } from '../../../core/repo/utils/route-param-signals';
import type { SnippetInfo } from '../../../domain/snippet/models/snippet.model';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-repo-snippets',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, RelativeTimePipe],
  templateUrl: './repo-snippets.page.html',
})
export class RepoSnippetsPage {
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly snippetService = inject(SnippetService);

  private readonly repoRouteParams = parentParamMapSignal(this.route);
  readonly owner = computed(() => this.repoRouteParams().get('owner') ?? '');
  readonly repoName = computed(() => this.repoRouteParams().get('repo') ?? '');

  readonly snippets = signal<SnippetInfo[]>([]);
  readonly loading = signal(true);
  readonly currentPage = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  constructor() {
    this.route.parent!.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => void this.load(0));
  }

  async goToPage(page: number): Promise<void> {
    if (page < 0 || page >= this.totalPages()) return;
    await this.load(page);
  }

  private async load(page: number): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    if (!owner || !repo) {
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    try {
      const data = await this.snippetService.listByRepo(owner, repo, page);
      this.snippets.set(data.content);
      this.currentPage.set(data.number);
      this.totalPages.set(data.totalPages);
      this.totalElements.set(data.totalElements);
    } catch {
      this.snippets.set([]);
    } finally {
      this.loading.set(false);
    }
  }
}
