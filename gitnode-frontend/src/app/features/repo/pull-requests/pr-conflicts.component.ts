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

import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { PullRequestService } from '../../../core/pull-request/services/pull-request.service';
import { ToastService } from '../../../core/toast/toast.service';
import type { PrConflictFile, PrConflictInfo } from '../../../domain/pull-request/models/pr-conflict.model';

const START = /^<{7}(?: (.*))?$/;
const MIDDLE = /^={7}$/;
const END = /^>{7}(?: (.*))?$/;

export type ConflictChoice = 'source' | 'target' | 'both';

/** Applies one of the three standard choices to every conflict block in a marked-up file. */
export function resolveBlocks(content: string, choice: ConflictChoice): string {
  const out: string[] = [];
  let side: 'none' | 'source' | 'target' = 'none';
  for (const line of content.split('\n')) {
    if (side === 'none' && START.test(line)) {
      side = 'source';
    } else if (side === 'source' && MIDDLE.test(line)) {
      side = 'target';
    } else if (side === 'target' && END.test(line)) {
      side = 'none';
    } else if (side === 'none' || choice === 'both' || choice === side) {
      out.push(line);
    }
  }
  return out.join('\n');
}

export function hasMarkers(content: string): boolean {
  return content.split('\n').some((line) => START.test(line) || END.test(line));
}

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-pr-conflicts',
  host: { class: 'block' },
  standalone: true,
  imports: [FormsModule, LucideAngularModule],
  templateUrl: './pr-conflicts.component.html',
  styleUrl: './pr-conflicts.component.css',
})
export class PrConflictsComponent {
  readonly owner = input.required<string>();
  readonly repoName = input.required<string>();
  readonly number = input.required<number>();
  readonly sourceBranch = input.required<string>();
  readonly targetBranch = input.required<string>();
  readonly canResolve = input.required<boolean>();
  /** Changes whenever the pull request is reloaded so the conflict state is re-checked. */
  readonly refreshKey = input<string>('');

  readonly conflicted = output<boolean>();
  readonly resolved = output<void>();

  private readonly prService = inject(PullRequestService);
  private readonly toast = inject(ToastService);

  readonly info = signal<PrConflictInfo | null>(null);
  readonly loading = signal(false);
  readonly loadFailed = signal(false);
  readonly editing = signal(false);
  readonly active = signal(0);
  readonly drafts = signal<Record<string, string>>({});
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  private requestId = 0;

  readonly files = computed(() => this.info()?.files ?? []);
  readonly activeFile = computed<PrConflictFile | null>(() => this.files()[this.active()] ?? null);
  readonly allResolvable = computed(() => this.files().every((f) => f.resolvable));
  readonly unresolvedPaths = computed(() =>
    this.files()
      .filter((f) => f.resolvable && hasMarkers(this.drafts()[f.path] ?? ''))
      .map((f) => f.path),
  );
  readonly remaining = computed(() => this.unresolvedPaths().length);
  readonly fetchUrl = computed(() => `git fetch origin && git checkout ${this.sourceBranch()} && git merge origin/${this.targetBranch()}`);

  constructor() {
    effect(() => {
      this.refreshKey();
      if (this.owner() && this.repoName() && this.number()) {
        void this.load();
      }
    });
  }

  async load(): Promise<void> {
    const id = ++this.requestId;
    this.loading.set(true);
    this.loadFailed.set(false);
    try {
      const data = await this.prService.getConflicts(this.owner(), this.repoName(), this.number());
      if (id !== this.requestId) return;
      this.info.set(data);
      this.conflicted.emit(!data.mergeable);
      this.drafts.set(Object.fromEntries(data.files.map((f) => [f.path, f.content ?? ''])));
      this.editing.set(false);
      this.active.set(0);
    } catch {
      if (id !== this.requestId) return;
      this.loadFailed.set(true);
      this.conflicted.emit(false);
    } finally {
      if (id === this.requestId) this.loading.set(false);
    }
  }

  startEditing(): void {
    this.error.set(null);
    this.editing.set(true);
  }

  select(index: number): void {
    this.active.set(index);
  }

  setDraft(path: string, value: string): void {
    this.drafts.update((d) => ({ ...d, [path]: value }));
  }

  choose(choice: ConflictChoice): void {
    const file = this.activeFile();
    if (!file) return;
    this.setDraft(file.path, resolveBlocks(this.drafts()[file.path] ?? '', choice));
  }

  isFileDone(file: PrConflictFile): boolean {
    return file.resolvable && !hasMarkers(this.drafts()[file.path] ?? '');
  }

  async commit(): Promise<void> {
    const data = this.info();
    if (!data || this.remaining() > 0 || !this.allResolvable() || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    try {
      await this.prService.resolveConflicts(this.owner(), this.repoName(), this.number(), {
        sourceSha: data.sourceSha,
        targetSha: data.targetSha,
        files: data.files.map((f) => ({ path: f.path, content: this.drafts()[f.path] ?? '' })),
      });
      this.toast.success('Conflicts resolved and merge commit created');
      this.resolved.emit();
    } catch (e) {
      const message = (e as { error?: { message?: string } })?.error?.message;
      this.error.set(message ?? 'Could not commit the resolution. Reload and try again.');
    } finally {
      this.saving.set(false);
    }
  }
}
