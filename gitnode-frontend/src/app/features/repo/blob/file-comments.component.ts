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

import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { FileCommentService } from '../../../core/file-comment/services/file-comment.service';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import { ToastService } from '../../../core/toast/toast.service';
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import type { FileComment } from '../../../domain/file-comment/file-comment.model';

/** Comments on the file being viewed; line comments are chosen from the gutter of the blob page. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-file-comments',
  host: { class: 'block' },
  standalone: true,
  imports: [NgTemplateOutlet, FormsModule, LucideAngularModule, AvatarComponent, RelativeTimePipe],
  templateUrl: './file-comments.component.html',
  styleUrl: './file-comments.component.css',
})
export class FileCommentsComponent {
  readonly owner = input.required<string>();
  readonly repoName = input.required<string>();
  readonly branch = input.required<string>();
  readonly path = input.required<string>();
  readonly lineCount = input<number>(0);
  readonly canComment = input.required<boolean>();
  /** Line chosen in the gutter; two-way so the form can clear it. */
  readonly targetLine = model<number | null>(null);

  /** Lines that carry a current (not outdated) comment, for gutter markers. */
  readonly commentedLines = output<number[]>();

  private readonly service = inject(FileCommentService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly toast = inject(ToastService);

  readonly comments = signal<FileComment[]>([]);
  readonly headSha = signal('');
  readonly loading = signal(false);
  readonly loadFailed = signal(false);
  readonly draft = signal('');
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly editingId = signal<string | null>(null);
  readonly editBody = signal('');
  private requestId = 0;

  readonly replyingTo = signal<string | null>(null);
  readonly replyDraft = signal('');
  readonly roots = computed(() => this.comments().filter((c) => !c.parentId));
  readonly current = computed(() => this.roots().filter((c) => !c.outdated));
  readonly outdated = computed(() => this.roots().filter((c) => c.outdated));
  readonly showOutdated = signal(false);

  constructor() {
    effect(() => {
      const owner = this.owner();
      const repo = this.repoName();
      const branch = this.branch();
      const path = this.path();
      if (owner && repo && branch && path) void this.load();
    });
  }

  shortSha(sha: string): string {
    return sha.slice(0, 7);
  }

  async load(): Promise<void> {
    const id = ++this.requestId;
    this.loading.set(true);
    this.loadFailed.set(false);
    try {
      const page = await this.service.list(this.owner(), this.repoName(), this.branch(), this.path());
      if (id !== this.requestId) return;
      this.comments.set(page.comments);
      this.headSha.set(page.headSha);
      this.emitLines();
    } catch {
      if (id !== this.requestId) return;
      this.loadFailed.set(true);
      this.comments.set([]);
      this.emitLines();
    } finally {
      if (id === this.requestId) this.loading.set(false);
    }
  }

  private emitLines(): void {
    const lines = this.current()
      .map((c) => c.line)
      .filter((line): line is number => line !== null);
    this.commentedLines.emit([...new Set(lines)]);
  }

  repliesOf(rootId: string): FileComment[] {
    return this.comments().filter((c) => c.parentId === rootId);
  }

  startReply(root: FileComment): void {
    this.replyingTo.set(root.id);
    this.replyDraft.set('');
    this.error.set(null);
  }

  async submitReply(root: FileComment): Promise<void> {
    const body = this.replyDraft().trim();
    if (!body || this.saving() || !this.canComment()) return;
    this.saving.set(true);
    this.error.set(null);
    try {
      const created = await this.service.create(this.owner(), this.repoName(), {
        branch: this.branch(),
        path: this.path(),
        body,
        parentId: root.id,
      });
      this.comments.update((list) => [...list, { ...created, outdated: root.outdated }]);
      this.replyingTo.set(null);
      this.replyDraft.set('');
    } catch (e) {
      this.error.set((e as { error?: { message?: string } })?.error?.message ?? 'Could not post the reply.');
    } finally {
      this.saving.set(false);
    }
  }

  clearLine(): void {
    this.targetLine.set(null);
  }

  async submit(): Promise<void> {
    const body = this.draft().trim();
    if (!body || this.saving() || !this.canComment()) return;
    this.saving.set(true);
    this.error.set(null);
    try {
      const created = await this.service.create(this.owner(), this.repoName(), {
        branch: this.branch(),
        path: this.path(),
        line: this.targetLine(),
        body,
      });
      this.comments.update((list) => [...list, created]);
      this.draft.set('');
      this.targetLine.set(null);
      this.emitLines();
    } catch (e) {
      this.error.set((e as { error?: { message?: string } })?.error?.message ?? 'Could not post the comment.');
    } finally {
      this.saving.set(false);
    }
  }

  startEdit(comment: FileComment): void {
    this.editingId.set(comment.id);
    this.editBody.set(comment.body);
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  async saveEdit(comment: FileComment): Promise<void> {
    const body = this.editBody().trim();
    if (!body || this.saving()) return;
    this.saving.set(true);
    try {
      const updated = await this.service.update(this.owner(), this.repoName(), comment.id, body);
      this.comments.update((list) => list.map((c) => (c.id === updated.id ? { ...updated, outdated: c.outdated } : c)));
      this.editingId.set(null);
    } catch {
      this.toast.error('Could not update the comment');
    } finally {
      this.saving.set(false);
    }
  }

  async remove(comment: FileComment): Promise<void> {
    if (!(await this.confirmModal.confirm('Delete comment', 'Delete this comment?', { confirmLabel: 'Delete', variant: 'danger' }))) return;
    try {
      await this.service.delete(this.owner(), this.repoName(), comment.id);
      this.comments.update((list) => list.filter((c) => c.id !== comment.id && c.parentId !== comment.id));
      this.emitLines();
    } catch {
      this.toast.error('Could not delete the comment');
    }
  }
}
