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
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { CommitService } from '../../../core/commit/services/commit.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import type { CommitDetail } from '../../../domain/commit/models/commit-detail.model';
import type { DiffLine } from '../../../domain/commit/models/diff-line.model';

@Component({
  selector: 'app-commit-detail',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, RelativeTimePipe, AvatarComponent],
  templateUrl: './commit-detail.page.html',
  styleUrl: './commit-detail.page.css',
})
export class CommitDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly commitService = inject(CommitService);
  private readonly repoContext = inject(RepoContextService);

  readonly commit = signal<CommitDetail | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly expandedFiles = signal<Set<string>>(new Set());

  readonly owner = computed(() => this.route.snapshot.parent?.paramMap.get('owner') ?? '');
  readonly repoName = computed(() => this.route.snapshot.parent?.paramMap.get('repo') ?? '');
  readonly sha = computed(() => this.route.snapshot.paramMap.get('sha') ?? '');
  readonly defaultBranch = this.repoContext.defaultBranch;

  constructor() {
    this.route.params.pipe(takeUntilDestroyed()).subscribe(() => this.loadCommit());
  }

  private async loadCommit(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const sha = this.sha();
    if (!owner || !repo || !sha) return;
    this.loading.set(true);
    this.error.set(null);
    try {
      let data = await this.commitService.getCommit(owner, repo, sha);
      if (!data.files?.length) {
        const diff = await this.commitService.getCommitDiff(owner, repo, sha);
        data = { ...data, files: diff };
      }
      this.commit.set(data);
      if (data.files?.length) {
        this.expandedFiles.set(new Set(data.files.map((f) => f.newPath || f.oldPath)));
      }
    } catch {
      this.error.set('Failed to load commit');
      this.commit.set(null);
    } finally {
      this.loading.set(false);
    }
  }

  toggleFile(path: string): void {
    this.expandedFiles.update((set) => {
      const next = new Set(set);
      if (next.has(path)) next.delete(path);
      else next.add(path);
      return next;
    });
  }

  isExpanded(path: string): boolean {
    return this.expandedFiles().has(path);
  }

  changeTypeLabel(type: string): string {
    const labels: Record<string, string> = {
      ADD: 'added',
      MODIFY: 'modified',
      DELETE: 'deleted',
      RENAME: 'renamed',
      COPY: 'copied',
    };
    return labels[type] ?? type;
  }

  lineClass(line: DiffLine): string {
    if (line.type === 'ADD') return 'bg-success/10 text-success';
    if (line.type === 'DELETE') return 'bg-error/10 text-error';
    return 'text-base-content/80';
  }

  linePrefix(line: DiffLine): string {
    if (line.type === 'ADD') return '+';
    if (line.type === 'DELETE') return '-';
    return ' ';
  }
}
