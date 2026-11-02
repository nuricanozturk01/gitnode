export interface PrConflictFile {
  path: string;
  resolvable: boolean;
  reason: string | null;
  content: string | null;
}

export interface PrConflictInfo {
  mergeable: boolean;
  sourceSha: string;
  targetSha: string;
  files: PrConflictFile[];
}

export interface PrConflictResolveForm {
  sourceSha: string;
  targetSha: string;
  files: { path: string; content: string }[];
  message?: string;
}

export interface PrMergeRequirements {
  protectedBranch: boolean;
  requiredApprovals: number;
  currentApprovals: number;
  changesRequestedBy: string[];
  checks: { name: string; state: string }[];
  requireConversationResolution: boolean;
  unresolvedConversations: number;
  satisfied: boolean;
}

export interface PrMetadata {
  labels: { id: string; name: string; color: string; description: string | null }[];
  assignees: { name: string; email: string; username: string; avatarUrl: string }[];
  milestone: { id: string; title: string; status: string } | null;
  canEdit: boolean;
}
