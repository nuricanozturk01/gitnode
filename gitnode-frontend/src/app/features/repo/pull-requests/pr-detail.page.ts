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

import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
  effect,
  DestroyRef,
  HostListener,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { parentParamMapSignal, paramMapSignal } from '../../../core/repo/utils/route-param-signals';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import {
  PullRequestService,
  type PrReviewer,
  type PrReview,
} from '../../../core/pull-request/services/pull-request.service';
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
import { parseUrlTab, replaceUrlFragment } from '../../../shared/utils/url-tab.utils';
import { WorkflowRunService } from '../../../core/actions/services/workflow-run.service';
import { AiService } from '../../../core/ai/services/ai.service';
import type { WorkflowRun } from '../../../domain/actions/models/workflow-run.model';
import type { AiCodeReview } from '../../../domain/ai/ai-settings.model';
import {
  resolveWorkflowDisplayStatus,
  workflowStatusBadgeClass,
  workflowStatusIconClass,
  workflowStatusIconName,
  workflowStatusIconSpinning,
  workflowStatusLabel,
} from '../../../shared/utils/workflow-status.utils';
import { apiErrorMessage } from '../../../shared/utils/api-error.utils';

const PR_DETAIL_TABS = ['conversation', 'commits', 'files', 'actions', 'ai-review'] as const;
type PrDetailTab = (typeof PR_DETAIL_TABS)[number];
const DEFAULT_PR_DETAIL_TAB: PrDetailTab = 'conversation';

import { BranchService } from '../../../core/branch/services/branch.service';
import { parseBackendErrorCode } from '../../../shared/utils/api-error.utils';
import { PrConflictsComponent } from './pr-conflicts.component';
import { PrMergeRequirementsComponent } from './pr-merge-requirements.component';
import { PrMetadataComponent } from './pr-metadata.component';
import { PrTimelineComponent } from './pr-timeline.component';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-pr-detail',
  standalone: true,
  imports: [
    RouterLink,
    LucideAngularModule,
    FormsModule,
    RelativeTimePipe,
    AvatarComponent,
    PrConflictsComponent,
    PrMergeRequirementsComponent,
    PrMetadataComponent,
    PrTimelineComponent,
  ],
  templateUrl: './pr-detail.page.html',
  styleUrl: './pull-requests.page.css',
  host: { class: 'block' },
})
export class PrDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly location = inject(Location);
  private readonly prService = inject(PullRequestService);
  private readonly branchService = inject(BranchService);
  private readonly tokenService = inject(TokenService);
  private readonly userService = inject(UserService);
  private readonly confirmModal = inject(ConfirmModalService);
  private readonly toast = inject(ToastService);
  private readonly runService = inject(WorkflowRunService);
  private readonly aiService = inject(AiService);
  private readonly destroyRef = inject(DestroyRef);
  readonly repoContext = inject(RepoContextService);

  readonly pr = signal<PullRequestDetail | null>(null);
  readonly currentUser = signal<{ avatarUrl: string | null; email: string; username: string } | null>(null);
  readonly comments = signal<PrCommentInfo[]>([]);
  readonly commentsTotalPages = signal(1);
  readonly commentsPage = signal(0);
  readonly commentsLoadingMore = signal(false);
  readonly commentsError = signal(false);
  readonly reviewers = signal<PrReviewer[]>([]);
  readonly reviewersError = signal(false);
  readonly reviewersLoading = signal(false);
  readonly reviewersSaving = signal(false);
  readonly reviewerNames = signal('');
  readonly reviews = signal<PrReview[]>([]);
  readonly reviewHeadSha = signal('');
  readonly reviewTargetSha = signal('');
  readonly reviewsError = signal(false);
  readonly reviewsLoading = signal(false);
  readonly reviewSubmitting = signal(false);
  readonly reviewDialogOpen = signal(false);
  readonly reviewBody = signal('');
  readonly reviewState = signal<PrReview['state']>('COMMENTED');
  readonly moreCommentsError = signal(false);
  readonly COMMENTS_PAGE_SIZE = 5;
  readonly files = signal<FileDiff[]>([]);
  readonly fileQuery = signal('');
  readonly visibleFiles = computed(() => {
    const q = this.fileQuery().trim().toLowerCase();
    return q ? this.files().filter((f) => (f.newPath || f.oldPath || '').toLowerCase().includes(q)) : this.files();
  });
  readonly commits = signal<CommitInfo[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal(false);
  readonly filesLoading = signal(false);
  readonly commitsLoading = signal(false);
  readonly filesLoaded = signal(false);
  readonly commitsLoaded = signal(false);
  readonly filesError = signal(false);
  readonly commitsError = signal(false);
  readonly activeTab = signal<PrDetailTab>(DEFAULT_PR_DETAIL_TAB);
  readonly expandedFiles = signal<Set<string>>(new Set());

  readonly editingHeader = signal(false);
  readonly editTitle = signal('');
  readonly editDescription = signal('');
  readonly savingHeader = signal(false);

  readonly runs = signal<WorkflowRun[]>([]);
  readonly runsLoading = signal(false);
  readonly runsLoaded = signal(false);
  readonly runsError = signal(false);

  readonly aiReview = signal<AiCodeReview | null>(null);
  readonly aiReviewLoading = signal(false);
  readonly aiReviewLoaded = signal(false);
  readonly aiReviewError = signal(false);
  readonly aiReviewPage = signal(0);
  readonly aiReviewRetrying = signal(false);
  readonly expandedAiComments = signal<Set<string>>(new Set());
  readonly aiSummaryExpanded = signal(true);

  private aiReviewPollTimer: ReturnType<typeof setInterval> | null = null;
  private dataRequest = 0;

  runStatusBadgeClass = workflowStatusBadgeClass;
  runStatusLabel = workflowStatusLabel;
  runStatusIconClass = workflowStatusIconClass;
  runStatusIconName = workflowStatusIconName;
  runStatusIconSpinning = workflowStatusIconSpinning;

  runDisplayStatus(run: WorkflowRun): string {
    return resolveWorkflowDisplayStatus(run.status, run.conclusion);
  }

  readonly newCommentBody = signal('');
  readonly submittingComment = signal(false);
  readonly editingCommentId = signal<string | null>(null);
  readonly editCommentBody = signal('');

  readonly conflicted = signal(false);
  readonly sourceBranchDeletable = signal(false);
  readonly deletingBranch = signal(false);
  readonly savingDraft = signal(false);
  readonly requirementsBlocked = signal(false);
  readonly requirementsRefresh = signal(0);
  readonly conflictRefresh = signal(0);
  readonly canResolveConflicts = computed(() => this.isLoggedIn() && this.repoContext.hasPermission('PUSH'));
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
    line: DiffLine | null;
    parentId?: string;
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
    return (
      p?.status === 'OPEN' && !p?.isDraft && this.isLoggedIn() && this.repoContext.hasPermission('PULL_REQUEST_MERGE')
    );
  });

  readonly canReview = computed(
    () =>
      this.isLoggedIn() &&
      (this.repoContext.hasPermission('PULL_REQUEST_REVIEW') || this.repoContext.hasPermission('PULL_REQUEST_MERGE')),
  );

  readonly isMergedOrClosed = computed(() => {
    const p = this.pr();
    return p?.status === 'MERGED' || p?.status === 'CLOSED';
  });

  readonly generalComments = computed(() => this.comments().filter((c) => !c.filePath && !c.lineNumber));
  readonly canSubmitReview = computed(() => this.canReview() && !!this.reviewHeadSha() && !!this.reviewTargetSha());
  readonly hasMergeReviewer = computed(() => this.reviewers().some((reviewer) => reviewer.canMerge));

  private readonly lineCommentsByKey = computed(() => {
    const map = new Map<string, PrCommentInfo[]>();
    for (const c of this.comments()) {
      if (!c.filePath || c.lineNumber == null) continue;
      const key = `${c.filePath}\0${c.lineNumber}\0${c.lineSide ?? ''}`;
      const bucket = map.get(key);
      if (bucket) bucket.push(c);
      else map.set(key, [c]);
    }
    // Each top-level conversation is followed by its replies.
    for (const [key, bucket] of map) {
      const roots = bucket.filter((c) => !c.parentId);
      const ordered = roots.flatMap((root) => [root, ...bucket.filter((c) => c.parentId === root.id)]);
      map.set(key, ordered.length === bucket.length ? ordered : bucket);
    }
    return map;
  });

  private readonly routeKey = computed(() => `${this.owner()}/${this.repoName()}/${this.number()}`);

  constructor() {
    this.route.fragment.pipe(takeUntilDestroyed()).subscribe((fragment) => {
      this.activeTab.set(parseUrlTab(fragment, PR_DETAIL_TABS, DEFAULT_PR_DETAIL_TAB));
    });

    effect(() => {
      this.routeKey();
      if (!this.owner() || !this.repoName() || !this.number()) {
        this.loading.set(false);
        return;
      }
      void this.loadData();
    });
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
      if (!owner || !repo || !num || this.loading() || !this.pr()) return;
      if (tab === 'files' && !this.filesLoaded() && !this.filesLoading()) this.loadDiff();
      if (tab === 'commits' && !this.commitsLoaded() && !this.commitsLoading()) this.loadCommits();
      if (tab === 'actions' && !this.runsLoaded() && !this.runsLoading()) this.loadRuns();
      if (tab === 'ai-review' && !this.aiReviewLoaded() && !this.aiReviewLoading()) void this.loadAiReview();
    });
    this.destroyRef.onDestroy(() => this.clearAiReviewPoll());
  }

  async loadData(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const num = this.number();
    if (!owner || !repo || !num) {
      this.loading.set(false);
      return;
    }
    const request = ++this.dataRequest;
    this.loading.set(true);
    this.loadError.set(false);
    this.pr.set(null);
    this.reviewers.set([]);
    this.reviews.set([]);
    this.reviewHeadSha.set('');
    this.reviewTargetSha.set('');
    this.reviewBody.set('');
    this.reviewsError.set(false);
    this.reviewDialogOpen.set(false);
    this.reviewSubmitting.set(false);
    this.reviewerNames.set('');
    this.reviewersError.set(false);
    this.reviewersSaving.set(false);
    this.comments.set([]);
    this.commentsError.set(false);
    this.moreCommentsError.set(false);
    this.commentsLoadingMore.set(false);
    this.files.set([]);
    this.commits.set([]);
    this.runs.set([]);
    this.filesLoaded.set(false);
    this.commitsLoaded.set(false);
    this.runsLoaded.set(false);
    this.runsError.set(false);
    this.filesLoading.set(false);
    this.commitsLoading.set(false);
    this.runsLoading.set(false);
    this.aiReviewLoading.set(false);
    this.aiReviewLoaded.set(false);
    this.aiReviewError.set(false);
    this.aiReview.set(null);
    this.aiReviewPage.set(0);
    this.clearAiReviewPoll();
    try {
      const [prResult, commentsResult] = await Promise.allSettled([
        this.prService.getPullRequest(owner, repo, num),
        this.prService.getComments(owner, repo, num, 0, this.COMMENTS_PAGE_SIZE),
      ]);
      if (request !== this.dataRequest || this.routeKey() !== `${owner}/${repo}/${num}`) return;
      if (prResult.status === 'rejected') throw prResult.reason;
      this.pr.set(prResult.value);
      this.requirementsRefresh.update((n) => n + 1);
      void this.loadSourceBranch(prResult.value.status, prResult.value.sourceBranch);
      void this.loadReviewers();
      void this.loadReviews();
      this.commentsPage.set(0);
      if (commentsResult.status === 'fulfilled') {
        this.comments.set(commentsResult.value.content);
        this.commentsTotalPages.set(commentsResult.value.totalPages);
      } else {
        this.commentsTotalPages.set(1);
        this.commentsError.set(true);
      }
      this.loading.set(false);
    } catch (error) {
      if (request !== this.dataRequest || this.routeKey() !== `${owner}/${repo}/${num}`) return;
      this.pr.set(null);
      this.comments.set([]);
      this.commentsTotalPages.set(1);
      this.commentsPage.set(0);
      this.loadError.set(!(error instanceof HttpErrorResponse && error.status === 404));
      this.loading.set(false);
    }
  }

  async loadReviewers(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const number = this.number();
    const request = this.dataRequest;
    this.reviewersLoading.set(true);
    this.reviewersError.set(false);
    try {
      const reviewers = await this.prService.getReviewers(owner, repo, number);
      if (request !== this.dataRequest) return;
      this.reviewers.set(reviewers);
      this.reviewerNames.set(reviewers.map((reviewer) => reviewer.username).join(', '));
    } catch {
      if (request === this.dataRequest) this.reviewersError.set(true);
    } finally {
      if (request === this.dataRequest) this.reviewersLoading.set(false);
    }
  }

  async loadReviews(): Promise<void> {
    const request = this.dataRequest;
    this.reviewsLoading.set(true);
    this.reviewsError.set(false);
    this.reviewDialogOpen.set(false);
    try {
      const data = await this.prService.getReviews(this.owner(), this.repoName(), this.number());
      if (request !== this.dataRequest) return;
      this.reviews.set(data.reviews);
      this.reviewHeadSha.set(data.headSha ?? '');
      this.reviewTargetSha.set(data.targetSha ?? '');
    } catch {
      if (request === this.dataRequest) this.reviewsError.set(true);
    } finally {
      if (request === this.dataRequest) this.reviewsLoading.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.reviewDialogOpen()) this.closeReviewDialog();
  }

  openReviewDialog(): void {
    const own = this.pr()?.author.username === this.currentUsername();
    if (own) this.reviewState.set('COMMENTED');
    this.reviewDialogOpen.set(true);
  }

  closeReviewDialog(): void {
    this.reviewDialogOpen.set(false);
  }

  async submitReview(): Promise<void> {
    if (!this.reviewHeadSha() || !this.reviewTargetSha() || this.reviewSubmitting()) return;
    const request = this.dataRequest;
    this.reviewSubmitting.set(true);
    try {
      const review = await this.prService.submitReview(this.owner(), this.repoName(), this.number(), {
        state: this.reviewState(),
        body: this.reviewBody().trim(),
        expectedSha: this.reviewHeadSha(),
        expectedTargetSha: this.reviewTargetSha(),
      });
      if (request !== this.dataRequest) return;
      this.reviews.update((reviews) => [...reviews, review]);
      this.requirementsRefresh.update((n) => n + 1);
      this.reviewBody.set('');
      this.reviewState.set('COMMENTED');
      this.reviewDialogOpen.set(false);
      this.toast.success('Review submitted');
    } catch (error) {
      if (request === this.dataRequest) this.toast.error(apiErrorMessage(error, 'Failed to submit review'));
    } finally {
      if (request === this.dataRequest) this.reviewSubmitting.set(false);
    }
  }

  async saveReviewers(): Promise<void> {
    const names = [
      ...new Set(
        this.reviewerNames()
          .split(',')
          .map((name) => name.trim())
          .filter(Boolean),
      ),
    ];
    if (!names.length || this.reviewersSaving()) return;
    const request = this.dataRequest;
    this.reviewersSaving.set(true);
    try {
      const reviewers = await this.prService.setReviewers(this.owner(), this.repoName(), this.number(), names);
      if (request !== this.dataRequest) return;
      this.reviewers.set(reviewers);
      this.reviewerNames.set(reviewers.map((reviewer) => reviewer.username).join(', '));
      this.reviewersError.set(false);
      this.toast.success('Reviewers updated');
    } catch (error) {
      if (request === this.dataRequest) this.toast.error(apiErrorMessage(error, 'Failed to update reviewers'));
    } finally {
      if (request === this.dataRequest) this.reviewersSaving.set(false);
    }
  }

  async retryComments(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const num = this.number();
    const request = this.dataRequest;
    if (!owner || !repo || !num || this.commentsLoadingMore()) return;
    this.commentsLoadingMore.set(true);
    try {
      const data = await this.prService.getComments(owner, repo, num, 0, this.COMMENTS_PAGE_SIZE);
      if (request !== this.dataRequest || this.routeKey() !== `${owner}/${repo}/${num}`) return;
      this.comments.set(data.content);
      this.commentsPage.set(0);
      this.commentsTotalPages.set(data.totalPages);
      this.commentsError.set(false);
    } catch {
      if (request === this.dataRequest) this.commentsError.set(true);
    } finally {
      if (request === this.dataRequest) this.commentsLoadingMore.set(false);
    }
  }

  async loadDiff(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const num = this.number();
    if (!owner || !repo || !num) return;
    const request = this.dataRequest;
    this.filesLoading.set(true);
    this.filesError.set(false);
    try {
      const diff = await this.prService.getPrDiff(owner, repo, num);
      if (request !== this.dataRequest || this.routeKey() !== `${owner}/${repo}/${num}`) return;
      this.files.set(diff);
      if (diff.length > 0) this.expandedFiles.set(new Set(diff.map((f) => f.newPath || f.oldPath)));
      this.filesLoading.set(false);
      this.filesLoaded.set(true);
    } catch {
      if (request !== this.dataRequest || this.routeKey() !== `${owner}/${repo}/${num}`) return;
      this.files.set([]);
      this.filesError.set(true);
      this.filesLoading.set(false);
      this.filesLoaded.set(true);
    }
  }

  async loadCommits(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const num = this.number();
    if (!owner || !repo || !num) return;
    const request = this.dataRequest;
    this.commitsLoading.set(true);
    this.commitsError.set(false);
    try {
      const commits = await this.prService.getPrCommits(owner, repo, num);
      if (request !== this.dataRequest || this.routeKey() !== `${owner}/${repo}/${num}`) return;
      this.commits.set(commits);
      this.commitsLoading.set(false);
      this.commitsLoaded.set(true);
    } catch {
      if (request !== this.dataRequest || this.routeKey() !== `${owner}/${repo}/${num}`) return;
      this.commits.set([]);
      this.commitsError.set(true);
      this.commitsLoading.set(false);
      this.commitsLoaded.set(true);
    }
  }

  readonly canEditPr = computed(() => {
    const p = this.pr();
    if (!p) return false;
    return this.repoContext.hasPermission('PULL_REQUEST_MERGE') || p.author.username === this.currentUsername();
  });

  readonly hasMoreComments = computed(() => this.commentsPage() < this.commentsTotalPages() - 1);

  async loadMoreComments(): Promise<void> {
    if (this.commentsLoadingMore() || !this.hasMoreComments()) return;
    const owner = this.owner();
    const repo = this.repoName();
    const num = this.number();
    if (!owner || !repo || !num) return;
    const request = this.dataRequest;
    this.commentsLoadingMore.set(true);
    this.moreCommentsError.set(false);
    try {
      const nextPage = this.commentsPage() + 1;
      const data = await this.prService.getComments(owner, repo, num, nextPage, this.COMMENTS_PAGE_SIZE);
      if (request !== this.dataRequest || this.routeKey() !== `${owner}/${repo}/${num}`) return;
      this.comments.update((c) => [...c, ...data.content]);
      this.commentsPage.set(nextPage);
      this.commentsTotalPages.set(data.totalPages);
    } catch {
      if (request === this.dataRequest) this.moreCommentsError.set(true);
    } finally {
      if (request === this.dataRequest) this.commentsLoadingMore.set(false);
    }
  }

  startEditHeader(): void {
    const p = this.pr();
    if (!p) return;
    this.editTitle.set(p.title);
    this.editDescription.set(p.description ?? '');
    this.editingHeader.set(true);
  }

  cancelEditHeader(): void {
    this.editingHeader.set(false);
  }

  async saveHeader(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const num = this.number();
    if (!owner || !repo || !num || this.savingHeader()) return;
    this.savingHeader.set(true);
    try {
      const updated = await this.prService.updatePullRequest(owner, repo, num, {
        title: this.editTitle().trim(),
        description: this.editDescription().trim() || undefined,
      });
      this.pr.set(updated);
      this.editingHeader.set(false);
      this.toast.success('Pull request updated');
    } catch {
      this.toast.error('Could not update pull request');
    } finally {
      this.savingHeader.set(false);
    }
  }

  private async loadSourceBranch(status: string, branch: string): Promise<void> {
    this.sourceBranchDeletable.set(false);
    if (status === 'OPEN' || !this.isLoggedIn() || !this.repoContext.hasPermission('PUSH')) return;
    const request = this.dataRequest;
    try {
      const branches = await this.branchService.getAll(this.owner(), this.repoName());
      if (request !== this.dataRequest) return;
      this.sourceBranchDeletable.set(branches.some((b) => b.name === branch && !b.isDefault));
    } catch {
      if (request === this.dataRequest) this.sourceBranchDeletable.set(false);
    }
  }

  async deleteSourceBranch(): Promise<void> {
    const p = this.pr();
    if (!p || this.deletingBranch()) return;
    if (
      !(await this.confirmModal.confirm('Delete branch', `Delete the ${p.sourceBranch} branch?`, {
        confirmLabel: 'Delete branch',
        variant: 'danger',
      }))
    )
      return;
    this.deletingBranch.set(true);
    try {
      await this.branchService.deleteBranch(this.owner(), this.repoName(), p.sourceBranch);
      this.sourceBranchDeletable.set(false);
      this.toast.success(`Deleted ${p.sourceBranch}`);
    } catch (error) {
      this.toast.error(apiErrorMessage(error, 'Could not delete branch'));
    } finally {
      this.deletingBranch.set(false);
    }
  }

  async reopen(): Promise<void> {
    if (this.savingDraft() || !this.canEditPr()) return;
    this.savingDraft.set(true);
    try {
      this.pr.set(await this.prService.reopenPullRequest(this.owner(), this.repoName(), this.number()));
      this.sourceBranchDeletable.set(false);
      this.requirementsRefresh.update((n) => n + 1);
      this.conflictRefreshBump();
      this.toast.success('Pull request reopened');
    } catch (error) {
      this.toast.error(apiErrorMessage(error, 'Could not reopen pull request'));
    } finally {
      this.savingDraft.set(false);
    }
  }

  private conflictRefreshBump(): void {
    this.conflictRefresh.update((n) => n + 1);
  }

  readonly resolvingId = signal<string | null>(null);
  private readonly openThreads = signal<ReadonlySet<string>>(new Set());

  isThreadOpen(id: string): boolean {
    return this.openThreads().has(id);
  }

  toggleThread(id: string): void {
    const next = new Set(this.openThreads());
    if (!next.delete(id)) next.add(id);
    this.openThreads.set(next);
  }

  canResolveThread(comment: PrCommentInfo): boolean {
    const p = this.pr();
    const me = this.currentUsername();
    return !!p && (this.canEditPr() || comment.author.username === me || p.author.username === me);
  }

  async toggleResolved(comment: PrCommentInfo): Promise<void> {
    if (this.resolvingId()) return;
    this.resolvingId.set(comment.id);
    try {
      const updated = await this.prService.setCommentResolved(
        this.owner(),
        this.repoName(),
        this.number(),
        comment.id,
        !comment.isResolved,
      );
      this.comments.update((list) => list.map((c) => (c.id === updated.id ? updated : c)));
      if (updated.isResolved) {
        const next = new Set(this.openThreads());
        next.delete(updated.id);
        this.openThreads.set(next);
      }
    } catch (error) {
      this.toast.error(apiErrorMessage(error, 'Could not update the conversation'));
    } finally {
      this.resolvingId.set(null);
    }
  }

  async setDraft(isDraft: boolean): Promise<void> {
    if (this.savingDraft() || !this.canEditPr()) return;
    this.savingDraft.set(true);
    try {
      const updated = await this.prService.updatePullRequest(this.owner(), this.repoName(), this.number(), { isDraft });
      this.pr.set(updated);
      this.requirementsRefresh.update((n) => n + 1);
      this.toast.success(isDraft ? 'Converted to draft' : 'Marked as ready for review');
    } catch (error) {
      this.toast.error(apiErrorMessage(error, 'Could not update pull request'));
    } finally {
      this.savingDraft.set(false);
    }
  }

  async loadRuns(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const pr = this.pr();
    if (!owner || !repo || !pr) return;
    const request = this.dataRequest;
    this.runsLoading.set(true);
    this.runsError.set(false);
    try {
      const triggerRef = `refs/heads/${pr.sourceBranch}`;
      const page = await this.runService.listRuns(owner, repo, 0, 30, { event: 'pull_request', triggerRef });
      if (request !== this.dataRequest) return;
      this.runs.set(page.content);
      this.runsLoaded.set(true);
    } catch {
      if (request !== this.dataRequest) return;
      this.runs.set([]);
      this.runsError.set(true);
      this.runsLoaded.set(true);
    } finally {
      if (request === this.dataRequest) this.runsLoading.set(false);
    }
  }

  setTab(t: PrDetailTab): void {
    this.activeTab.set(t);
    replaceUrlFragment(this.location, t === DEFAULT_PR_DETAIL_TAB ? null : t);
  }

  onTabKeydown(event: KeyboardEvent): void {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    const container = (event.currentTarget as HTMLElement).parentElement;
    if (!container) return;
    const tabs = Array.from(container.querySelectorAll<HTMLButtonElement>('[role="tab"]'));
    const current = tabs.indexOf(document.activeElement as HTMLButtonElement);
    if (current < 0 || tabs.length === 0) return;
    event.preventDefault();
    const next =
      event.key === 'Home'
        ? 0
        : event.key === 'End'
          ? tabs.length - 1
          : (current + (event.key === 'ArrowRight' ? 1 : -1) + tabs.length) % tabs.length;
    tabs[next].focus();
  }

  prTabButtonClass(tab: PrDetailTab): string {
    const base = 'shrink-0 whitespace-nowrap border-b-2 px-3 py-2.5 text-sm font-medium transition-colors sm:px-4';
    return this.activeTab() === tab
      ? `${base} border-primary text-primary`
      : `${base} border-transparent text-base-content/55 hover:text-base-content`;
  }

  mergeStrategyButtonClass(strategy: 'MERGE_COMMIT' | 'SQUASH' | 'REBASE'): string {
    const base = 'btn btn-sm justify-start gap-2 rounded-lg border';
    return this.mergeStrategy() === strategy
      ? `${base} btn-primary border-primary`
      : `${base} btn-ghost border-base-300/70`;
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
    const key = `${filePath}\0${lineNumber}\0${lineSide}`;
    return this.lineCommentsByKey().get(key) ?? [];
  }

  startReplyToLine(filePath: string, lineNumber: number, lineSide: string, line: DiffLine): void {
    this.replyingToLine.set({ filePath, lineNumber, lineSide, line });
    this.lineCommentBody.set('');
  }

  startReplyToComment(root: PrCommentInfo): void {
    if (!root.filePath || root.lineNumber == null) return;
    this.replyingToLine.set({
      filePath: root.filePath,
      lineNumber: root.lineNumber,
      lineSide: root.lineSide ?? '',
      line: null,
      parentId: root.id,
    });
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
        parentId: reply.parentId,
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

  async onConflictsResolved(): Promise<void> {
    await this.loadData();
    this.conflictRefresh.update((n) => n + 1);
  }

  async merge(): Promise<void> {
    if (this.conflicted() || this.requirementsBlocked()) return;
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
      const msg =
        err instanceof HttpErrorResponse ? (parseBackendErrorCode(err.error) ?? 'Merge failed') : 'Merge failed';
      this.mergeError.set(msg);
      this.toast.error(msg);
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

  async loadAiReview(silent = false): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const num = this.number();
    if (!owner || !repo || !num) return;
    const request = this.dataRequest;
    const page = this.aiReviewPage();
    if (!silent) {
      this.aiReviewLoading.set(true);
      this.aiReviewError.set(false);
    }
    try {
      const review = await this.aiService.getCodeReview(owner, repo, num, page);
      if (request !== this.dataRequest || page !== this.aiReviewPage()) return;
      this.aiReview.set(review);
      this.aiReviewError.set(false);
      this.aiReviewLoaded.set(true);
      if (this.aiReviewPage() === 0) {
        this.initAiReviewExpanded(review);
      }
      this.scheduleAiReviewPoll(review);
    } catch (error) {
      if (request !== this.dataRequest || page !== this.aiReviewPage()) return;
      this.aiReview.set(null);
      this.aiReviewError.set(!(error instanceof HttpErrorResponse && error.status === 404));
      this.aiReviewLoaded.set(true);
      this.clearAiReviewPoll();
    } finally {
      if (!silent && request === this.dataRequest && page === this.aiReviewPage()) {
        this.aiReviewLoading.set(false);
      }
    }
  }

  async retryAiReview(): Promise<void> {
    const owner = this.owner();
    const repo = this.repoName();
    const num = this.number();
    if (!owner || !repo || !num) return;
    this.aiReviewRetrying.set(true);
    const previousStatus = this.aiReview()?.status;
    try {
      const review = await this.aiService.retryCodeReview(owner, repo, num);
      this.aiReview.set(review);
      this.aiReviewLoaded.set(true);
      this.aiReviewPage.set(0);
      this.expandedAiComments.set(new Set());
      this.aiSummaryExpanded.set(true);
      this.scheduleAiReviewPoll(review);
      const toastMessage =
        previousStatus === 'COMPLETED'
          ? 'Re-running AI review'
          : previousStatus === 'PENDING'
            ? 'Restarting AI review'
            : previousStatus === 'FAILED' || previousStatus === 'SKIPPED'
              ? 'Retrying AI review'
              : 'AI review started';
      this.toast.success(toastMessage);
    } catch (err) {
      this.toast.error(apiErrorMessage(err, 'Failed to retry AI review'));
    } finally {
      this.aiReviewRetrying.set(false);
    }
  }

  private scheduleAiReviewPoll(review: AiCodeReview | null): void {
    this.clearAiReviewPoll();
    if (review?.status === 'PENDING' && this.activeTab() === 'ai-review') {
      this.aiReviewPollTimer = setInterval(() => void this.loadAiReview(true), 3000);
    }
  }

  private clearAiReviewPoll(): void {
    if (this.aiReviewPollTimer) {
      clearInterval(this.aiReviewPollTimer);
      this.aiReviewPollTimer = null;
    }
  }

  async loadMoreAiReviewComments(): Promise<void> {
    const review = this.aiReview();
    if (!review || this.aiReviewPage() + 1 >= review.comments.totalPages) return;
    this.aiReviewPage.update((p) => p + 1);
    await this.loadAiReview();
  }

  async loadPrevAiReviewComments(): Promise<void> {
    if (this.aiReviewPage() === 0) return;
    this.aiReviewPage.update((p) => p - 1);
    await this.loadAiReview();
  }

  isAiCommentExpanded(commentId: string): boolean {
    return this.expandedAiComments().has(commentId);
  }

  toggleAiComment(commentId: string): void {
    this.expandedAiComments.update((current) => {
      const next = new Set(current);
      if (next.has(commentId)) {
        next.delete(commentId);
      } else {
        next.add(commentId);
      }
      return next;
    });
  }

  expandAllAiComments(): void {
    const review = this.aiReview();
    if (!review) return;
    this.expandedAiComments.set(new Set(review.comments.content.map((c) => c.id)));
    this.aiSummaryExpanded.set(true);
  }

  collapseAllAiComments(): void {
    this.expandedAiComments.set(new Set());
    this.aiSummaryExpanded.set(false);
  }

  toggleAiSummary(): void {
    this.aiSummaryExpanded.update((open) => !open);
  }

  private initAiReviewExpanded(review: AiCodeReview): void {
    if (review.status !== 'COMPLETED') {
      this.expandedAiComments.set(new Set());
      this.aiSummaryExpanded.set(true);
      return;
    }

    const expanded = new Set<string>();
    for (const comment of review.comments.content) {
      if (comment.severity === 'CRITICAL' || comment.severity === 'HIGH') {
        expanded.add(comment.id);
      }
    }
    this.expandedAiComments.set(expanded);
    this.aiSummaryExpanded.set(true);
  }

  severityBadgeClass(severity: string): string {
    switch (severity) {
      case 'CRITICAL':
      case 'HIGH':
        return 'badge-pill badge-pill--error';
      case 'MEDIUM':
        return 'badge-pill badge-pill--warning';
      case 'LOW':
        return 'badge-pill badge-pill--primary';
      default:
        return 'badge-pill badge-pill--neutral';
    }
  }

  aiReviewStatusMessageClass(status: string): string {
    const base = 'rounded-lg border px-4 py-3 text-sm';
    switch (status) {
      case 'FAILED':
        return `${base} border-error/30 bg-error/5 text-error`;
      case 'SKIPPED':
        return `${base} border-base-300/70 bg-base-200/40 text-base-content/70`;
      default:
        return base;
    }
  }
}
