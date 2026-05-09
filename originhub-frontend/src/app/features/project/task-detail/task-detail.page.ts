import { Component, inject, signal, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { TaskService } from '../../../core/project/services/task.service';
import { BoardService } from '../../../core/project/services/board.service';
import { ProjectService } from '../../../core/project/services/project.service';
import { BranchService } from '../../../core/branch/services/branch.service';
import { RepoService } from '../../../core/repo/services/repo.service';
import { TokenService } from '../../../core/auth/services/token.service';
import { ToastService } from '../../../core/toast/toast.service';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import type { TaskDetail, SubtaskInfo, TaskStatus, TaskType } from '../../../domain/project/models/task.model';
import type { BoardColumnInfo } from '../../../domain/project/models/board-info.model';
import type { ProjectRepoInfo } from '../../../domain/project/models/project-repo-info.model';
import type { BranchInfo } from '../../../domain/repository/models/branch-info.model';

/** Repo row for the create-branch modal (linked project repos or collaborator list). */
interface BranchModalRepo {
  id: string;
  name: string;
  ownerUsername: string;
  defaultBranch: string;
}

@Component({
  selector: 'app-task-detail',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, RelativeTimePipe],
  templateUrl: './task-detail.page.html',
})
export class TaskDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly taskService = inject(TaskService);
  private readonly boardService = inject(BoardService);
  private readonly projectService = inject(ProjectService);
  private readonly branchService = inject(BranchService);
  private readonly repoService = inject(RepoService);
  private readonly tokenService = inject(TokenService);
  private readonly toastService = inject(ToastService);
  private readonly confirmModal = inject(ConfirmModalService);

  readonly task = signal<TaskDetail | null>(null);
  readonly columns = signal<BoardColumnInfo[]>([]);
  readonly loading = signal(true);

  readonly editingTitle = signal(false);
  readonly editTitle = signal('');
  readonly editingDesc = signal(false);
  readonly editDesc = signal('');
  readonly saving = signal(false);

  readonly newSubtaskTitle = signal('');
  readonly addingSubtask = signal(false);

  readonly linkedRepos = signal<ProjectRepoInfo[]>([]);
  readonly showBranchModal = signal(false);
  readonly creatingBranch = signal(false);
  readonly branchModalRepos = signal<BranchModalRepo[]>([]);
  readonly branchSelectedRepoId = signal('');
  readonly branchRefBranches = signal<BranchInfo[]>([]);
  readonly branchListLoading = signal(false);
  readonly branchSourceBranch = signal('');
  /** Editable suffix; full branch name is `{prefix}{slugify(suffix)}` (prefix ends with `-`). */
  readonly branchNameSuffix = signal('');
  readonly branchModalTarget = signal<'task' | 'subtask'>('task');
  readonly branchModalSubtaskId = signal<string | null>(null);

  get owner(): string {
    return this.tokenService.getUsername() ?? '';
  }

  get projectCode(): string {
    return this.route.snapshot.paramMap.get('projectCode') ?? '';
  }

  get taskCode(): string {
    return this.route.snapshot.paramMap.get('taskCode') ?? '';
  }

  statusBadgeClass(status: string): string {
    switch (status) {
      case 'COMPLETED':
        return 'badge-success';
      case 'IN_PROGRESS':
        return 'badge-warning';
      case 'NOT_STARTED':
        return 'badge-ghost';
      default:
        return 'badge-ghost';
    }
  }

  statusLabel(status: string): string {
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

  columnName(columnId: string): string {
    return this.columns().find((c) => c.id === columnId)?.name ?? columnId;
  }

  completedSubtasksCount(subtasks: SubtaskInfo[]): number {
    return subtasks.filter((s) => s.status === 'COMPLETED').length;
  }

  subtasksProgressPercent(subtasks: SubtaskInfo[]): number {
    if (subtasks.length === 0) return 0;
    return (this.completedSubtasksCount(subtasks) / subtasks.length) * 100;
  }

  subtasksProgressCaption(subtasks: SubtaskInfo[]): string {
    if (subtasks.length === 0) return '';
    const done = this.completedSubtasksCount(subtasks);
    if (done === subtasks.length) return 'All subtasks complete';
    return `${subtasks.length - done} remaining`;
  }

  repoForTask(task: TaskDetail): ProjectRepoInfo | null {
    if (!task.branchRepoId) {
      return null;
    }
    return this.linkedRepos().find((r) => r.id === task.branchRepoId) ?? null;
  }

  repoForSubtask(sub: SubtaskInfo): ProjectRepoInfo | null {
    if (!sub.branchRepoId) {
      return null;
    }
    return this.linkedRepos().find((r) => r.id === sub.branchRepoId) ?? null;
  }

  branchNamePrefixForModal(): string {
    const t = this.task();
    if (!t) {
      return '';
    }
    if (this.branchModalTarget() === 'subtask') {
      const sid = this.branchModalSubtaskId();
      const st = sid ? t.subtasks.find((s) => s.id === sid) : undefined;
      if (st?.code) {
        return `${t.code}.${st.code}-`;
      }
    }
    return `${t.code}-`;
  }

  private mapLinkedToModalRepo(r: ProjectRepoInfo): BranchModalRepo {
    return {
      id: r.id,
      name: r.name,
      ownerUsername: r.ownerUsername,
      defaultBranch: r.defaultBranch || 'main',
    };
  }

  async openCreateBranchModal(): Promise<void> {
    this.branchModalTarget.set('task');
    this.branchModalSubtaskId.set(null);
    await this.openBranchModalShared();
  }

  async openCreateBranchModalForSubtask(subtask: SubtaskInfo): Promise<void> {
    this.branchModalTarget.set('subtask');
    this.branchModalSubtaskId.set(subtask.id);
    await this.openBranchModalShared();
  }

  private async openBranchModalShared(): Promise<void> {
    this.branchNameSuffix.set('');
    this.branchRefBranches.set([]);

    let rows: BranchModalRepo[] = this.linkedRepos().map((r) => this.mapLinkedToModalRepo(r));
    if (rows.length === 0 && this.owner) {
      try {
        const page = await this.repoService.listUserRepos(this.owner, 0, 200);
        rows = page.content.map((r) => ({
          id: r.id,
          name: r.name,
          ownerUsername: r.owner?.username ?? this.owner,
          defaultBranch: r.defaultBranch || 'main',
        }));
      } catch {
        rows = [];
      }
    }

    this.branchModalRepos.set(rows);
    this.showBranchModal.set(true);

    if (rows.length === 0) {
      this.branchSelectedRepoId.set('');
      this.branchSourceBranch.set('');
      return;
    }

    this.branchSelectedRepoId.set(rows[0].id);
    await this.loadModalBranches();
  }

  closeCreateBranchModal(): void {
    this.showBranchModal.set(false);
    this.branchRefBranches.set([]);
    this.branchModalTarget.set('task');
    this.branchModalSubtaskId.set(null);
  }

  onBranchRepoSelect(repoId: string): void {
    this.branchSelectedRepoId.set(repoId);
    void this.loadModalBranches();
  }

  private async loadModalBranches(): Promise<void> {
    const id = this.branchSelectedRepoId();
    const repo = this.branchModalRepos().find((r) => r.id === id);
    if (!repo) {
      this.branchRefBranches.set([]);
      return;
    }
    this.branchListLoading.set(true);
    try {
      const branches = await this.branchService.getAll(repo.ownerUsername, repo.name);
      this.branchRefBranches.set(branches);
      const preferred =
        branches.find((b) => b.isDefault)?.name ??
        (branches.some((b) => b.name === repo.defaultBranch) ? repo.defaultBranch : null) ??
        branches[0]?.name ??
        repo.defaultBranch;
      this.branchSourceBranch.set(preferred || 'main');
    } catch {
      this.branchRefBranches.set([]);
      this.branchSourceBranch.set('');
      this.toastService.error('Failed to load branches');
    } finally {
      this.branchListLoading.set(false);
    }
  }

  private resolveRepoForBranchForm(): { owner: string; name: string } | null {
    const id = this.branchSelectedRepoId();
    const repo = this.branchModalRepos().find((r) => r.id === id);
    if (!repo) {
      return null;
    }
    return { owner: repo.ownerUsername, name: repo.name };
  }

  branchModalCanSubmit(): boolean {
    return (
      this.branchModalRepos().length > 0 &&
      !!this.branchSelectedRepoId() &&
      !this.branchListLoading() &&
      this.branchRefBranches().length > 0 &&
      !!this.branchSourceBranch().trim() &&
      this.branchNameSuffix().trim().length > 0
    );
  }

  /** Mirrors backend TaskService.slugify for the suffix segment. */
  private slugifyBranchSegment(input: string): string {
    return input
      .toLowerCase()
      .replace(/[^a-z0-9-]/g, '-')
      .replace(/-+/g, '-')
      .replace(/^-+/, '')
      .replace(/-+$/, '');
  }

  async submitCreateBranch(): Promise<void> {
    const repo = this.resolveRepoForBranchForm();
    if (!repo) {
      this.toastService.error('Select a repository');
      return;
    }
    const source = this.branchSourceBranch().trim();
    if (!source || !this.branchRefBranches().some((b) => b.name === source)) {
      this.toastService.error('Select a base branch');
      return;
    }
    const rawSuffix = this.branchNameSuffix().trim();
    if (!rawSuffix) {
      this.toastService.error('Enter the branch name suffix');
      return;
    }
    const slug = this.slugifyBranchSegment(rawSuffix);
    if (!slug) {
      this.toastService.error('Use letters, numbers, or hyphens in the branch name');
      return;
    }
    const branchName = `${this.branchNamePrefixForModal()}${slug}`;
    this.creatingBranch.set(true);
    try {
      const payload = {
        repoOwner: repo.owner,
        repoName: repo.name,
        sourceBranch: source,
        branchName,
      };
      if (this.branchModalTarget() === 'subtask' && this.branchModalSubtaskId()) {
        await this.taskService.createBranchForSubtask(
          this.owner,
          this.projectCode,
          this.taskCode,
          this.branchModalSubtaskId()!,
          payload,
        );
      } else {
        await this.taskService.createBranch(this.owner, this.projectCode, this.taskCode, payload);
      }
      this.toastService.success('Branch created');
      this.closeCreateBranchModal();
      await this.loadTask();
    } catch {
      this.toastService.error('Failed to create branch');
    } finally {
      this.creatingBranch.set(false);
    }
  }

  openNewPullRequest(task: TaskDetail): void {
    const repo = this.repoForTask(task);
    if (!repo || !task.branchName) {
      return;
    }
    const base = repo.defaultBranch || 'main';
    const title = `${task.code}: ${task.title}`.slice(0, 240);
    void this.router.navigate(['/', repo.ownerUsername, repo.name, 'pulls', 'new'], {
      queryParams: { head: task.branchName, base, title },
    });
  }

  openNewPullRequestForSubtask(task: TaskDetail, sub: SubtaskInfo): void {
    const repo = this.repoForSubtask(sub);
    if (!repo || !sub.branchName) {
      return;
    }
    const base = repo.defaultBranch || 'main';
    const title = `${task.code} ${sub.code}: ${sub.title}`.slice(0, 240);
    void this.router.navigate(['/', repo.ownerUsername, repo.name, 'pulls', 'new'], {
      queryParams: { head: sub.branchName, base, title },
    });
  }

  readonly statuses: TaskStatus[] = ['NOT_STARTED', 'IN_PROGRESS', 'COMPLETED'];
  readonly types: TaskType[] = ['TASK', 'BUG'];

  async ngOnInit(): Promise<void> {
    await this.loadTask();
  }

  private async loadTask(): Promise<void> {
    this.loading.set(true);
    try {
      const [task, boards, linkedRepos] = await Promise.all([
        this.taskService.get(this.owner, this.projectCode, this.taskCode),
        this.boardService.getAllBoards(this.owner, this.projectCode),
        this.projectService.getLinkedRepos(this.owner, this.projectCode).catch(() => []),
      ]);
      this.task.set(task);
      this.columns.set(boards.flatMap((b) => b.columns));
      this.linkedRepos.set(linkedRepos);
    } catch {
      this.toastService.error('Failed to load task');
    } finally {
      this.loading.set(false);
    }
  }

  startEditTitle(): void {
    this.editTitle.set(this.task()?.title ?? '');
    this.editingTitle.set(true);
  }

  async saveTitle(): Promise<void> {
    const title = this.editTitle().trim();
    if (!title || title === this.task()?.title) {
      this.editingTitle.set(false);
      return;
    }
    this.saving.set(true);
    try {
      const updated = await this.taskService.update(this.owner, this.projectCode, this.taskCode, { title });
      this.task.set(updated);
      this.editingTitle.set(false);
    } catch {
      this.toastService.error('Failed to update title');
    } finally {
      this.saving.set(false);
    }
  }

  startEditDesc(): void {
    this.editDesc.set(this.task()?.description ?? '');
    this.editingDesc.set(true);
  }

  async saveDesc(): Promise<void> {
    const description = this.editDesc().trim() || undefined;
    this.saving.set(true);
    try {
      const updated = await this.taskService.update(this.owner, this.projectCode, this.taskCode, {
        description: description ?? '',
      });
      this.task.set(updated);
      this.editingDesc.set(false);
    } catch {
      this.toastService.error('Failed to update description');
    } finally {
      this.saving.set(false);
    }
  }

  async changeStatus(status: TaskStatus): Promise<void> {
    try {
      const updated = await this.taskService.update(this.owner, this.projectCode, this.taskCode, { status });
      this.task.set(updated);
    } catch {
      this.toastService.error('Failed to update status');
    }
  }

  async changeType(type: TaskType): Promise<void> {
    try {
      const updated = await this.taskService.update(this.owner, this.projectCode, this.taskCode, { type });
      this.task.set(updated);
    } catch {
      this.toastService.error('Failed to update type');
    }
  }

  async moveToColumn(columnId: string): Promise<void> {
    try {
      const updated = await this.taskService.update(this.owner, this.projectCode, this.taskCode, {
        boardColumnId: columnId,
      });
      this.task.set(updated);
    } catch {
      this.toastService.error('Failed to move task');
    }
  }

  async addSubtask(): Promise<void> {
    const title = this.newSubtaskTitle().trim();
    if (!title) return;
    this.addingSubtask.set(true);
    try {
      const subtask = await this.taskService.createSubtask(this.owner, this.projectCode, this.taskCode, {
        title,
        position: 0,
      });
      this.task.update((t) => (t ? { ...t, subtasks: [...t.subtasks, subtask] } : t));
      this.newSubtaskTitle.set('');
    } catch {
      this.toastService.error('Failed to add subtask');
    } finally {
      this.addingSubtask.set(false);
    }
  }

  async toggleSubtask(subtask: SubtaskInfo): Promise<void> {
    const newStatus: TaskStatus = subtask.status === 'COMPLETED' ? 'NOT_STARTED' : 'COMPLETED';
    try {
      const updated = await this.taskService.updateSubtask(this.owner, this.projectCode, this.taskCode, subtask.id, {
        status: newStatus,
      });
      this.task.update((t) => (t ? { ...t, subtasks: t.subtasks.map((s) => (s.id === subtask.id ? updated : s)) } : t));
    } catch {
      this.toastService.error('Failed to update subtask');
    }
  }

  async deleteSubtask(subtask: SubtaskInfo): Promise<void> {
    try {
      await this.taskService.deleteSubtask(this.owner, this.projectCode, this.taskCode, subtask.id);
      this.task.update((t) => (t ? { ...t, subtasks: t.subtasks.filter((s) => s.id !== subtask.id) } : t));
    } catch {
      this.toastService.error('Failed to delete subtask');
    }
  }

  async deleteTask(): Promise<void> {
    const t = this.task();
    const ok = await this.confirmModal.confirm(
      `Delete task "${t?.code}"?`,
      'This will permanently delete the task and all its subtasks. This cannot be undone.',
      { confirmLabel: 'Delete task', variant: 'danger' },
    );
    if (!ok) return;
    try {
      await this.taskService.delete(this.owner, this.projectCode, this.taskCode);
      this.router.navigate(['/projects', this.projectCode]);
    } catch {
      this.toastService.error('Failed to delete task');
    }
  }
}
