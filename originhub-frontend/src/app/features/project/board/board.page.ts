import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideAngularModule } from 'lucide-angular';
import { ProjectService } from '../../../core/project/services/project.service';
import { BoardService } from '../../../core/project/services/board.service';
import { TaskService } from '../../../core/project/services/task.service';
import { TokenService } from '../../../core/auth/services/token.service';
import { ToastService } from '../../../core/toast/toast.service';
import { ConfirmModalService } from '../../../core/confirm-modal/confirm-modal.service';
import type { ProjectInfo } from '../../../domain/project/models/project-info.model';
import type { BoardInfo, BoardColumnInfo } from '../../../domain/project/models/board-info.model';
import type { TaskInfo } from '../../../domain/project/models/task.model';

export interface ColumnColor {
  token: string;
  hex: string;
  label: string;
}

export const COLUMN_COLORS: ColumnColor[] = [
  { token: 'indigo', hex: '#6366f1', label: 'Indigo' },
  { token: 'violet', hex: '#8b5cf6', label: 'Violet' },
  { token: 'pink', hex: '#ec4899', label: 'Pink' },
  { token: 'red', hex: '#ef4444', label: 'Red' },
  { token: 'orange', hex: '#f97316', label: 'Orange' },
  { token: 'amber', hex: '#f59e0b', label: 'Amber' },
  { token: 'green', hex: '#22c55e', label: 'Green' },
  { token: 'teal', hex: '#14b8a6', label: 'Teal' },
  { token: 'sky', hex: '#0ea5e9', label: 'Sky' },
  { token: 'blue', hex: '#3b82f6', label: 'Blue' },
  { token: 'slate', hex: '#64748b', label: 'Slate' },
];

@Component({
  selector: 'app-board',
  standalone: true,
  imports: [RouterLink, LucideAngularModule],
  templateUrl: './board.page.html',
})
export class BoardPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly projectService = inject(ProjectService);
  private readonly boardService = inject(BoardService);
  private readonly taskService = inject(TaskService);
  private readonly tokenService = inject(TokenService);
  private readonly toastService = inject(ToastService);
  private readonly confirmModal = inject(ConfirmModalService);

  readonly project = signal<ProjectInfo | null>(null);
  readonly boards = signal<BoardInfo[]>([]);
  readonly tasks = signal<TaskInfo[]>([]);
  readonly loading = signal(true);
  readonly selectedBoardId = signal<string | null>(null);

  readonly showCreateBoardModal = signal(false);
  readonly showAddColumnModal = signal(false);
  readonly showCreateTaskModal = signal(false);
  readonly creatingBoard = signal(false);
  readonly creatingColumn = signal(false);
  readonly creatingTask = signal(false);

  readonly newBoardName = signal('');
  readonly newColumnName = signal('');
  readonly newTaskTitle = signal('');
  readonly newTaskType = signal<'TASK' | 'BUG'>('TASK');
  readonly newTaskColumnId = signal('');

  // color picker
  readonly colorPickerColumnId = signal<string | null>(null);
  readonly columnColors = COLUMN_COLORS;

  // task drag state
  private draggedTaskId: string | null = null;
  private draggedFromColumnId: string | null = null;
  readonly dropIndicatorColumnId = signal<string | null>(null);
  readonly dropIndicatorIndex = signal<number>(-1);
  readonly dragOverColumnId = signal<string | null>(null);

  // column drag state
  readonly draggedColumnId = signal<string | null>(null);
  readonly dragOverColumnHeaderId = signal<string | null>(null);

  get owner(): string {
    return this.tokenService.getUsername() ?? '';
  }
  get projectCode(): string {
    return this.route.snapshot.paramMap.get('projectCode') ?? '';
  }

  readonly selectedBoard = computed(() => {
    const id = this.selectedBoardId();
    return this.boards().find((b) => b.id === id) ?? this.boards()[0] ?? null;
  });

  tasksByColumn(columnId: string): TaskInfo[] {
    return this.tasks()
      .filter((t) => t.boardColumnId === columnId)
      .sort((a, b) => a.position - b.position);
  }

  columnColor(col: BoardColumnInfo): string {
    return COLUMN_COLORS.find((c) => c.token === col.color)?.hex ?? '';
  }

  statusDotClass(status: string): string {
    switch (status) {
      case 'COMPLETED':
        return 'bg-success';
      case 'IN_PROGRESS':
        return 'bg-warning';
      default:
        return 'bg-base-content/30';
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

  async ngOnInit(): Promise<void> {
    await this.loadAll();
  }

  private async loadAll(): Promise<void> {
    this.loading.set(true);
    try {
      const [project, boards, tasks] = await Promise.all([
        this.projectService.get(this.owner, this.projectCode),
        this.boardService.getAllBoards(this.owner, this.projectCode),
        this.taskService.getAll(this.owner, this.projectCode),
      ]);
      this.project.set(project);
      this.boards.set(boards);
      this.tasks.set(tasks);
      if (boards.length > 0) this.selectedBoardId.set(boards[0].id);
    } catch {
      this.toastService.error('Failed to load board');
    } finally {
      this.loading.set(false);
    }
  }

  selectBoard(boardId: string): void {
    this.selectedBoardId.set(boardId);
  }

  async createBoard(): Promise<void> {
    const name = this.newBoardName().trim();
    if (!name) return;
    this.creatingBoard.set(true);
    try {
      const board = await this.boardService.createBoard(this.owner, this.projectCode, { name, position: 0 });
      this.boards.update((list) => [...list, board]);
      this.selectedBoardId.set(board.id);
      this.showCreateBoardModal.set(false);
      this.newBoardName.set('');
    } catch {
      this.toastService.error('Failed to create board');
    } finally {
      this.creatingBoard.set(false);
    }
  }

  async addColumn(): Promise<void> {
    const board = this.selectedBoard();
    if (!board) return;
    const name = this.newColumnName().trim();
    if (!name) return;
    this.creatingColumn.set(true);
    try {
      const position = board.columns.length;
      const col = await this.boardService.createColumn(this.owner, this.projectCode, board.id, { name, position });
      this.boards.update((list) => list.map((b) => (b.id === board.id ? { ...b, columns: [...b.columns, col] } : b)));
      this.showAddColumnModal.set(false);
      this.newColumnName.set('');
    } catch {
      this.toastService.error('Failed to add column');
    } finally {
      this.creatingColumn.set(false);
    }
  }

  openCreateTask(columnId: string): void {
    this.newTaskColumnId.set(columnId);
    this.newTaskTitle.set('');
    this.newTaskType.set('TASK');
    this.showCreateTaskModal.set(true);
  }

  async createTask(): Promise<void> {
    const title = this.newTaskTitle().trim();
    const columnId = this.newTaskColumnId();
    if (!title || !columnId) return;
    this.creatingTask.set(true);
    try {
      const detail = await this.taskService.create(this.owner, this.projectCode, {
        title,
        boardColumnId: columnId,
        type: this.newTaskType(),
        position: 0,
      });
      const taskInfo: TaskInfo = {
        id: detail.id,
        code: detail.code,
        title: detail.title,
        status: detail.status,
        type: detail.type,
        position: detail.position,
        boardColumnId: detail.boardColumnId,
        assignee: detail.assignee,
        branchName: detail.branchName,
        hasLinkedPr: !!detail.linkedPr,
        subtaskCount: detail.subtasks.length,
        completedSubtaskCount: detail.subtasks.filter((s) => s.status === 'COMPLETED').length,
        createdAt: detail.createdAt,
        updatedAt: detail.updatedAt,
      };
      this.tasks.update((list) => [...list, taskInfo]);
      this.showCreateTaskModal.set(false);
    } catch {
      this.toastService.error('Failed to create task');
    } finally {
      this.creatingTask.set(false);
    }
  }

  async setColumnColor(column: BoardColumnInfo, colorToken: string | null): Promise<void> {
    const board = this.selectedBoard();
    if (!board) return;
    this.colorPickerColumnId.set(null);

    // optimistic
    this.boards.update((list) =>
      list.map((b) =>
        b.id === board.id
          ? { ...b, columns: b.columns.map((c) => (c.id === column.id ? { ...c, color: colorToken } : c)) }
          : b,
      ),
    );

    try {
      await this.boardService.updateColumn(this.owner, this.projectCode, board.id, column.id, {
        color: colorToken ?? '',
      });
    } catch {
      this.toastService.error('Failed to update column color');
      await this.loadAll();
    }
  }

  async deleteBoard(board: BoardInfo): Promise<void> {
    const ok = await this.confirmModal.confirm(
      `Delete board "${board.name}"?`,
      'All columns and tasks will be permanently removed.',
      { confirmLabel: 'Delete', variant: 'danger' },
    );
    if (!ok) return;
    try {
      await this.boardService.deleteBoard(this.owner, this.projectCode, board.id);
      this.boards.update((list) => list.filter((b) => b.id !== board.id));
      if (this.selectedBoardId() === board.id) {
        this.selectedBoardId.set(this.boards()[0]?.id ?? null);
      }
      this.toastService.success('Board deleted');
    } catch {
      this.toastService.error('Failed to delete board');
    }
  }

  async deleteColumn(board: BoardInfo, column: BoardColumnInfo): Promise<void> {
    const ok = await this.confirmModal.confirm(
      `Delete column "${column.name}"?`,
      'All tasks in this column will be permanently removed.',
      { confirmLabel: 'Delete', variant: 'danger' },
    );
    if (!ok) return;
    try {
      await this.boardService.deleteColumn(this.owner, this.projectCode, board.id, column.id);
      this.boards.update((list) =>
        list.map((b) => (b.id === board.id ? { ...b, columns: b.columns.filter((c) => c.id !== column.id) } : b)),
      );
      this.tasks.update((list) => list.filter((t) => t.boardColumnId !== column.id));
      this.toastService.success('Column deleted');
    } catch {
      this.toastService.error('Failed to delete column');
    }
  }

  // ── Task Drag & Drop ─────────────────────────────────────────────────────────

  onTaskDragStart(event: DragEvent, task: TaskInfo): void {
    event.stopPropagation();
    this.draggedTaskId = task.id;
    this.draggedFromColumnId = task.boardColumnId;
    this.draggedColumnId.set(null);
    event.dataTransfer!.effectAllowed = 'move';
    event.dataTransfer!.setData('type', 'task');
    event.dataTransfer!.setData('id', task.id);
    (event.currentTarget as HTMLElement).classList.add('opacity-40', 'scale-95');
  }

  onTaskDragEnd(event: DragEvent): void {
    (event.currentTarget as HTMLElement).classList.remove('opacity-40', 'scale-95');
    this.draggedTaskId = null;
    this.draggedFromColumnId = null;
    this.dropIndicatorColumnId.set(null);
    this.dropIndicatorIndex.set(-1);
    this.dragOverColumnId.set(null);
  }

  onColumnDragOver(event: DragEvent, columnId: string): void {
    if (this.draggedColumnId()) return;
    event.preventDefault();
    event.dataTransfer!.dropEffect = 'move';
    this.dragOverColumnId.set(columnId);
    // If drag is over empty column area (not over a task card), set indicator at end
    const tasks = this.tasksByColumn(columnId);
    if (this.dropIndicatorColumnId() !== columnId) {
      this.dropIndicatorColumnId.set(columnId);
      this.dropIndicatorIndex.set(tasks.length);
    }
  }

  onColumnDragLeave(event: DragEvent, columnId: string): void {
    if (this.draggedColumnId()) return;
    const related = event.relatedTarget as HTMLElement | null;
    const container = event.currentTarget as HTMLElement;
    if (!related || !container.contains(related)) {
      if (this.dragOverColumnId() === columnId) {
        this.dragOverColumnId.set(null);
      }
    }
  }

  onTaskDragOver(event: DragEvent, columnId: string, taskIndex: number): void {
    if (this.draggedColumnId()) return;
    event.preventDefault();
    event.stopPropagation();
    event.dataTransfer!.dropEffect = 'move';
    this.dragOverColumnId.set(columnId);

    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const insertIndex = event.clientY < rect.top + rect.height / 2 ? taskIndex : taskIndex + 1;
    this.dropIndicatorColumnId.set(columnId);
    this.dropIndicatorIndex.set(insertIndex);
  }

  async onDrop(event: DragEvent, targetColumnId: string): Promise<void> {
    event.preventDefault();
    this.dragOverColumnId.set(null);

    const taskId = this.draggedTaskId;
    const fromColumnId = this.draggedFromColumnId;
    if (!taskId || !fromColumnId) return;

    const task = this.tasks().find((t) => t.id === taskId);
    if (!task) return;

    const targetTasks = this.tasksByColumn(targetColumnId);
    let insertIndex = this.dropIndicatorIndex() >= 0 ? this.dropIndicatorIndex() : targetTasks.length;

    // same column: adjust for removed item
    if (fromColumnId === targetColumnId) {
      const currentIndex = targetTasks.findIndex((t) => t.id === taskId);
      if (insertIndex > currentIndex) insertIndex = insertIndex - 1;
      if (insertIndex === currentIndex) {
        this.dropIndicatorColumnId.set(null);
        this.dropIndicatorIndex.set(-1);
        return;
      }
    }

    this.dropIndicatorColumnId.set(null);
    this.dropIndicatorIndex.set(-1);

    // optimistic update
    this.tasks.update((list) => {
      const others = list.filter((t) => t.id !== taskId);
      const colTasks = others.filter((t) => t.boardColumnId === targetColumnId).sort((a, b) => a.position - b.position);

      colTasks.splice(insertIndex, 0, { ...task, boardColumnId: targetColumnId });
      const reindexed = colTasks.map((t, i) => ({ ...t, position: i }));

      return [...others.filter((t) => t.boardColumnId !== targetColumnId), ...reindexed];
    });

    try {
      await this.taskService.update(this.owner, this.projectCode, task.code, {
        boardColumnId: targetColumnId,
        position: insertIndex,
      });
    } catch {
      this.toastService.error('Failed to move task');
      await this.loadAll();
    }
  }

  // ── Column Drag & Drop ───────────────────────────────────────────────────────

  onColumnDragStart(event: DragEvent, column: BoardColumnInfo): void {
    this.draggedColumnId.set(column.id);
    this.draggedTaskId = null;
    event.dataTransfer!.effectAllowed = 'move';
    event.dataTransfer!.setData('type', 'column');
    event.dataTransfer!.setData('id', column.id);
  }

  onColumnDragEnd(): void {
    this.draggedColumnId.set(null);
    this.dragOverColumnHeaderId.set(null);
  }

  onColumnHeaderDragOver(event: DragEvent, columnId: string): void {
    if (!this.draggedColumnId() || this.draggedColumnId() === columnId) return;
    event.preventDefault();
    event.stopPropagation();
    event.dataTransfer!.dropEffect = 'move';
    this.dragOverColumnHeaderId.set(columnId);
  }

  onColumnHeaderDragLeave(event: DragEvent, columnId: string): void {
    if (this.dragOverColumnHeaderId() === columnId) {
      this.dragOverColumnHeaderId.set(null);
    }
  }

  async onColumnDrop(event: DragEvent, targetColumnId: string): Promise<void> {
    event.preventDefault();
    event.stopPropagation();
    const sourceId = this.draggedColumnId();
    this.dragOverColumnHeaderId.set(null);
    if (!sourceId || sourceId === targetColumnId) return;

    const board = this.selectedBoard();
    if (!board) return;

    const cols = [...board.columns].sort((a, b) => a.position - b.position);
    const fromIdx = cols.findIndex((c) => c.id === sourceId);
    const toIdx = cols.findIndex((c) => c.id === targetColumnId);
    if (fromIdx < 0 || toIdx < 0) return;

    const reordered = [...cols];
    const [moved] = reordered.splice(fromIdx, 1);
    reordered.splice(toIdx, 0, moved);
    const withPositions = reordered.map((c, i) => ({ ...c, position: i }));

    // optimistic
    this.boards.update((list) => list.map((b) => (b.id === board.id ? { ...b, columns: withPositions } : b)));

    try {
      // persist the moved column's new position (server can derive others)
      await this.boardService.updateColumn(this.owner, this.projectCode, board.id, sourceId, {
        position: toIdx,
      });
    } catch {
      this.toastService.error('Failed to reorder columns');
      await this.loadAll();
    }
  }

  showIndicator(columnId: string, index: number): boolean {
    return this.dropIndicatorColumnId() === columnId && this.dropIndicatorIndex() === index && !!this.draggedTaskId;
  }
}
