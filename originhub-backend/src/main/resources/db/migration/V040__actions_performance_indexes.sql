-- Partial index for expired artifact cleanup scan (nightly scheduler)
CREATE INDEX idx_workflow_artifact_expires
    ON workflow_artifact (expires_at)
    WHERE expires_at IS NOT NULL;

-- Composite index for latest-run-per-workflow-def query (resolves N+1 in workflow list)
CREATE INDEX idx_workflow_run_def_created
    ON workflow_run (repo_id, workflow_def_id, created_at DESC)
    WHERE workflow_def_id IS NOT NULL;

-- Composite index for per-runner in-progress count (markRunnerOnlineIfIdle)
CREATE INDEX idx_workflow_job_runner_status
    ON workflow_job (runner_id, status)
    WHERE runner_id IS NOT NULL;
