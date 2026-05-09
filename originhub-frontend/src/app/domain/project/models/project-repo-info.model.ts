export interface OpenPrInfo {
  id: string;
  number: number;
  title: string;
  sourceBranch: string;
  targetBranch: string;
}

export interface ProjectRepoInfo {
  id: string;
  name: string;
  ownerUsername: string;
  description: string | null;
  defaultBranch: string;
  openPullRequests: OpenPrInfo[];
}
