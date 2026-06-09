export interface ProjectInfo {
  id: string;
  name: string;
  description: string | null;
  codePrefix: string;
  taskCount: number;
  syncTaskStatusOnPrMerge: boolean;
  isPublic: boolean;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface ProjectForm {
  name: string;
  description?: string;
  codePrefix: string;
  isPublic: boolean;
}

export interface ProjectUpdateForm {
  name?: string;
  description?: string;
  syncTaskStatusOnPrMerge?: boolean;
  isPublic?: boolean;
}

export interface ProjectPage {
  content: ProjectInfo[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
