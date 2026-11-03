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

import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { IssueService } from '../../../core/issue/services/issue.service';
import { PullRequestService } from '../../../core/pull-request/services/pull-request.service';
import { ToastService } from '../../../core/toast/toast.service';
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';
import type { PrMetadata } from '../../../domain/pull-request/models/pr-conflict.model';
import type { IssueLabel, IssueMilestone } from '../../../domain/repository/models/issue.model';

/** Labels, assignees and milestone of a pull request, editable by its author and maintainers. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-pr-metadata',
  standalone: true,
  imports: [FormsModule, LucideAngularModule, AvatarComponent],
  templateUrl: './pr-metadata.component.html',
  styleUrl: './pr-metadata.component.css',
})
export class PrMetadataComponent {
  readonly owner = input.required<string>();
  readonly repoName = input.required<string>();
  readonly number = input.required<number>();

  private readonly service = inject(PullRequestService);
  private readonly issues = inject(IssueService);
  private readonly toast = inject(ToastService);

  readonly data = signal<PrMetadata | null>(null);
  readonly failed = signal(false);
  readonly editing = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly allLabels = signal<IssueLabel[]>([]);
  readonly allMilestones = signal<IssueMilestone[]>([]);
  readonly pickedLabels = signal<ReadonlySet<string>>(new Set());
  readonly pickedMilestone = signal('');
  readonly assigneeText = signal('');
  readonly isEmpty = computed(() => {
    const d = this.data();
    return !d || (!d.labels.length && !d.assignees.length && !d.milestone);
  });
  private requestId = 0;

  constructor() {
    effect(() => {
      if (this.owner() && this.repoName() && this.number()) void this.load();
    });
  }

  async load(): Promise<void> {
    const id = ++this.requestId;
    this.failed.set(false);
    try {
      const value = await this.service.getMetadata(this.owner(), this.repoName(), this.number());
      if (id === this.requestId) this.data.set(value);
    } catch {
      if (id === this.requestId) this.failed.set(true);
    }
  }

  async startEdit(): Promise<void> {
    const d = this.data();
    if (!d) return;
    this.error.set(null);
    this.pickedLabels.set(new Set(d.labels.map((l) => l.id)));
    this.pickedMilestone.set(d.milestone?.id ?? '');
    this.assigneeText.set(d.assignees.map((a) => a.username).join(', '));
    this.editing.set(true);
    try {
      const [labels, milestones] = await Promise.all([
        this.issues.listLabels(this.owner(), this.repoName()),
        this.issues.listMilestones(this.owner(), this.repoName()),
      ]);
      this.allLabels.set(labels);
      this.allMilestones.set(milestones);
    } catch {
      this.error.set('Could not load the repository labels and milestones.');
    }
  }

  toggleLabel(id: string, checked: boolean): void {
    const next = new Set(this.pickedLabels());
    if (checked) next.add(id);
    else next.delete(id);
    this.pickedLabels.set(next);
  }

  textColor(hex: string): string {
    const r = parseInt(hex.slice(0, 2), 16);
    const g = parseInt(hex.slice(2, 4), 16);
    const b = parseInt(hex.slice(4, 6), 16);
    return (r * 299 + g * 587 + b * 114) / 1000 > 150 ? '#1f2328' : '#ffffff';
  }

  async save(): Promise<void> {
    if (this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    try {
      const assignees = [
        ...new Set(
          this.assigneeText()
            .split(',')
            .map((name) => name.trim())
            .filter(Boolean),
        ),
      ];
      const updated = await this.service.saveMetadata(this.owner(), this.repoName(), this.number(), {
        labelIds: [...this.pickedLabels()],
        assignees,
        milestoneId: this.pickedMilestone() || null,
      });
      this.data.set(updated);
      this.editing.set(false);
      this.toast.success('Pull request updated');
    } catch (e) {
      this.error.set(
        (e as { error?: { message?: string } })?.error?.message ?? 'Could not save. Check the assignee usernames.',
      );
    } finally {
      this.saving.set(false);
    }
  }
}
