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
import { HttpErrorResponse } from '@angular/common/http';
import { LucideAngularModule } from 'lucide-angular';
import { PullRequestService } from '../../../core/pull-request/services/pull-request.service';
import { ToastService } from '../../../core/toast/toast.service';
import { parseBackendErrorCode } from '../../../shared/utils/api-error.utils';
import type { MergeStrategy, PrMergeRequirements } from '../../../domain/pull-request/models/pr-conflict.model';

const CHECK_LABELS: Record<string, string> = {
  SUCCESS: 'Passed',
  FAILURE: 'Failed',
  CANCELLED: 'Cancelled',
  SKIPPED: 'Skipped',
  QUEUED: 'Queued',
  IN_PROGRESS: 'Running',
  MISSING: 'Not run for this commit',
};

/** Shows what the protected target branch still needs, so merge blockers are visible before clicking. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-pr-merge-requirements',
  host: { class: 'block' },
  standalone: true,
  imports: [LucideAngularModule],
  templateUrl: './pr-merge-requirements.component.html',
  styleUrl: './pr-merge-requirements.component.css',
})
export class PrMergeRequirementsComponent {
  readonly owner = input.required<string>();
  readonly repoName = input.required<string>();
  readonly number = input.required<number>();
  readonly targetBranch = input.required<string>();
  readonly refreshKey = input<string>('');
  /** Whether the viewer may merge, which is also what allows switching auto-merge on or off. */
  readonly canMerge = input<boolean>(false);

  /** True while the rule has unmet requirements, so the parent can disable merging. */
  readonly blocked = output<boolean>();

  private readonly prService = inject(PullRequestService);
  private readonly toast = inject(ToastService);
  readonly autoMergeStrategy = signal<MergeStrategy>('MERGE_COMMIT');
  readonly autoMergeBusy = signal(false);
  readonly strategies: { value: MergeStrategy; label: string }[] = [
    { value: 'MERGE_COMMIT', label: 'Merge commit' },
    { value: 'SQUASH', label: 'Squash' },
    { value: 'REBASE', label: 'Rebase' },
  ];
  readonly data = signal<PrMergeRequirements | null>(null);
  readonly failed = signal(false);
  private requestId = 0;

  readonly visible = computed(() => this.data()?.protectedBranch === true);
  readonly approvalsMet = computed(() => {
    const d = this.data();
    return !!d && d.currentApprovals >= d.requiredApprovals;
  });

  constructor() {
    effect(() => {
      this.refreshKey();
      if (this.owner() && this.repoName() && this.number()) void this.load();
    });
  }

  async toggleAutoMerge(): Promise<void> {
    const current = this.data();
    if (!current || this.autoMergeBusy()) return;
    this.autoMergeBusy.set(true);
    try {
      const updated = current.autoMergeStrategy
        ? await this.prService.disableAutoMerge(this.owner(), this.repoName(), this.number())
        : await this.prService.enableAutoMerge(this.owner(), this.repoName(), this.number(), this.autoMergeStrategy());
      this.data.set(updated);
      this.blocked.emit(!updated.satisfied && !updated.canBypass);
      this.toast.success(updated.autoMergeStrategy ? 'Auto-merge enabled' : 'Auto-merge disabled');
    } catch (err) {
      const message = err instanceof HttpErrorResponse ? parseBackendErrorCode(err.error) : null;
      this.toast.error(message ?? 'Could not change auto-merge');
    } finally {
      this.autoMergeBusy.set(false);
    }
  }

  label(state: string): string {
    return CHECK_LABELS[state] ?? state;
  }

  async load(): Promise<void> {
    const id = ++this.requestId;
    this.failed.set(false);
    try {
      const value = await this.prService.getMergeRequirements(this.owner(), this.repoName(), this.number());
      if (id !== this.requestId) return;
      this.data.set(value);
      this.blocked.emit(!value.satisfied && !value.canBypass);
    } catch {
      if (id !== this.requestId) return;
      this.data.set(null);
      this.failed.set(true);
      this.blocked.emit(false);
    }
  }
}
