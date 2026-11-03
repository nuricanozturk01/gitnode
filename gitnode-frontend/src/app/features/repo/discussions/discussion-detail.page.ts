import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { TokenService } from '../../../core/auth/services/token.service';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import { DiscussionService } from '../../../core/discussion/services/discussion.service';
import { parentParamMapSignal, paramMapSignal } from '../../../core/repo/utils/route-param-signals';
import { ToastService } from '../../../core/toast/toast.service';
import {
  DISCUSSION_CATEGORIES,
  type DiscussionComment,
  type DiscussionDetail,
} from '../../../domain/discussion/models/discussion.model';
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';

interface ReplyThread {
  items: DiscussionComment[];
  page: number;
  totalPages: number;
  loading: boolean;
  open: boolean;
}

/**
 * One discussion. Comments are loaded a page at a time and replies only when a thread is opened,
 * so a long discussion never loads everything at once.
 */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-discussion-detail',
  standalone: true,
  imports: [RouterLink, FormsModule, NgTemplateOutlet, LucideAngularModule, RelativeTimePipe, AvatarComponent],
  templateUrl: './discussion-detail.page.html',
})
export class DiscussionDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly discussionService = inject(DiscussionService);
  private readonly tokenService = inject(TokenService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly toast = inject(ToastService);

  readonly detail = signal<DiscussionDetail | null>(null);
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly comments = signal<DiscussionComment[]>([]);
  readonly commentPage = signal(0);
  readonly commentPages = signal(0);
  readonly commentsLoading = signal(false);
  readonly threads = signal<Record<string, ReplyThread>>({});

  readonly newComment = signal('');
  readonly replyingTo = signal<string | null>(null);
  readonly replyBody = signal('');
  readonly editingComment = signal<string | null>(null);
  readonly editBody = signal('');
  readonly editing = signal(false);
  readonly editTitle = signal('');
  readonly editText = signal('');
  readonly busy = signal(false);
  private version = 0;

  private readonly repoParams = parentParamMapSignal(this.route);
  private readonly ownParams = paramMapSignal(this.route);
  readonly owner = computed(() => this.repoParams().get('owner') ?? '');
  readonly repoName = computed(() => this.repoParams().get('repo') ?? '');
  readonly number = computed(() => Number(this.ownParams().get('number') ?? 0));
  readonly isLoggedIn = computed(() => this.tokenService.isLoggedIn());
  readonly username = computed(() => this.tokenService.getUsername()?.toLowerCase() ?? '');
  readonly hasMoreComments = computed(() => this.commentPage() < this.commentPages() - 1);
  readonly isQuestion = computed(() => this.detail()?.info.category === 'Q_AND_A');
  readonly canEditDiscussion = computed(() => {
    const d = this.detail();
    return !!d && (d.canModerate || d.info.author.username.toLowerCase() === this.username());
  });
  readonly canPost = computed(() => this.isLoggedIn() && (!this.detail()?.info.locked || !!this.detail()?.canModerate));

  constructor() {
    inject(DestroyRef).onDestroy(() => this.version++);
    effect(() => {
      if (this.owner() && this.repoName() && this.number()) void this.load();
    });
  }

  categoryLabel(): string {
    const value = this.detail()?.info.category;
    return DISCUSSION_CATEGORIES.find((c) => c.value === value)?.label ?? '';
  }

  isOwnComment(comment: DiscussionComment): boolean {
    return comment.author.username.toLowerCase() === this.username();
  }

  canModifyComment(comment: DiscussionComment): boolean {
    return this.isLoggedIn() && (this.isOwnComment(comment) || !!this.detail()?.canModerate);
  }

  canMarkAnswer(): boolean {
    return this.isQuestion() && this.canEditDiscussion();
  }

  private async load(): Promise<void> {
    const version = ++this.version;
    this.loading.set(true);
    this.failed.set(false);
    this.comments.set([]);
    this.threads.set({});
    try {
      const detail = await this.discussionService.get(this.owner(), this.repoName(), this.number());
      if (version !== this.version) return;
      this.detail.set(detail);
      await this.loadComments(0);
    } catch {
      if (version === this.version) this.failed.set(true);
    } finally {
      if (version === this.version) this.loading.set(false);
    }
  }

  async loadComments(page: number): Promise<void> {
    this.commentsLoading.set(true);
    try {
      const result = await this.discussionService.comments(this.owner(), this.repoName(), this.number(), page);
      this.comments.update((current) => (page === 0 ? result.content : [...current, ...result.content]));
      this.commentPage.set(result.number);
      this.commentPages.set(result.totalPages);
    } catch {
      this.toast.error('Could not load comments');
    } finally {
      this.commentsLoading.set(false);
    }
  }

  async toggleReplies(comment: DiscussionComment): Promise<void> {
    const existing = this.threads()[comment.id];
    if (existing) {
      this.patchThread(comment.id, { open: !existing.open });
      return;
    }
    this.threads.update((t) => ({
      ...t,
      [comment.id]: { items: [], page: -1, totalPages: 0, loading: false, open: true },
    }));
    await this.loadMoreReplies(comment.id);
  }

  async loadMoreReplies(commentId: string): Promise<void> {
    const thread = this.threads()[commentId];
    if (!thread || thread.loading) return;
    this.patchThread(commentId, { loading: true });
    try {
      const next = thread.page + 1;
      const result = await this.discussionService.replies(
        this.owner(),
        this.repoName(),
        this.number(),
        commentId,
        next,
      );
      this.patchThread(commentId, {
        items: [...thread.items, ...result.content],
        page: result.number,
        totalPages: result.totalPages,
        loading: false,
      });
    } catch {
      this.patchThread(commentId, { loading: false });
      this.toast.error('Could not load replies');
    }
  }

  hasMoreReplies(commentId: string): boolean {
    const thread = this.threads()[commentId];
    return !!thread && thread.page < thread.totalPages - 1;
  }

  private patchThread(id: string, patch: Partial<ReplyThread>): void {
    this.threads.update((t) => (t[id] ? { ...t, [id]: { ...t[id], ...patch } } : t));
  }

  async toggleUpvote(): Promise<void> {
    const d = this.detail();
    if (!d || !this.isLoggedIn() || this.busy()) return;
    this.busy.set(true);
    try {
      const result = await this.discussionService.setUpvote(
        this.owner(),
        this.repoName(),
        this.number(),
        !d.viewerUpvoted,
      );
      this.detail.set({ ...d, viewerUpvoted: result.upvoted, info: { ...d.info, upvotes: result.upvotes } });
    } catch {
      this.toast.error('Could not update your vote');
    } finally {
      this.busy.set(false);
    }
  }

  startEdit(): void {
    const d = this.detail();
    if (!d) return;
    this.editTitle.set(d.info.title);
    this.editText.set(d.body ?? '');
    this.editing.set(true);
  }

  async saveEdit(): Promise<void> {
    if (!this.editTitle().trim() || this.busy()) return;
    this.busy.set(true);
    try {
      this.detail.set(
        await this.discussionService.update(this.owner(), this.repoName(), this.number(), {
          title: this.editTitle().trim(),
          body: this.editText(),
        }),
      );
      this.editing.set(false);
    } catch {
      this.toast.error('Could not save changes');
    } finally {
      this.busy.set(false);
    }
  }

  async toggleLock(): Promise<void> {
    const d = this.detail();
    if (!d) return;
    try {
      this.detail.set(
        await this.discussionService.setLocked(this.owner(), this.repoName(), this.number(), !d.info.locked),
      );
    } catch {
      this.toast.error('Could not change the lock');
    }
  }

  async deleteDiscussion(): Promise<void> {
    const ok = await this.confirmModal.confirm('Delete this discussion?', 'Its comments are deleted too.', {
      confirmLabel: 'Delete',
      variant: 'danger',
    });
    if (!ok) return;
    try {
      await this.discussionService.delete(this.owner(), this.repoName(), this.number());
      await this.router.navigate(['/', this.owner(), this.repoName(), 'discussions']);
    } catch {
      this.toast.error('Could not delete the discussion');
    }
  }

  async markAnswer(comment: DiscussionComment): Promise<void> {
    try {
      const detail = await this.discussionService.setAnswer(
        this.owner(),
        this.repoName(),
        this.number(),
        comment.isAnswer ? null : comment.id,
      );
      this.detail.set(detail);
      const answerId = detail.answer?.id ?? null;
      this.comments.update((list) => list.map((c) => ({ ...c, isAnswer: c.id === answerId })));
      this.threads.update((all) =>
        Object.fromEntries(
          Object.entries(all).map(([id, t]) => [
            id,
            { ...t, items: t.items.map((c) => ({ ...c, isAnswer: c.id === answerId })) },
          ]),
        ),
      );
    } catch {
      this.toast.error('Could not change the answer');
    }
  }

  async postComment(): Promise<void> {
    const body = this.newComment().trim();
    if (!body || this.busy()) return;
    this.busy.set(true);
    try {
      const created = await this.discussionService.addComment(this.owner(), this.repoName(), this.number(), body, null);
      this.newComment.set('');
      // Show the new comment only when the last page is loaded; otherwise it appears on "load more".
      if (!this.hasMoreComments()) this.comments.update((list) => [...list, created]);
      this.bumpCount(1);
    } catch (err) {
      this.toast.error(this.message(err, 'Could not post the comment'));
    } finally {
      this.busy.set(false);
    }
  }

  startReply(comment: DiscussionComment): void {
    this.replyingTo.set(comment.id);
    this.replyBody.set('');
  }

  async postReply(parent: DiscussionComment): Promise<void> {
    const body = this.replyBody().trim();
    if (!body || this.busy()) return;
    this.busy.set(true);
    try {
      const created = await this.discussionService.addComment(
        this.owner(),
        this.repoName(),
        this.number(),
        body,
        parent.id,
      );
      this.replyingTo.set(null);
      this.replyBody.set('');
      this.comments.update((list) =>
        list.map((c) => (c.id === parent.id ? { ...c, replyCount: c.replyCount + 1 } : c)),
      );
      const thread = this.threads()[parent.id];
      if (thread && thread.page >= thread.totalPages - 1) {
        this.patchThread(parent.id, { items: [...thread.items, created], open: true });
      } else if (!thread) {
        await this.toggleReplies({ ...parent, replyCount: parent.replyCount + 1 });
      }
      this.bumpCount(1);
    } catch (err) {
      this.toast.error(this.message(err, 'Could not post the reply'));
    } finally {
      this.busy.set(false);
    }
  }

  startEditComment(comment: DiscussionComment): void {
    this.editingComment.set(comment.id);
    this.editBody.set(comment.body);
  }

  async saveComment(comment: DiscussionComment): Promise<void> {
    const body = this.editBody().trim();
    if (!body || this.busy()) return;
    this.busy.set(true);
    try {
      const updated = await this.discussionService.updateComment(
        this.owner(),
        this.repoName(),
        this.number(),
        comment.id,
        body,
      );
      this.replaceComment(updated);
      this.editingComment.set(null);
    } catch {
      this.toast.error('Could not save the comment');
    } finally {
      this.busy.set(false);
    }
  }

  async deleteComment(comment: DiscussionComment): Promise<void> {
    const ok = await this.confirmModal.confirm('Delete this comment?', undefined, {
      confirmLabel: 'Delete',
      variant: 'danger',
    });
    if (!ok) return;
    try {
      await this.discussionService.deleteComment(this.owner(), this.repoName(), this.number(), comment.id);
      this.comments.update((list) => list.filter((c) => c.id !== comment.id));
      this.threads.update((all) =>
        Object.fromEntries(
          Object.entries(all).map(([id, t]) => [id, { ...t, items: t.items.filter((c) => c.id !== comment.id) }]),
        ),
      );
      this.bumpCount(-1);
    } catch {
      this.toast.error('Could not delete the comment');
    }
  }

  private replaceComment(updated: DiscussionComment): void {
    this.comments.update((list) => list.map((c) => (c.id === updated.id ? updated : c)));
    this.threads.update((all) =>
      Object.fromEntries(
        Object.entries(all).map(([id, t]) => [
          id,
          { ...t, items: t.items.map((c) => (c.id === updated.id ? updated : c)) },
        ]),
      ),
    );
  }

  private bumpCount(delta: number): void {
    const d = this.detail();
    if (d) this.detail.set({ ...d, info: { ...d.info, commentCount: Math.max(0, d.info.commentCount + delta) } });
  }

  private message(err: unknown, fallback: string): string {
    const body = (err as { error?: unknown })?.error;
    return typeof body === 'string' && body ? body : fallback;
  }
}
