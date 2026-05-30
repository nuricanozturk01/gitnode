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

import { Component, inject, signal, computed, effect, NgZone } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { merge } from 'rxjs';
import { parentParamMapSignal, paramMapSignal } from '../../../core/repo/utils/route-param-signals';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { PullRequestService } from '../../../core/pull-request/services/pull-request.service';
import { TokenService } from '../../../core/auth/services/token.service';
import { UserService } from '../../../core/user/services/user.service';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { ToastService } from '../../../core/toast/toast.service';
import type { PullRequestDetail } from '../../../domain/pull-request/models/pull-request-detail.model';
import type { PrCommentInfo } from '../../../domain/pull-request/models/pr-comment-info.model';
import type { FileDiff } from '../../../domain/commit/models/file-diff.model';
import type { DiffLine } from '../../../domain/commit/models/diff-line.model';
import type { CommitInfo } from '../../../domain/commit/models/commit-info.model';

@Component({
  selector: 'app-pr-detail',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, FormsModule, RelativeTimePipe, AvatarComponent],
  templateUrl: './pr-detail.page.html',
  styleUrl: './pull-requests.page.css',
})
export class PrDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly prService = inject(PullRequestService);
  private readonly tokenService = inject(TokenService);
  private readonly userService = inject(UserService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly toast = inject(ToastService);
  readonly repoContext = inject(RepoContextService);
  private readonly ngZone = inject(NgZone);

  readonly pr = signal<PullRequestDetail | null>(null);
  readonly currentUser = signal<{ avatarUrl: string | null; email: string; username: string } | null>(null);
  readonly comments = signal<PrCommentInfo[]>([]);
  readonly files = signal<FileDiff[]>([]);
  readonly commits = signal<CommitInfo[]>([]);
  readonly loading = signal(true);
  readonly filesLoading = signal(false);
  readonly commitsLoading = signal(false);
  readonly filesLoaded = signal(false);
  readonly commitsLoaded = signal(false);
  readonly activeTab = signal<'conversation' | 'commits' | 'files'>('conversation');
  readonly expandedFiles = signal<Set<string>>(new Set());

  readonly newCommentBody = signal('');
  readonly submittingComment = signal(false);
  readonly editingCommentId = signal<string | null>(null);
  readonly editCommentBody = signal('');

  readonly mergeStrategy = signal<'MERGE_COMMIT' | 'SQUASH' | 'REBASE'>('MERGE_COMMIT');
  readonly mergeCommitMessage = signal('');
  readonly merging = signal(false);
  readonly mergeError = signal<string | null>(null);

  readonly mergeActionLabel = computed(() => {
    switch (this.mergeStrategy()) {
      case 'MERGE_COMMIT':
        return 'Merge';
      case 'SQUASH':
        return 'Squash and merge';
      case 'REBASE':
        return 'Rebase and merge';
      default:
        return 'Merge';
    }
  });

  readonly replyingToLine = signal<{
    filePath: string;
    lineNumber: number;
    lineSide: string;
    line: DiffLine;
  } | null>(null);
  readonly lineCommentBody = signal('');

  private readonly parentPm = parentParamMapSignal(this.route);
  private readonly localPm = paramMapSignal(this.route);
  readonly owner = computed(() => this.parentPm().get('owner') ?? '');
  readonly repoName = computed(() => this.parentPm().get('repo') ?? '');
  readonly number = computed(() => {
    const n = this.localPm().get('number');
    return n ? parseInt(n, 10) : 0;
  });

  readonly isLoggedIn = computed(() => this.tokenService.isLoggedIn());
  readonly currentUsername = computed(() => this.tokenService.getUsername() ?? '');

  readonly canMerge = computed(() => {
    const p = this.pr();
    return p?.status === 'OPEN' && !p?.isDraft && this.isLoggedIn() && this.repoContext.canEdit();
  });

  readonly isMergedOrClosed = computed(() => {
    const p = this.pr();
    return p?.status === 'MERGED' || p?.status === 'CLOSED';
  });

  readonly generalComments = computed(() => this.comments().filter((c) => !c.filePath && !c.lineNumber));

  constructor() {
    merge(this.route.parent!.paramMap, this.route.paramMap)
      .pipe(takeUntilDestroyed())
      .subscribe(() => void this.loadData());
    if (this.tokenService.isLoggedIn()) {
      this.userService
        .getMe()
        .then((u) => {
          this.currentUser.set({
            avatarUrl: u.avatarUrl,
            email: u.email,
            username: u.username,
          });
        })
        .catch(() => {
          //ignored
        });
    }
    effect(() => {
      const tab = this.activeTab();
      const owner = this.owner();
      const repo = this.repoName();
      const num = this.number();
      if (!owner || !repo || !num) return;
      if (tab === 'files' && !this.filesLoaded() && !this.filesLoading()) this.loadDiff();
      if (tab === 'commits' && !this.commitsLoaded() && !this.commitsLoading()) this.loadCommits();
    });
  }

  private async loadData(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const num = this.number();
    if (!owner || !repo || !num) {
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    try {
      const [prData, commentsData] = await Promise.all([
        this.prService.getPullRequest(owner, repo, num),
        this.prService.getComments(owner, repo, num),
      ]);
      this.ngZone.run(() => {
        this.pr.set(prData);
        this.comments.set(commentsData);
        this.filesLoaded.set(false);
        this.commitsLoaded.set(false);
        this.loading.set(false);
      });
    } catch {
      this.ngZone.run(() => {
        this.pr.set(null);
        this.comments.set([]);
        this.loading.set(false);
      });
    }
  }

  private async loadDiff(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const num = this.number();
    if (!owner || !repo || !num) return;
    this.filesLoading.set(true);
    try {
      const diff = await this.prService.getPrDiff(owner, repo, num);
      this.ngZone.run(() => {
        this.files.set(diff);
        if (diff.length > 0) this.expandedFiles.set(new Set(diff.map((f) => f.newPath || f.oldPath)));
        this.filesLoading.set(false);
        this.filesLoaded.set(true);
      });
    } catch {
      this.ngZone.run(() => {
        this.files.set([]);
        this.filesLoading.set(false);
        this.filesLoaded.set(true);
      });
    }
  }

  private async loadCommits(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const num = this.number();
    if (!owner || !repo || !num) return;
    this.commitsLoading.set(true);
    try {
      const commits = await this.prService.getPrCommits(owner, repo, num);
      this.ngZone.run(() => {
        this.commits.set(commits);
        this.commitsLoading.set(false);
        this.commitsLoaded.set(true);
      });
    } catch {
      this.ngZone.run(() => {
        this.commits.set([]);
        this.commitsLoading.set(false);
        this.commitsLoaded.set(true);
      });
    }
  }

  setTab(t: 'conversation' | 'commits' | 'files'): void {
    this.activeTab.set(t);
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

  statusBadgeClass(status: string): string {
    switch (status) {
      case 'OPEN':
        return 'badge-pill--success';
      case 'MERGED':
        return 'badge-pill--primary';
      case 'CLOSED':
        return 'badge-pill--error';
      default:
        return 'badge-pill--neutral';
    }
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'OPEN':
        return 'Open';
      case 'MERGED':
        return 'Merged';
      case 'CLOSED':
        return 'Closed';
      default:
        return status;
    }
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

  isOwnComment(comment: PrCommentInfo): boolean {
    const username = this.currentUsername();
    return username !== '' && comment.author.username === username;
  }

  getCommentsForFileLine(filePath: string, lineNumber: number, lineSide: string): PrCommentInfo[] {
    return this.comments().filter(
      (c) => c.filePath === filePath && c.lineNumber === lineNumber && c.lineSide === lineSide,
    );
  }

  startReplyToLine(filePath: string, lineNumber: number, lineSide: string, line: DiffLine): void {
    this.replyingToLine.set({ filePath, lineNumber, lineSide, line });
    this.lineCommentBody.set('');
  }

  cancelReplyToLine(): void {
    this.replyingToLine.set(null);
    this.lineCommentBody.set('');
  }

  async submitComment(): Promise<void> {
    const body = this.newCommentBody().trim();
    if (!body || this.submittingComment()) return;
    const owner = this.owner();
    const repo = this.repoName();
    const num = this.number();
    if (!owner || !repo || !num) return;
    this.submittingComment.set(true);
    try {
      const comment = await this.prService.addComment(owner, repo, num, { body });
      this.comments.update((c) => [...c, comment]);
      this.newCommentBody.set('');
      this.toast.success('Comment added');
    } catch (err) {
      this.toast.error(err instanceof Error ? err.message : 'Failed to add comment');
    } finally {
      this.submittingComment.set(false);
    }
  }

  async submitLineComment(): Promise<void> {
    const reply = this.replyingToLine();
    const body = this.lineCommentBody().trim();
    if (!reply || !body) return;
    const { filePath, lineNumber, lineSide } = reply;
    const owner = this.owner();
    const repo = this.repoName();
    const num = this.number();
    const p = this.pr();
    if (!owner || !repo || !num || !p?.sourceSha) return;
    this.submittingComment.set(true);
    try {
      const comment = await this.prService.addComment(owner, repo, num, {
        body,
        filePath,
        commitSha: p.sourceSha,
        lineNumber,
        lineSide,
      });
      this.comments.update((c) => [...c, comment]);
      this.replyingToLine.set(null);
      this.lineCommentBody.set('');
      this.toast.success('Comment added');
    } catch (err) {
      this.toast.error(err instanceof Error ? err.message : 'Failed to add comment');
    } finally {
      this.submittingComment.set(false);
    }
  }

  startEditComment(comment: PrCommentInfo): void {
    this.editingCommentId.set(comment.id);
    this.editCommentBody.set(comment.body);
  }

  cancelEditComment(): void {
    this.editingCommentId.set(null);
    this.editCommentBody.set('');
  }

  async saveEditComment(): Promise<void> {
    const id = this.editingCommentId();
    const body = this.editCommentBody().trim();
    if (!id || !body) return;
    const ok = await this.confirmModal.confirm('Save comment changes?', undefined, {
      confirmLabel: 'Save',
      variant: 'primary',
    });
    if (!ok) return;
    const owner = this.owner();
    const repo = this.repoName();
    const num = this.number();
    if (!owner || !repo || !num) return;
    try {
      const updated = await this.prService.updateComment(owner, repo, num, id, { body });
      this.comments.update((c) => c.map((x) => (x.id === id ? updated : x)));
      this.cancelEditComment();
      this.toast.success('Comment updated');
    } catch (err) {
      this.toast.error(err instanceof Error ? err.message : 'Failed to update comment');
    }
  }

  async deleteComment(comment: PrCommentInfo): Promise<void> {
    const ok = await this.confirmModal.confirm('Delete this comment?', undefined, {
      variant: 'danger',
    });
    if (!ok) return;
    const owner = this.owner();
    const repo = this.repoName();
    const num = this.number();
    if (!owner || !repo || !num) return;
    try {
      await this.prService.deleteComment(owner, repo, num, comment.id);
      this.comments.update((c) => c.filter((x) => x.id !== comment.id));
      this.toast.success('Comment deleted');
    } catch (err) {
      this.toast.error(err instanceof Error ? err.message : 'Failed to delete comment');
    }
  }

  async merge(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const num = this.number();
    const p = this.pr();
    if (!owner || !repo || !num || !p || !this.canMerge()) return;
    this.merging.set(true);
    this.mergeError.set(null);
    try {
      const msg = this.mergeCommitMessage().trim();
      const updated = await this.prService.mergePullRequest(owner, repo, num, {
        strategy: this.mergeStrategy(),
        commitMessage: msg || undefined,
      });
      this.pr.set(updated);
      this.toast.success('Pull request merged');
    } catch (err) {
      this.mergeError.set(err.error);
      this.toast.error(err.error);
    } finally {
      this.merging.set(false);
    }
  }

  async closePr(): Promise<void> {
    const ok = await this.confirmModal.confirm('Close this pull request?', undefined, {
      variant: 'danger',
    });
    if (!ok) return;
    const owner = this.owner();
    const repo = this.repoName();
    const num = this.number();
    if (!owner || !repo || !num) return;
    try {
      await this.prService.closePullRequest(owner, repo, num);
      this.toast.success('Pull request closed');
      await this.loadData();
    } catch (err) {
      this.toast.error(err instanceof Error ? err.message : 'Failed to close pull request');
    }
  }
}
