export interface WorkflowStep {
  id: string;
  stepNumber: number;
  name: string | null;
  uses: string | null;
  status: string;
  conclusion: string | null;
  startedAt: string | null;
  completedAt: string | null;
}

export interface WorkflowLogLine {
  lineNumber: number;
  content: string;
  level: string;
  /** When the server stored the line (ISO instant); absent on lines from before it was recorded. */
  timestamp?: string | null;
}
