/** A ::error, ::warning or ::notice a step wrote into its log. */
export interface RunAnnotation {
  level: 'error' | 'warning' | 'notice';
  message: string;
  jobId: string;
  jobName: string;
  stepId: string;
  stepNumber: number;
  lineNumber: number;
}

/** The markdown a job wrote to $GITHUB_STEP_SUMMARY. */
export interface JobSummary {
  jobId: string;
  jobName: string;
  markdown: string;
}
