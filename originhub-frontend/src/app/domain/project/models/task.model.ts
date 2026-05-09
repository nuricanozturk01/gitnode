export type TaskStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'NONE';
export type TaskType = 'TASK' | 'BUG';

export interface TaskAssignee {
  name: string;
  email: string;
  avatarUrl?: string | null;
}

export interface LinkedPrInfo {
  id: string;
  number: number;
  title: string;
  status: string;
  sourceBranch: string;
  targetBranch: string;
}

export interface SubtaskInfo {
  id: string;
  title: string;
  description: string | null;
  status: TaskStatus;
  position: number;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface TaskInfo {
  id: string;
  code: string;
  title: string;
  status: TaskStatus;
  type: TaskType;
  position: number;
  boardColumnId: string;
  assignee: TaskAssignee | null;
  branchName: string | null;
  hasLinkedPr: boolean;
  subtaskCount: number;
  completedSubtaskCount: number;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface TaskDetail {
  id: string;
  code: string;
  title: string;
  description: string | null;
  status: TaskStatus;
  type: TaskType;
  position: number;
  boardColumnId: string;
  assignee: TaskAssignee | null;
  branchName: string | null;
  branchRepoId: string | null;
  linkedPr: LinkedPrInfo | null;
  subtasks: SubtaskInfo[];
  createdAt: string | null;
  updatedAt: string | null;
}

export interface TaskForm {
  title: string;
  description?: string;
  boardColumnId: string;
  type?: TaskType;
  assigneeId?: string;
  position?: number;
}

export interface TaskUpdateForm {
  title?: string;
  description?: string;
  boardColumnId?: string;
  status?: TaskStatus;
  type?: TaskType;
  assigneeId?: string;
  position?: number;
}

export interface SubtaskForm {
  title: string;
  description?: string;
  position?: number;
}

export interface SubtaskUpdateForm {
  title?: string;
  description?: string;
  status?: TaskStatus;
  position?: number;
}

export interface CreateBranchFromTaskForm {
  repoOwner: string;
  repoName: string;
  sourceBranch: string;
  branchName?: string;
}
