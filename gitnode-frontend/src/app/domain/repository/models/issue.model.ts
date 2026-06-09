export type IssueStatus = 'OPEN' | 'CLOSED';

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
}
