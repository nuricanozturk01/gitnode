export interface WorkflowSummary {
  id: string | null;
  name: string;
  filePath: string;
  enabled: boolean;
  dispatchable: boolean;
  lastRunStatus: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}
