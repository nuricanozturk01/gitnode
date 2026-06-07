ALTER TABLE workflow_run ADD COLUMN IF NOT EXISTS concurrency_group VARCHAR(256);
CREATE INDEX IF NOT EXISTS idx_workflow_run_concurrency ON workflow_run(concurrency_group) WHERE concurrency_group IS NOT NULL;
