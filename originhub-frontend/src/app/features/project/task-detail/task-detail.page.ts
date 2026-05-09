import { Component, inject, signal, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { TaskService } from '../../../core/project/services/task.service';
import { BoardService } from '../../../core/project/services/board.service';
import { TokenService } from '../../../core/auth/services/token.service';
import { ToastService } from '../../../core/toast/toast.service';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import type { TaskDetail, SubtaskInfo, TaskStatus, TaskType } from '../../../domain/project/models/task.model';
import type { BoardColumnInfo } from '../../../domain/project/models/board-info.model';

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

  readonly statuses: TaskStatus[] = ['NOT_STARTED', 'IN_PROGRESS', 'COMPLETED'];
  readonly types: TaskType[] = ['TASK', 'BUG'];

  async ngOnInit(): Promise<void> {
    await this.loadTask();
  }

  private async loadTask(): Promise<void> {
    this.loading.set(true);
    try {
      const task = await this.taskService.get(this.owner, this.projectCode, this.taskCode);
      this.task.set(task);
      const boards = await this.boardService.getAllBoards(this.owner, this.projectCode);
      this.columns.set(boards.flatMap((b) => b.columns));
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
