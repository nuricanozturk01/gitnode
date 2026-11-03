export interface WikiPageSummary {
  slug: string;
  title: string;
}

export interface WikiPage {
  slug: string;
  title: string;
  content: string;
  blobSha: string;
  commitSha: string;
  lastEditor: string;
  updatedAt: string;
}

export interface WikiRevision {
  sha: string;
  message: string;
  author: string;
  date: string;
}

export interface WikiPageSlice<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
