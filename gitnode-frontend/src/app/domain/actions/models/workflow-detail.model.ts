export interface DispatchInput {
  name: string;
  description: string | null;
  type: string | null;
  defaultValue: string | null;
  options: string[] | null;
  required: boolean;
}

export interface WorkflowDetail {
  id: string | null;
  name: string;
  filePath: string;
  content: string;
  defaultBranch: string;
  enabled: boolean;
  dispatchable: boolean;
  lastRunStatus: string | null;
  dispatchInputs?: DispatchInput[];
}
