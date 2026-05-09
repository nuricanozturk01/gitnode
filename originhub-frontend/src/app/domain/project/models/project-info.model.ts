export interface ProjectInfo {
  id: string;
  name: string;
  description: string | null;
  codePrefix: string;
  taskSeq: number;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface ProjectForm {
  name: string;
  description?: string;
  codePrefix: string;
}

export interface ProjectUpdateForm {
  name?: string;
  description?: string;
}
