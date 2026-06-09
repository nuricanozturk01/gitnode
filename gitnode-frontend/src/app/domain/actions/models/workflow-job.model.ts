import type { WorkflowStep } from './workflow-step.model';

export interface WorkflowJob {
  id: string;
  name: string;
  status: string;
  conclusion: string | null;
  runnerLabels: string[];
  needs: string[] | null;
  matrixValues: Record<string, string> | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  steps: WorkflowStep[];
}
