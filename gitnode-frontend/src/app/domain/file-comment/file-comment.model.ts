export interface FileCommentAuthor {
  name: string;
  email: string;
  username: string;
  avatarUrl: string;
}

export interface FileComment {
  id: string;
  path: string;
  line: number | null;
  parentId: string | null;
  body: string;
  commitSha: string;
  outdated: boolean;
  author: FileCommentAuthor;
  canEdit: boolean;
  canDelete: boolean;
  createdAt: string;
  updatedAt: string | null;
}

export interface FileCommentPage {
  headSha: string;
  comments: FileComment[];
}
