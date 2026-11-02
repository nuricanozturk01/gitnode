export type IssueStatus = 'OPEN' | 'CLOSED';

export interface IssueMilestone {
  id: string;
  title: string;
  description: string | null;
  dueDate: string | null;
  status: IssueStatus;
}

export interface IssueLabel {
  id: string;
  name: string;
  color: string;
  description: string | null;
}

export interface IssueAuthor {
  name: string | null;
  email: string;
  username: string;
  avatarUrl: string | null;
}

export interface IssueCommentInfo {
  id: string;
  author: IssueAuthor;
  body: string;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface IssueInfo {
  id: string;
  number: number;
  title: string;
  status: IssueStatus;
  author: IssueAuthor;
  assignee: IssueAuthor | null;
  assignees?: IssueAuthor[];
  labels?: IssueLabel[];
  milestone?: IssueMilestone | null;
  commentCount: number;
  createdAt: string | null;
  updatedAt: string | null;
  closedAt: string | null;
}

export interface IssueDetail {
  id: string;
  number: number;
  title: string;
  description: string | null;
  status: IssueStatus;
  author: IssueAuthor;
  assignee: IssueAuthor | null;
  assignees?: IssueAuthor[];
  labels?: IssueLabel[];
  milestone?: IssueMilestone | null;
  commentCount: number;
  createdAt: string | null;
  updatedAt: string | null;
  closedAt: string | null;
}

export interface IssuePage {
  content: IssueInfo[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface IssueCommentPage {
  content: IssueCommentInfo[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface IssueForm {
  title: string;
  description?: string;
  assigneeId?: string;
}

export interface IssueUpdateForm {
  title?: string;
  description?: string;
  status?: IssueStatus;
  assigneeId?: string;
  assigneeUsername?: string;
  addAssigneeUsername?: string;
  removeAssigneeUsername?: string;
  addLabelId?: string;
  removeLabelId?: string;
  clearAssignee?: boolean;
  milestoneId?: string;
  clearMilestone?: boolean;
}

export interface IssueCommentForm {
  body: string;
}

export interface IssueCommentUpdateForm {
  body: string;
}

export interface IssueLinkedTaskInfo {
  taskCode: string;
  taskTitle: string;
  taskStatus: string;
  projectCode: string;
  projectName: string;
  projectOwner: string;
}
