ALTER TABLE workflow_job ADD COLUMN IF NOT EXISTS job_key VARCHAR(256);
CREATE INDEX IF NOT EXISTS idx_workflow_job_key ON workflow_job (run_id, job_key);
