export interface BoardColumnInfo {
  id: string;
  name: string;
  position: number;
  color: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface BoardInfo {
  id: string;
  name: string;
  position: number;
  columns: BoardColumnInfo[];
  createdAt: string | null;
  updatedAt: string | null;
}

export interface BoardForm {
  name: string;
  position?: number;
}

export interface BoardColumnForm {
  name: string;
  position?: number;
}
