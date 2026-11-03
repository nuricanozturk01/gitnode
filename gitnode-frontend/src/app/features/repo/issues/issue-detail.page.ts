import { Component, ChangeDetectionStrategy, DestroyRef, effect, inject, signal, computed } from '@angular/core';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { LucideAngularModule } from 'lucide-angular';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';
import { parentParamMapSignal, paramMapSignal } from '../../../core/repo/utils/route-param-signals';
import { IssueService } from '../../../core/issue/services/issue.service';
import { TokenService } from '../../../core/auth/services/token.service';
import { UserService, type UserSearchResult } from '../../../core/user/services/user.service';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import { RepoContextService } from '../../../core/repo/services/repo-context.service';
import { ToastService } from '../../../core/toast/toast.service';
import type {
  IssueDetail,
  IssueCommentInfo,
  IssueLinkedTaskInfo,
  IssueLabel,
  IssueMilestone,
} from '../../../domain/repository/models/issue.model';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
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
  readonly loadFailed = signal(false);
  readonly notFound = signal(false);
  private loadVersion = 0;
  readonly currentUser = signal<{ id: string; avatarUrl: string | null; email: string; username: string } | null>(null);
  readonly updatingAssignee = signal(false);
  readonly assigneeUsername = signal('');
  readonly assigneeSuggestions = signal<UserSearchResult[]>([]);
  private assigneeSearchTimer: ReturnType<typeof setTimeout> | undefined;
  private assigneeSearchVersion = 0;
  readonly linkedTasks = signal<IssueLinkedTaskInfo[]>([]);
  readonly labels = signal<IssueLabel[]>([]);
  readonly milestones = signal<IssueMilestone[]>([]);
  readonly milestonesFailed = signal(false);
  readonly updatingMilestone = signal(false);
  readonly selectedMilestone = signal('');
  readonly updatingLabel = signal(false);

  readonly comments = signal<IssueCommentInfo[]>([]);
  readonly commentPage = signal(0);
  readonly commentTotalPages = signal(0);
  readonly commentTotalElements = signal(0);
  readonly commentsLoading = signal(false);
  readonly commentsFailed = signal(false);

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
  readonly canManageIssues = computed(() => this.repoContext.hasPermission('ISSUE_MANAGE'));
  readonly assignees = computed(() => {
    const issue = this.issue();
    return issue?.assignees ?? (issue?.assignee ? [issue.assignee] : []);
  });
  readonly hasPrevComments = computed(() => this.commentPage() > 0);
  readonly hasNextComments = computed(() => this.commentPage() < this.commentTotalPages() - 1);

  isOwnIssue(): boolean {
    const cu = this.currentUser();
    const iss = this.issue();
    return !!cu && !!iss && cu.username === iss.author.username;
  }

  isCurrentUserAssigned(): boolean {
    const username = this.currentUser()?.username;
    return !!username && this.assignees().some((assignee) => assignee.username === username);
  }

  isOwnComment(comment: IssueCommentInfo): boolean {
    const cu = this.currentUser();
    return !!cu && cu.username === comment.author.username;
  }

  canModifyComment(comment: IssueCommentInfo): boolean {
    return this.isLoggedIn() && (this.isOwnComment(comment) || this.canManageIssues());
  }

  private readonly routeKey = computed(() => `${this.owner()}/${this.repoName()}/${this.issueNumber()}`);

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      clearTimeout(this.assigneeSearchTimer);
      this.assigneeSearchVersion++;
      this.loadVersion++;
    });
    effect(() => {
      this.routeKey();
      if (!this.owner() || !this.repoName() || !this.issueNumber()) {
        this.loading.set(false);
        return;
      }
      void this.load();
    });
    if (this.tokenService.isLoggedIn()) {
      void this.userService
        .getMe()
        .then((user) => this.currentUser.set(user))
        .catch(() => this.currentUser.set(null));
    }
  }

  private async load(): Promise<void> {
    const version = ++this.loadVersion;
    const owner = this.owner();
    const repo = this.repoName();
    const number = this.issueNumber();
    if (!owner || !repo || !number) {
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.loadFailed.set(false);
    this.notFound.set(false);
    this.commentsLoading.set(false);
    this.commentsFailed.set(false);
    this.updatingMilestone.set(false);
    try {
      const [issue, commentPage, linkedTasks, labels, milestones] = await Promise.all([
        this.issueService.get(owner, repo, number),
        this.issueService.getComments(owner, repo, number, 0).catch(() => null),
        this.issueService.getLinkedTasks(owner, repo, number).catch(() => []),
        this.issueService.listLabels(owner, repo).catch(() => []),
        this.issueService.listMilestones(owner, repo).catch(() => null),
      ]);
      if (version !== this.loadVersion) return;
      this.issue.set(issue);
      this.selectedMilestone.set(issue.milestone?.id ?? '');
      this.comments.set(commentPage?.content ?? []);
      this.commentPage.set(commentPage?.number ?? 0);
      this.commentTotalPages.set(commentPage?.totalPages ?? 0);
      this.commentTotalElements.set(commentPage?.totalElements ?? 0);
      this.commentsFailed.set(commentPage === null);
      this.linkedTasks.set(linkedTasks);
      this.labels.set(labels);
      this.milestones.set(milestones ?? []);
      this.milestonesFailed.set(milestones === null);
    } catch (error) {
      if (version !== this.loadVersion) return;
      this.issue.set(null);
      this.notFound.set(error instanceof HttpErrorResponse && error.status === 404);
      this.loadFailed.set(!this.notFound());
    } finally {
      if (version === this.loadVersion) this.loading.set(false);
    }
  }

  retryLoad(): void {
    void this.load();
  }

  hasLabel(id: string): boolean {
    return (this.issue()?.labels ?? []).some((label) => label.id === id);
  }

  async retryMilestones(): Promise<void> {
    const version = this.loadVersion;
    try {
      const milestones = await this.issueService.listMilestones(this.owner(), this.repoName());
      if (version !== this.loadVersion) return;
      this.milestones.set(milestones);
      this.milestonesFailed.set(false);
    } catch {
      if (version === this.loadVersion) this.milestonesFailed.set(true);
    }
  }

  async setMilestone(id: string): Promise<void> {
    const issue = this.issue();
    if (!issue || this.updatingMilestone()) return;
    const version = this.loadVersion;
    this.selectedMilestone.set(id);
    this.updatingMilestone.set(true);
    try {
      const updated = await this.issueService.update(
        this.owner(),
        this.repoName(),
        issue.number,
        id ? { milestoneId: id } : { clearMilestone: true },
      );
      if (version !== this.loadVersion) return;
      this.issue.set(updated);
      this.selectedMilestone.set(updated.milestone?.id ?? '');
    } catch {
      if (version !== this.loadVersion) return;
      this.selectedMilestone.set(issue.milestone?.id ?? '');
      this.toast.error('Failed to update milestone');
    } finally {
      if (version === this.loadVersion) this.updatingMilestone.set(false);
    }
  }

  async toggleLabel(label: IssueLabel): Promise<void> {
    const issue = this.issue();
    if (!issue || this.updatingLabel()) return;
    this.updatingLabel.set(true);
    try {
      this.issue.set(
        await this.issueService.update(
          this.owner(),
          this.repoName(),
          issue.number,
          this.hasLabel(label.id) ? { removeLabelId: label.id } : { addLabelId: label.id },
        ),
      );
    } catch {
      this.toast.error('Failed to update label');
    } finally {
      this.updatingLabel.set(false);
    }
  }

  async loadCommentPage(page: number): Promise<void> {
    const version = this.loadVersion;
    this.commentsLoading.set(true);
    this.commentsFailed.set(false);
    try {
      const result = await this.issueService.getComments(this.owner(), this.repoName(), this.issueNumber(), page);
      if (version !== this.loadVersion) return;
      this.comments.set(result.content);
      this.commentPage.set(result.number);
      this.commentTotalPages.set(result.totalPages);
      this.commentTotalElements.set(result.totalElements);
    } catch {
      if (version === this.loadVersion) this.commentsFailed.set(true);
    } finally {
      if (version === this.loadVersion) this.commentsLoading.set(false);
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

  async updateAssignee(clear: boolean): Promise<void> {
    const issue = this.issue();
    const user = this.currentUser();
    if (!issue || (!clear && !user) || this.updatingAssignee()) return;
    this.updatingAssignee.set(true);
    try {
      const updated = await this.issueService.update(
        this.owner(),
        this.repoName(),
        issue.number,
        clear ? { clearAssignee: true } : { addAssigneeUsername: user!.username },
      );
      this.issue.set(updated);
      this.toast.success(clear ? 'Assignee removed' : 'Issue assigned to you');
    } catch {
      this.toast.error('Failed to update assignee');
    } finally {
      this.updatingAssignee.set(false);
    }
  }

  async assignByUsername(): Promise<void> {
    const issue = this.issue();
    const username = this.assigneeUsername().trim();
    if (!issue || !username || this.updatingAssignee()) return;
    this.updatingAssignee.set(true);
    try {
      const updated = await this.issueService.update(this.owner(), this.repoName(), issue.number, {
        addAssigneeUsername: username,
      });
      this.issue.set(updated);
      this.assigneeUsername.set('');
      this.assigneeSuggestions.set([]);
      this.assigneeSearchVersion++;
      this.toast.success('Assignee updated');
    } catch {
      this.toast.error('Could not assign user. Check the username and try again.');
    } finally {
      this.updatingAssignee.set(false);
    }
  }

  async removeAssignee(username: string): Promise<void> {
    const issue = this.issue();
    if (!issue || this.updatingAssignee()) return;
    this.updatingAssignee.set(true);
    try {
      const updated = await this.issueService.update(this.owner(), this.repoName(), issue.number, {
        removeAssigneeUsername: username,
      });
      this.issue.set(updated);
      this.toast.success('Assignee removed');
    } catch {
      this.toast.error('Failed to remove assignee');
    } finally {
      this.updatingAssignee.set(false);
    }
  }

  searchAssignees(value: string): void {
    this.assigneeUsername.set(value);
    clearTimeout(this.assigneeSearchTimer);
    const version = ++this.assigneeSearchVersion;
    const query = value.trim();
    this.assigneeSuggestions.set([]);
    if (query.length < 3) return;
    this.assigneeSearchTimer = setTimeout(() => {
      void this.userService
        .searchUsers(query)
        .then((users) => {
          if (version === this.assigneeSearchVersion) this.assigneeSuggestions.set(users);
        })
        .catch(() => {
          if (version === this.assigneeSearchVersion) this.assigneeSuggestions.set([]);
        });
    }, 250);
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
