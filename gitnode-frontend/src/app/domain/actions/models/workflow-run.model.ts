import type { WorkflowJob } from './workflow-job.model';

export interface WorkflowRun {
  id: string;
  runNumber: number;
  workflowName: string;
  status: string;
  conclusion: string | null;
  triggerEvent: string;
  triggerRef: string | null;
  triggerSha: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  jobs: WorkflowJob[];
}

export interface WorkflowRunPage {
  content: WorkflowRun[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
