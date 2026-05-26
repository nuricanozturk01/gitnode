import { Component, inject, signal, computed } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { merge } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';
import { parentParamMapSignal, paramMapSignal } from '../../../core/repo/utils/route-param-signals';
import { IssueService } from '../../../core/issue/services/issue.service';
import { TokenService } from '../../../core/auth/services/token.service';
import { UserService } from '../../../core/user/services/user.service';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { ToastService } from '../../../core/toast/toast.service';
import type { IssueDetail, IssueCommentInfo, IssueLinkedTaskInfo } from '../../../domain/repository/models/issue.model';

@Component({
  selector: 'app-issue-detail',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, FormsModule, RelativeTimePipe, AvatarComponent],
  templateUrl: './issue-detail.page.html',
})
export class IssueDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly issueService = inject(IssueService);
  private readonly tokenService = inject(TokenService);
  private readonly userService = inject(UserService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly toast = inject(ToastService);
  readonly repoContext = inject(RepoContextService);

  readonly issue = signal<IssueDetail | null>(null);
  readonly loading = signal(true);
  readonly currentUser = signal<{ avatarUrl: string | null; email: string; username: string } | null>(null);
  readonly linkedTasks = signal<IssueLinkedTaskInfo[]>([]);

  readonly comments = signal<IssueCommentInfo[]>([]);
  readonly commentPage = signal(0);
  readonly commentTotalPages = signal(0);
  readonly commentTotalElements = signal(0);
  readonly commentsLoading = signal(false);

  readonly newCommentBody = signal('');
  readonly submittingComment = signal(false);
  readonly editingCommentId = signal<string | null>(null);
  readonly editCommentBody = signal('');

  private readonly repoRouteParams = parentParamMapSignal(this.route);
  private readonly routeParams = paramMapSignal(this.route);
  readonly owner = computed(() => this.repoRouteParams().get('owner') ?? '');
  readonly repoName = computed(() => this.repoRouteParams().get('repo') ?? '');
  readonly issueNumber = computed(() => Number(this.routeParams().get('number') ?? '0'));

  readonly isLoggedIn = computed(() => this.tokenService.isLoggedIn());
  readonly hasPrevComments = computed(() => this.commentPage() > 0);
  readonly hasNextComments = computed(() => this.commentPage() < this.commentTotalPages() - 1);

  constructor() {
    merge(this.route.parent!.paramMap, this.route.paramMap)
      .pipe(takeUntilDestroyed())
      .subscribe(() => void this.load());
  }

  private async load(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const number = this.issueNumber();
    if (!owner || !repo || !number) {
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    try {
      const [issue, commentPage, user, linkedTasks] = await Promise.all([
        this.issueService.get(owner, repo, number),
        this.issueService.getComments(owner, repo, number, 0),
        this.userService.getMe().catch(() => null),
        this.issueService.getLinkedTasks(owner, repo, number).catch(() => []),
      ]);
      this.issue.set(issue);
      this.comments.set(commentPage.content);
      this.commentPage.set(commentPage.number);
      this.commentTotalPages.set(commentPage.totalPages);
      this.commentTotalElements.set(commentPage.totalElements);
      this.currentUser.set(user);
      this.linkedTasks.set(linkedTasks);
    } catch {
      this.issue.set(null);
    } finally {
      this.loading.set(false);
    }
  }

  async loadCommentPage(page: number): Promise<void> {
    this.commentsLoading.set(true);
    try {
      const result = await this.issueService.getComments(this.owner(), this.repoName(), this.issueNumber(), page);
      this.comments.set(result.content);
      this.commentPage.set(result.number);
      this.commentTotalPages.set(result.totalPages);
      this.commentTotalElements.set(result.totalElements);
    } catch {
      this.toast.error('Failed to load comments');
    } finally {
      this.commentsLoading.set(false);
    }
  }

  async prevCommentPage(): Promise<void> {
    if (!this.hasPrevComments()) return;
    await this.loadCommentPage(this.commentPage() - 1);
  }

  async nextCommentPage(): Promise<void> {
    if (!this.hasNextComments()) return;
    await this.loadCommentPage(this.commentPage() + 1);
  }

  async toggleStatus(): Promise<void> {
    const issue = this.issue();
    if (!issue) return;
    const newStatus = issue.status === 'OPEN' ? 'CLOSED' : 'OPEN';
    try {
      const updated = await this.issueService.update(this.owner(), this.repoName(), issue.number, {
        status: newStatus,
      });
      this.issue.set(updated);
      this.toast.success(`Issue ${newStatus === 'CLOSED' ? 'closed' : 're-opened'}`);
    } catch {
      this.toast.error('Failed to update issue');
    }
  }

  async deleteIssue(): Promise<void> {
    const confirmed = await this.confirmModal.confirm(
      'Delete issue',
      'This will permanently delete the issue and all its comments.',
      { confirmLabel: 'Delete', variant: 'danger' },
    );
    if (!confirmed) return;
    try {
      await this.issueService.delete(this.owner(), this.repoName(), this.issueNumber());
      await this.router.navigate(['/', this.owner(), this.repoName(), 'issues']);
      this.toast.success('Issue deleted');
    } catch {
      this.toast.error('Failed to delete issue');
    }
  }

  async submitComment(): Promise<void> {
    const body = this.newCommentBody().trim();
    if (!body) return;
    this.submittingComment.set(true);
    try {
      await this.issueService.addComment(this.owner(), this.repoName(), this.issueNumber(), { body });
      this.newCommentBody.set('');
      const issue = this.issue();
      if (issue) {
        this.issue.set({ ...issue, commentCount: issue.commentCount + 1 });
      }
      const lastPage = Math.max(0, Math.ceil((this.commentTotalElements() + 1) / 10) - 1);
      await this.loadCommentPage(lastPage);
    } catch {
      this.toast.error('Failed to add comment');
    } finally {
      this.submittingComment.set(false);
    }
  }

  startEditComment(comment: IssueCommentInfo): void {
    this.editingCommentId.set(comment.id);
    this.editCommentBody.set(comment.body);
  }

  cancelEditComment(): void {
    this.editingCommentId.set(null);
    this.editCommentBody.set('');
  }

  async saveEditComment(commentId: string): Promise<void> {
    const body = this.editCommentBody().trim();
    if (!body) return;
    try {
      const updated = await this.issueService.updateComment(
        this.owner(),
        this.repoName(),
        this.issueNumber(),
        commentId,
        { body },
      );
      this.comments.update((list) => list.map((c) => (c.id === commentId ? updated : c)));
      this.editingCommentId.set(null);
    } catch {
      this.toast.error('Failed to update comment');
    }
  }

  async deleteComment(commentId: string): Promise<void> {
    const confirmed = await this.confirmModal.confirm('Delete comment', 'This will permanently delete this comment.', {
      confirmLabel: 'Delete',
      variant: 'danger',
    });
    if (!confirmed) return;
    try {
      await this.issueService.deleteComment(this.owner(), this.repoName(), this.issueNumber(), commentId);
      const issue = this.issue();
      if (issue) {
        this.issue.set({ ...issue, commentCount: Math.max(0, issue.commentCount - 1) });
      }
      await this.loadCommentPage(this.commentPage());
    } catch {
      this.toast.error('Failed to delete comment');
    }
  }

  statusBadgeClass(status: string): string {
    return status === 'OPEN' ? 'badge-pill--success' : 'badge-pill--neutral';
  }

  statusLabel(status: string): string {
    return status === 'OPEN' ? 'Open' : 'Closed';
  }

  taskStatusLabel(status: string): string {
    switch (status) {
      case 'NOT_STARTED':
        return 'Not started';
      case 'IN_PROGRESS':
        return 'In progress';
      case 'COMPLETED':
        return 'Completed';
      default:
        return status;
    }
  }

  taskStatusClass(status: string): string {
    switch (status) {
      case 'COMPLETED':
        return 'text-success';
      case 'IN_PROGRESS':
        return 'text-warning';
      default:
        return 'text-base-content/50';
    }
  }
}
